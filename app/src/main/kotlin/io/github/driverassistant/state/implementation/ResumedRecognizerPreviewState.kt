package io.github.driverassistant.state.implementation

import android.hardware.camera2.CameraCaptureSession
import android.os.HandlerThread
import io.github.driverassistant.RecognizersRunner
import io.github.driverassistant.state.ImageShotAction
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.RecognizerImageButtonClickedAction
import io.github.driverassistant.util.camera.PreviewingCamera
import io.github.driverassistant.util.camera.SetUpCamera

class ResumedRecognizerPreviewState(
    private val setUpCamera: SetUpCamera,
    private val previewingCamera: PreviewingCamera,
    private val captureThread: HandlerThread,
    private val previewCaptureSession: CameraCaptureSession,
    private val recognizersRunner: RecognizersRunner
) : MainScreenActivityState() {

    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is RecognizerImageButtonClickedAction -> {
            action.recognizersRunnerListener.onEnd()

            ResumedPreviewState(
                setUpCamera = setUpCamera,
                previewingCamera = previewingCamera,
                captureThread = captureThread,
                previewCaptureSession = previewCaptureSession
            )
        }

        is ImageShotAction -> {
            recognizersRunner.recognize(action.imageData)

            this
        }

        else -> super.consume(action)
    }
}
