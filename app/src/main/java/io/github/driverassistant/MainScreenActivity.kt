package io.github.driverassistant

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.CameraDevice.*
import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER
import android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION
import android.hardware.camera2.CaptureResult.CONTROL_AF_STATE
import android.media.ImageReader
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.View.INVISIBLE
import io.github.driverassistant.recognizer.LatestImage
import io.github.driverassistant.recognizer.RandomRecognizer
import io.github.driverassistant.recognizer.Recognizer
import android.view.View.OnClickListener
import io.github.driverassistant.state.*
import io.github.driverassistant.util.*
import kotlinx.android.synthetic.main.activity_main_screen.*
import java.io.File
import kotlin.math.roundToLong

class MainScreenActivity : AppCompatActivity() {

    var latestImage: LatestImage? = null

    private lateinit var recognizersRunner: LoopWithDelay

    private val recognizers: List<Recognizer> = listOf(RandomRecognizer())  // TODO: Add normal recognizers here

    private var recognizerThread: HandlerThread? = null

    private var captureState = State.PREVIEW

    private val surfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            stateMachine.make(
                SurfaceTextureAvailableAction(
                    width = width,
                    height = height,
                    onImageAvailableListener = onImageAvailableListener,
                    activity = this@MainScreenActivity,  // TODO: is there a method to check permissions without passing the activity?
                    cameraDeviceStateCallback = cameraDeviceStateCallback
                )
            )
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) = Unit

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = true
    }

    private var cameraDevice: CameraDevice? = null

    private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            stateMachine.make(CameraOpenedAction(camera, cameraTextureView, previewCaptureSessionStateCallback))
        }

        override fun onDisconnected(camera: CameraDevice) {
            stateMachine.make(CameraClosedAction(camera))
        }

        override fun onError(camera: CameraDevice, error: Int) {
            stateMachine.make(CameraClosedAction(camera, error))
        }
    }

    private lateinit var cameraId: String

    private lateinit var previewSize: Size

    private lateinit var videoSize: Size

    private lateinit var imageSize: Size

    private lateinit var imageReader: ImageReader

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { imageReader ->
        imageReader.acquireLatestImage().apply {
            val buffer = planes[0].buffer

            val latestImageBytes = ByteArray(buffer.remaining())
            buffer.get(latestImageBytes)

            latestImage = LatestImage(latestImageBytes, width, height)

            println("Image is shoot: $width x $height")

            close()
        }
    }

    private var totalRotation = 0

    private var captureThread: HandlerThread? = null

    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    private var isRecording = false

    private lateinit var videoFolder: File

    private lateinit var previewCaptureSession: CameraCaptureSession

    private val recognizerImageButtonListener = OnClickListener {
        stateMachine.make(RecognizerImageButtonClickedAction(FPS))

//        val recognizer = recognizerThread
//
//        if (recognizer == null) {
//            startRecognizerThread(FPS)
//        } else {
//            stopRecognizerThread()
//        }
    }

    private val previewCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            stateMachine.make(PreviewCaptureSessionConfiguredAction(session))
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            shortToast(R.string.unable_to_start_preview)
        }
    }

    private val recordingCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            stateMachine.make(RecordingCaptureSessionConfiguredAction(session))
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            shortToast(R.string.unable_to_start_recording)
        }
    }

    private val videoImageButtonListener = OnClickListener {
        stateMachine.make(
            VideoImageButtonClickedAction(
                videoFolderName = FOLDER_NAME,
                recordingCaptureSessionStateCallback = recordingCaptureSessionStateCallback,
                previewCaptureSessionStateCallback = previewCaptureSessionStateCallback,
                videoImageButton = videoImageButton,
                cameraTextureView = cameraTextureView,
                chronometer = chronometer,
                activity = this
            )
        )
    }

