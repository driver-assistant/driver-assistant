package io.github.driverassistant

import android.os.Handler
import android.os.HandlerThread
import io.github.driverassistant.MainScreenActivity.Companion.LatestImageResult
import kotlin.math.roundToLong

class RecognizersRunner(
    private val mainScreenActivity: MainScreenActivity,
    private val fps: Double,
    private val handler: Handler
) : Runnable {
    override fun run() {
        mainScreenActivity.lockFocus()
        val (bytes, size) = waitForImage()

        val statsText = "${bytes.size} bytes, ${size.first} x ${size.second}"

        mainScreenActivity.statsText.postOnAnimation { mainScreenActivity.statsText.text = statsText }

        process(bytes, size)

        handler.postDelayed(this, (1000 / fps).roundToLong())
    }

    private fun waitForImage(): LatestImageResult {
        while (true) {
            val latestImage = mainScreenActivity.latestImage

            if (latestImage != null) {
                mainScreenActivity.latestImage = null

                return latestImage
            }
        }
    }

    private fun process(bytes: ByteArray, size: Pair<Int, Int>) {
    }
}

data class RecognizersRunnerThreadChain(
    val recognizersRunner: RecognizersRunner,
    val thread: HandlerThread,
    val threadHandler: Handler
)

fun startRecognizersRunnerThreadChain(
    mainScreenActivity: MainScreenActivity,
    fps: Double
): RecognizersRunnerThreadChain {
    with(HandlerThread("Driver Assistant Recognizer Thread")) {
        this.start()
        val looper = Handler(this.looper)

        val recognizersRunner = RecognizersRunner(mainScreenActivity, fps, looper)
        looper.post(recognizersRunner)

        return RecognizersRunnerThreadChain(recognizersRunner, this, looper)
    }
}

fun RecognizersRunnerThreadChain.stop() {
    threadHandler.removeCallbacks(recognizersRunner)
    thread.quitSafely()
    thread.join()
}
