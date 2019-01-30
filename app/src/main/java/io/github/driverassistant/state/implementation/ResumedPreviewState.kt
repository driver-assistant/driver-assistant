package io.github.driverassistant.state.implementation

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE
import android.media.MediaRecorder
import android.media.MediaRecorder.OutputFormat.MPEG_4
import android.media.MediaRecorder.VideoEncoder.H264
import android.os.HandlerThread
import android.os.SystemClock
import android.support.v4.app.ActivityCompat.requestPermissions
import android.support.v4.app.ActivityCompat.shouldShowRequestPermissionRationale
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Chronometer
import android.widget.ImageButton
import io.github.driverassistant.R
import io.github.driverassistant.RecognizersRunner
import io.github.driverassistant.state.*
import io.github.driverassistant.util.*
import io.github.driverassistant.util.camera.PreviewingCamera
import io.github.driverassistant.util.camera.SetUpCamera
import io.github.driverassistant.util.camera.ShootingCamera

class ResumedPreviewState(
    private val setUpCamera: SetUpCamera,
    private val previewingCamera: PreviewingCamera,
    private val captureThread: HandlerThread,
    private val previewCaptureSession: CameraCaptureSession
) : MainScreenActivityState() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is CameraClosedAction -> {
            action.cameraDevice.close()

            ResumedCameraState(captureThread, setUpCamera)
        }

        is RecordSwitchAction -> {
            if (permissionsAvailableOnThisAndroidVersion &&
                checkSelfPermission(action.activity, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(action.activity, WRITE_EXTERNAL_STORAGE)) {
                    action.activity.shortToast(R.string.no_write_external_storage_permission)
                }

                requestPermissions(
                    action.activity,
                    arrayOf(WRITE_EXTERNAL_STORAGE),
                    RequestCode.WRITE_EXTERNAL_STORAGE.id
                )

                this
            } else {
                val videoFolder = ensureVideoFolder(action.videoFolderName)
                val videoFilePath = createVideoFile(videoFolder).absolutePath

                val mediaRecorder = startRecording(
                    videoFilePath = videoFilePath,
                    setUpCamera = setUpCamera,
                    cameraDevice = previewingCamera.cameraDevice,
                    recordingCaptureSessionStateCallback = action.recordingCaptureSessionStateCallback,
                    videoImageButton = action.videoImageButton,
                    chronometer = action.chronometer,
                    cameraTextureView = action.cameraTextureView
                )

                WaitingForRecordingSessionState(
                    setUpCamera = setUpCamera,
                    previewingCamera = previewingCamera,
                    mediaRecorder = mediaRecorder,
                    captureThread = captureThread
                )
            }
        }

        is RecognizerImageButtonClickedAction -> {
            val shootingCamera = ShootingCamera(
                captureSession = previewCaptureSession,
                requestTemplateType = TEMPLATE_STILL_CAPTURE,
                setUpCamera = setUpCamera,
                captureThread = captureThread,
                previewingCamera = previewingCamera
            )

            val recognizersRunner = RecognizersRunner(
                fps = action.fps,
                statsTextView = action.statsTextView,
                recognizedObjectsView = action.recognizedObjectsView,
                recognizers = action.recognizers,
                shootingCamera = shootingCamera,
                captureThread = captureThread
            )

            shootingCamera.submitRequestForNextShot()

            ResumedRecognizerPreviewState(
                setUpCamera = setUpCamera,
                previewingCamera = previewingCamera,
                captureThread = captureThread,
                previewCaptureSession = previewCaptureSession,
                recognizersRunner = recognizersRunner
            )
        }

        else -> super.consume(action)
    }

    companion object {
        private fun startRecording(
            setUpCamera: SetUpCamera,
            videoFilePath: String,
            videoImageButton: ImageButton,
            cameraTextureView: TextureView,
            cameraDevice: CameraDevice,
            chronometer: Chronometer,
            recordingCaptureSessionStateCallback: CameraCaptureSession.StateCallback
        ): MediaRecorder {
            videoImageButton.setImageResource(R.mipmap.btn_video_busy)

            val mediaRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)

                setOutputFile(videoFilePath)

                setOutputFormat(MPEG_4)
                setVideoEncoder(H264)

                setVideoEncodingBitRate(5_000_000)  // TODO: Move the setting to the activity
                setVideoFrameRate(30)  // TODO: Move the setting to the activity

                setVideoSize(setUpCamera.videoSize.width, setUpCamera.videoSize.height)
                setOrientationHint(setUpCamera.totalRotation)

                prepare()
            }

            val surfaceTexture = cameraTextureView.surfaceTexture
            surfaceTexture.setDefaultBufferSize(setUpCamera.previewSize.width, setUpCamera.previewSize.height)
            val previewSurface = Surface(surfaceTexture)
            val recordSurface = mediaRecorder.surface

            cameraDevice.createCaptureSession(
                listOf(previewSurface, recordSurface, setUpCamera.imageReader.surface),
                recordingCaptureSessionStateCallback,
                null
            )

            mediaRecorder.start()

            chronometer.postApply {
                base = SystemClock.elapsedRealtime()
                visibility = View.VISIBLE
                start()
            }

            return mediaRecorder
        }
    }
}
