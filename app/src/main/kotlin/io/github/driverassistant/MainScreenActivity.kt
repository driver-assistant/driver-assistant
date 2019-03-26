package io.github.driverassistant

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.media.ImageReader
import android.os.Bundle
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.TextureView.SurfaceTextureListener
import android.view.View.OnClickListener
import io.github.driverassistant.recognizer.ImageData
import io.github.driverassistant.recognizer.LinesRecognizer
import io.github.driverassistant.recognizer.RandomRecognizer
import io.github.driverassistant.recognizer.Recognizer
import io.github.driverassistant.state.*
import io.github.driverassistant.util.*
import kotlinx.android.synthetic.main.activity_main_screen.*

class MainScreenActivity : AppCompatActivity() {
    private val recognizers: List<Recognizer> = listOf(RandomRecognizer(), LinesRecognizer())

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

                val latestImage = ImageData(latestImageBytes, width, height)

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
                recognizersRunnerListener = recognizersRunnerListener,
                recognizers = recognizers
            )
        )
    }

    private val previewCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            stateMachine.make(PreviewCaptureSessionConfiguredAction(session))
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            shortToast(R.string.unable_to_start_preview)  // TODO: stateMachine.make action
        }
    }

    private val recordingCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            stateMachine.make(RecordingCaptureSessionConfiguredAction(session))
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            shortToast(R.string.unable_to_start_recording)  // TODO: stateMachine.make action
        }
    }

    private val videoImageButtonListener = OnClickListener {
        stateMachine.make(
            RecordSwitchAction(
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

    private val recognizersRunnerListener = object : RecognizersRunner.Companion.RecognizersRunnerListener {
        override fun onResult(imageData: ImageData, objects: Iterable<PaintableOnCanvas>) {
            val statsText = with(imageData) { "${bytes.size} bytes, $width x $height" }
            statsTextView.postApply { text = statsText }

            recognizedObjectsView.postApply {
                paintables = objects
                invalidate()
            }
        }

        override fun onEnd() {
            statsTextView.postApply { text = "" }

            recognizedObjectsView.postApply {
                paintables = emptyList()
                invalidate()
            }
        }
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
                        RecordSwitchAction(
                            videoFolderName = FOLDER_NAME,
                            recordingCaptureSessionStateCallback = recordingCaptureSessionStateCallback,
                            previewCaptureSessionStateCallback = previewCaptureSessionStateCallback,
                            videoImageButton = videoImageButton,
                            cameraTextureView = cameraTextureView,
                            chronometer = chronometer,
                            activity = this  // TODO: is there a method to check permissions without passing the activity?
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainScreenActivity"

        private const val FPS = 5.0

        private const val FOLDER_NAME = "driver-assistant/"
    }
}
