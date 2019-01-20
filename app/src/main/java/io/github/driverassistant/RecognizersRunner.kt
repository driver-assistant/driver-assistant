package io.github.driverassistant

import android.os.Handler
import android.os.HandlerThread
import io.github.driverassistant.MainScreenActivity.Companion.LatestImageResult
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.random.nextInt

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
        val (w, h) = size

        val objects = mutableListOf<RecognizedObject>()

        for (i in 1..10) {  // TODO: Replace the dummy with logic code
            val x0 = Random.nextInt(delta..(w - delta))
            val y0 = Random.nextInt(delta..(h - delta))

            val x1 = x0 + diff[i % diff.size][0]
            val y1 = y0 + diff[i % diff.size][1]

            objects.add(RedLine(y0, x0, y1, x1))
        }

        updateObjects(objects)
    }

    private fun updateObjects(objects: Iterable<RecognizedObject>) {
        mainScreenActivity.recognizedObjects.recognizedObjects = objects
        mainScreenActivity.recognizedObjects.invalidate()
    }

    companion object {
        private const val delta = 50

        private val diff = listOf(
            listOf(delta, delta),
            listOf(delta, -delta),
            listOf(-delta, delta),
            listOf(-delta, -delta)
        )
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
