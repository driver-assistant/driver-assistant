package io.github.driverassistant.state.implementation

import android.hardware.camera2.CameraCaptureSession
import android.media.MediaRecorder
import android.os.HandlerThread
import android.view.View.INVISIBLE
import io.github.driverassistant.R
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.RecordSwitchAction
import io.github.driverassistant.state.common.startPreview
import io.github.driverassistant.util.camera.PreviewingCamera
import io.github.driverassistant.util.camera.SetUpCamera
import io.github.driverassistant.util.postApply

class ResumedRecordingState(
    private val setUpCamera: SetUpCamera,
    private val previewingCamera: PreviewingCamera,
    private val mediaRecorder: MediaRecorder,
    private val recordingCaptureSession: CameraCaptureSession,
    private val captureThread: HandlerThread
) : MainScreenActivityState() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is RecordSwitchAction -> {
            action.videoImageButton.setImageResource(R.mipmap.btn_video_online)

            mediaRecorder.apply {
                stop()
                reset()
            }

            action.chronometer.postApply {
                stop()
                visibility = INVISIBLE
            }

            val newPreviewingCamera = startPreview(
                cameraDevice = previewingCamera.cameraDevice,
                previewSize = setUpCamera.previewSize,
                imageReader = setUpCamera.imageReader,
                previewCaptureSessionStateCallback = action.previewCaptureSessionStateCallback,
                cameraTextureView = action.cameraTextureView
            )

            WaitingForPreviewSessionState(
                setUpCamera = setUpCamera,
                previewingCamera = newPreviewingCamera,
                captureThread = captureThread
            )
        }

        else -> super.consume(action)
    }
}
