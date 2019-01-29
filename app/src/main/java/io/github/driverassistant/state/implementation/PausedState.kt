package io.github.driverassistant.state.implementation

import io.github.driverassistant.state.common.startCaptureThread
import io.github.driverassistant.state.*

class PausedState : MainScreenActivityState() {
    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is ActivityResumedAction -> {
            action.cameraTextureView.surfaceTextureListener = action.surfaceTextureListener
            val captureThread = startCaptureThread()

            ResumedState(captureThread)
        }

        else -> super.consume(action)
    }
}
