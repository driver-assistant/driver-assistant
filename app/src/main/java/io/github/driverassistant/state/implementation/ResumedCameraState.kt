package io.github.driverassistant.state.implementation

import android.os.HandlerThread
import io.github.driverassistant.PreviewingCamera
import io.github.driverassistant.SetUpCamera
import io.github.driverassistant.state.ActivityPausedAction
import io.github.driverassistant.state.CameraOpenedAction
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.common.startPreview
import io.github.driverassistant.state.common.stopCaptureThread

class ResumedCameraState(
    private val captureThread: HandlerThread,
    private val setUpCamera: SetUpCamera
) : MainScreenActivityState() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is ActivityPausedAction -> {
            stopCaptureThread(captureThread)
            setUpCamera.imageReader.setOnImageAvailableListener(null, null)

            PausedCameraState(setUpCamera)
        }

        is CameraOpenedAction -> {
            val previewingCamera = startPreview(
                previewSize = setUpCamera.previewSize,
                imageReader = setUpCamera.imageReader,
                cameraDevice = action.cameraDevice,
                cameraTextureView = action.cameraTextureView,
                previewCaptureSessionStateCallback = action.previewCaptureSessionStateCallback
            )

            WaitingForPreviewSessionState(
                previewingCamera = previewingCamera,
                setUpCamera = setUpCamera,
                captureThread = captureThread
            )
        }

        else -> super.consume(action)
    }
}
