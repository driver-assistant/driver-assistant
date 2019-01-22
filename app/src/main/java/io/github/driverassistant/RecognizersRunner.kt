package io.github.driverassistant

import android.os.Handler
import android.os.HandlerThread
import io.github.driverassistant.recognizer.LatestImage
import io.github.driverassistant.recognizer.Recognizer
import kotlin.math.roundToLong

class RecognizersRunner(
    private val mainScreenActivity: MainScreenActivity,
    private val fps: Double,
    private val handler: Handler,
    private val recognizers: List<Recognizer>
) : Runnable {

    override fun run() {
        iterate()

        handler.postDelayed(this, (1000 / fps).roundToLong())
    }

    private fun iterate() {
        mainScreenActivity.lockFocusToTakeShot()
        val latestImage = waitForImage()

        val statsText = with(latestImage) { "${bytes.size} bytes, $wight x $height" }
        mainScreenActivity.statsText.post { mainScreenActivity.statsText.text = statsText }

        val paintables = recognizers
            .flatMap { recognizer -> recognizer.recognize(latestImage) }
            .flatMap { recognizedObject -> recognizedObject.elements }
            .map { recognizedObjectElement -> recognizedObjectElement.toPaintableOnCanvas() }

        mainScreenActivity.recognizedObjects.paintables = paintables
        mainScreenActivity.recognizedObjects.invalidate()
    }

    private fun waitForImage(): LatestImage {
        while (true) {
            val latestImage = mainScreenActivity.latestImage

            if (latestImage != null) {
                mainScreenActivity.latestImage = null

                return latestImage
            }
        }
    }
}

data class RecognizersRunnerThreadChain(
    val recognizersRunner: RecognizersRunner,
    val thread: HandlerThread,
    val threadHandler: Handler
)

fun startRecognizersRunnerThreadChain(
    mainScreenActivity: MainScreenActivity,
    fps: Double,
    recognizers: List<Recognizer>
): RecognizersRunnerThreadChain {
    with(HandlerThread("Driver Assistant Recognizer Thread")) {
        this.start()
        val looper = Handler(this.looper)

        val recognizersRunner = RecognizersRunner(mainScreenActivity, fps, looper, recognizers)
        looper.post(recognizersRunner)

        return RecognizersRunnerThreadChain(recognizersRunner, this, looper)
    }
}

fun RecognizersRunnerThreadChain.stop() {
    threadHandler.removeCallbacks(recognizersRunner)
    thread.quitSafely()
    thread.join()
}
