package io.github.driverassistant

import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER
import android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION
import android.hardware.camera2.CaptureResult.CONTROL_AF_STATE
import android.os.HandlerThread
import android.util.Log
import android.widget.TextView
import io.github.driverassistant.recognizer.LatestImage
import io.github.driverassistant.recognizer.Recognizer
import io.github.driverassistant.util.camera.ShootingCamera
import io.github.driverassistant.util.handler
import io.github.driverassistant.util.postApply
import io.github.driverassistant.util.postDelayed
import kotlin.math.roundToLong

class RecognizersRunner(
    fps: Double,
    private val statsTextView: TextView,
    private val recognizedObjectsView: RecognizedObjectsView,
    private val recognizers: Iterable<Recognizer>,
    private val shootingCamera: ShootingCamera,
    private val captureThread: HandlerThread
) {
    private val delay = (1000.0 / fps).roundToLong()

    fun recognize(latestImage: LatestImage) {
        Log.d(TAG, "Shooting request time: ${System.currentTimeMillis()}, delay: $delay ms")

        captureThread.handler.postDelayed(delay) { shootingCamera.submitRequestForNextShot() }

        val statsText = with(latestImage) { "${bytes.size} bytes, $width x $height" }
        statsTextView.postApply { text = statsText }

        val paintablesList = recognizers
            .flatMap { recognizer -> recognizer.recognize(latestImage) }
            .flatMap { recognizedObject -> recognizedObject.elements }
            .map { recognizedObjectElement -> recognizedObjectElement.toPaintableOnCanvas() }

        recognizedObjectsView.postApply {
            paintables = paintablesList
            invalidate()
        }
    }

    companion object {
        private const val TAG = "RecognizersRunner"
    }
}
