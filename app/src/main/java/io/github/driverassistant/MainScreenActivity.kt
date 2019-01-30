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
import android.util.Log
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
    private val recognizers: List<Recognizer> = listOf(RandomRecognizer())  // TODO: Add normal recognizers here

    private var recognizerThread = HandlerThread("Driver Assistant Recognizer Thread").apply {
        isDaemon = true
        start()
    }

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

    private var previousImageShotTime = System.currentTimeMillis()

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { imageReader ->
        recognizerThread.handler.post {
            val startTime = System.currentTimeMillis()
            val delta = startTime - previousImageShotTime
            val actualFps = 1000.0 / delta.toDouble()

            Log.d(TAG, "Time after previous image: $delta ms, actual fps: $actualFps")

            imageReader.acquireNextImage()?.apply {
                val buffer = planes[0].buffer

                val latestImageBytes = ByteArray(buffer.remaining())
                buffer.get(latestImageBytes)

                Log.d(TAG, "Image copied after ${System.currentTimeMillis() - startTime} ms")

                val latestImage = LatestImage(latestImageBytes, width, height)

                stateMachine.make(ImageShotAction(latestImage))

                close()
            }

            Log.d(TAG, "Image closed after ${System.currentTimeMillis() - startTime} ms")

            previousImageShotTime = startTime
        }
    }

    private val recognizerImageButtonListener = OnClickListener {
        stateMachine.make(
            RecognizerImageButtonClickedAction(
                fps = FPS,
                recognizedObjectsView = recognizedObjectsView,
                statsTextView = statsTextView,
                recognizers = recognizers
            )
        )
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
            window.decorView.hideStatusAndNavigationBars()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (RequestCode.byId(requestCode)) {
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

    val template = TEMPLATE_VIDEO_SNAPSHOT

    companion object {
        private const val TAG = "MainScreenActivity"

        private const val FPS = 5.0

        private const val FOLDER_NAME = "driver-assistant/"
    }
}
