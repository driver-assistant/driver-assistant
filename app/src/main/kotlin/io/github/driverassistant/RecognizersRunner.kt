package io.github.driverassistant

import android.os.HandlerThread
import android.util.Log
import io.github.driverassistant.recognizer.ImageData
import io.github.driverassistant.recognizer.Recognizer
import io.github.driverassistant.util.camera.ShootingCamera
import io.github.driverassistant.util.handler
import io.github.driverassistant.util.postDelayed
import kotlin.math.roundToLong

class RecognizersRunner(
    fps: Double,
    private val recognizersRunnerListener: RecognizersRunnerListener,
    private val recognizers: Iterable<Recognizer>,
    private val shootingCamera: ShootingCamera,
    private val captureThread: HandlerThread
) {
    private val delay = (1000.0 / fps).roundToLong()

    fun recognize(imageData: ImageData) {
        Log.d(TAG, "Shooting request time: ${System.currentTimeMillis()}, delay: $delay ms")

        captureThread.handler.postDelayed(delay) { shootingCamera.submitRequestForNextShot() }

        val paintables = recognizers
            .flatMap { recognizer -> recognizer.recognize(imageData) }
            .flatMap { recognizedObject -> recognizedObject.elements }
            .map { recognizedObjectElement -> recognizedObjectElement.toPaintableOnCanvas() }

        recognizersRunnerListener.onResult(imageData, paintables)
    }

    companion object {
        private const val TAG = "RecognizersRunner"

        interface RecognizersRunnerListener {
            fun onResult(imageData: ImageData, objects: Iterable<PaintableOnCanvas>)

            fun onEnd()
        }
    }
}
