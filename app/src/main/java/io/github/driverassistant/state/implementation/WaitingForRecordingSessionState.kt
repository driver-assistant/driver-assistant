package io.github.driverassistant.state.implementation

import android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
import android.os.HandlerThread
import io.github.driverassistant.util.camera.PreviewingCamera
import io.github.driverassistant.util.camera.RecordingCamera
import io.github.driverassistant.util.camera.SetUpCamera
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.RecordingCaptureSessionConfiguredAction

class WaitingForRecordingSessionState(
    private val setUpCamera: SetUpCamera,
    private val previewingCamera: PreviewingCamera,
    private val recordingCamera: RecordingCamera,
    private val captureThread: HandlerThread
) : MainScreenActivityState() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is RecordingCaptureSessionConfiguredAction -> {
            val captureRequestBuilder = previewingCamera.cameraDevice.createCaptureRequest(TEMPLATE_RECORD).apply {
                addTarget(previewingCamera.previewSurface)
                addTarget(recordingCamera.recordSurface)
            }

            val recordingCaptureSession = action.recordingCaptureSession.apply {
                setRepeatingRequest(captureRequestBuilder.build(), null, null)
            }

            ResumedRecordingState(
                setUpCamera = setUpCamera,
                previewingCamera = previewingCamera,
                recordingCamera = recordingCamera,
                recordingCaptureSession = recordingCaptureSession,
                captureThread = captureThread
            )
        }

        else -> super.consume(action)
    }
}
