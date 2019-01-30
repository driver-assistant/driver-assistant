package io.github.driverassistant.state.implementation

import io.github.driverassistant.util.camera.SetUpCamera
import io.github.driverassistant.state.ActivityResumedAction
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.common.startCaptureThread
import io.github.driverassistant.util.handler

class PausedCameraState(private val setUpCamera: SetUpCamera) : MainScreenActivityState() {
    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is ActivityResumedAction -> {
            val captureThread = startCaptureThread()

            setUpCamera.apply {
                imageReader.setOnImageAvailableListener(onImageAvailableListener, captureThread.handler)
            }

            ResumedCameraState(captureThread, setUpCamera)
        }

        else -> super.consume(action)
    }
}
