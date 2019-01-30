package io.github.driverassistant.state.implementation

import android.os.HandlerThread
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.PreviewCaptureSessionConfiguredAction
import io.github.driverassistant.util.camera.PreviewingCamera
import io.github.driverassistant.util.camera.SetUpCamera
import io.github.driverassistant.util.handler

class WaitingForPreviewSessionState(
    private val setUpCamera: SetUpCamera,
    private val previewingCamera: PreviewingCamera,
    private val captureThread: HandlerThread
) : MainScreenActivityState() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is PreviewCaptureSessionConfiguredAction -> {
            val previewCaptureSession = action.previewCaptureSession.apply {
                setRepeatingRequest(previewingCamera.captureRequestBuilder.build(), null, captureThread.handler)
            }

            ResumedPreviewState(
                setUpCamera = setUpCamera,
                previewingCamera = previewingCamera,
                captureThread = captureThread,
                previewCaptureSession = previewCaptureSession
            )
        }

        else -> super.consume(action)
    }
}