//    private val captureCallback = object : CaptureCallback() {
//        override fun onCaptureCompleted(
//            session: CameraCaptureSession,
//            request: CaptureRequest,
//            result: TotalCaptureResult
//        ) {
//            super.onCaptureCompleted(session, request, result)
//
//            if (captureState == State.WAIT_LOCK) {
//                captureState = State.PREVIEW
//
//                val afState = result[CONTROL_AF_STATE]
//                if (afState == CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
//                    requestToStartStillCapture()
//                }
//            }
//        }
//    }

    private lateinit var recordCaptureSession: CameraCaptureSession

    private lateinit var stateMachine: MainScreenActivityStateMachine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_screen)

        stateMachine = MainScreenActivityStateMachine()

        recognizerImageButton.setOnClickListener(recognizerImageButtonListener)
        videoImageButton.setOnClickListener(videoImageButtonListener)
    }

    override fun onResume() {
        super.onResume()

        stateMachine.make(ActivityResumedAction(cameraTextureView, surfaceTextureListener))
    }

    override fun onPause() {
        stateMachine.make(ActivityPausedAction)

        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            window.decorView.hideStatusAndNavigationBars(hasFocus)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (RequestCode.values()[requestCode]) {
            RequestCode.CAMERA -> {
                if (grantResults[0] != PERMISSION_GRANTED) {
                    shortToast(R.string.no_camera_permission)
                }
            }

            RequestCode.WRITE_EXTERNAL_STORAGE -> {
                if (grantResults[0] != PERMISSION_GRANTED) {
                    shortToast(R.string.no_write_external_storage_permission)
                } else {
                    stateMachine.make(
                        VideoImageButtonClickedAction(
                            videoFolderName = FOLDER_NAME,
                            recordingCaptureSessionStateCallback = recordingCaptureSessionStateCallback,
                            previewCaptureSessionStateCallback = previewCaptureSessionStateCallback,
                            videoImageButton = videoImageButton,
                            cameraTextureView = cameraTextureView,
                            chronometer = chronometer,
                            activity = this
                        )
                    )
                }
            }
        }
    }

//    private fun requestToStartStillCapture() {
//        captureRequestBuilder = if (isRecording) {
//            cameraDevice!!.createCaptureRequest(TEMPLATE_VIDEO_SNAPSHOT)
//        } else {
//            cameraDevice!!.createCaptureRequest(TEMPLATE_STILL_CAPTURE)
//        }
//
//        captureRequestBuilder.apply {
//            addTarget(imageReader.surface)
//            this[JPEG_ORIENTATION] = totalRotation
//        }
//
//        if (isRecording) {
//            recordCaptureSession.capture(captureRequestBuilder.build(), null, null)
//        } else {
//            previewCaptureSession.capture(captureRequestBuilder.build(), null, null)
//        }
//    }
//
//    private fun startRecognizerThread(fps: Double) {
//        val thread = HandlerThread("Driver Assistant Recognizer Thread")
//        thread.start()
//        val handler = thread.handler
//
//        recognizersRunner = object : LoopWithDelay(handler, (1000 / fps).roundToLong()) {
//            override fun iterate() {
//                lockFocusToTakeShot()
//                val latestImage = waitForImage()
//
//                val statsText = with(latestImage) { "${bytes.size} bytes, $width x $height" }
//                statsTextView.post { statsTextView.text = statsText }
//
//                val paintables = recognizers
//                    .flatMap { recognizer -> recognizer.recognize(latestImage) }
//                    .flatMap { recognizedObject -> recognizedObject.elements }
//                    .map { recognizedObjectElement -> recognizedObjectElement.toPaintableOnCanvas() }
//
//                recognizedObjectsView.paintables = paintables
//                recognizedObjectsView.invalidate()
//            }
//
//            private fun waitForImage(): LatestImage {
//                val activity = this@MainScreenActivity
//
//                while (true) {
//                    val latestImage = activity.latestImage
//
//                    if (latestImage != null) {
//                        activity.latestImage = null
//
//                        return latestImage
//                    }
//                }
//            }
//        }
//
//        handler.post(recognizersRunner)
//
//        recognizerThread = thread
//    }
//
//    private fun stopRecognizerThread() {
//        recognizerThread!!.apply {
//            handler.removeCallbacks(recognizersRunner)
//
//            quitSafely()
//            join()
//        }
//
//        recognizedObjectsView.paintables = emptyList()
//        recognizedObjectsView.invalidate()
//        statsTextView.text = ""
//
//        recognizerThread = null
//    }
//
//    fun lockFocusToTakeShot() {
//        captureState = State.WAIT_LOCK
//        captureRequestBuilder[CONTROL_AF_TRIGGER] = CONTROL_AF_TRIGGER_START
//
//        if (isRecording) {
//            recordCaptureSession.capture(
//                captureRequestBuilder.build(),
//                captureCallback,
//                captureThread!!.handler
//            )
//        } else {
//            previewCaptureSession.capture(
//                captureRequestBuilder.build(),
//                captureCallback,
//                captureThread!!.handler
//            )
//        }
//    }

    companion object {
        private const val FPS = 5.0

        private const val FOLDER_NAME = "driver-assistant/"
    }
}
