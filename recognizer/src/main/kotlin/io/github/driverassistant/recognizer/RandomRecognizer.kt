package io.github.driverassistant.recognizer

import android.support.annotation.ColorInt
import kotlin.random.Random
import kotlin.random.nextInt

class RandomRecognizer : Recognizer {
    override fun recognize(image: LatestImage): List<RecognizedObject> {
        val objects = mutableListOf<RecognizedObject>()

        for (i in 1..OBJECT_COUNT) {
            val angle = Random.nextFloat() * 2 * Math.PI

            val x0 = Random.nextInt(LENGTH..(image.width - LENGTH)).toFloat()
            val y0 = Random.nextInt(LENGTH..(image.height - LENGTH)).toFloat()

            val x1 = x0 + LENGTH * Math.cos(angle).toFloat()
            val y1 = y0 + LENGTH * Math.sin(angle).toFloat()

            objects.add(RedArrow(x0, y0, x1, y1))
        }

        return objects
    }

    companion object {
        private const val OBJECT_COUNT = 10

        private const val LENGTH = 50
    }
}

data class RedArrow(
    private val x0: Float,
    private val y0: Float,
    private val x1: Float,
    private val y1: Float
) : RecognizedObject() {

    override val elements: List<RecognizedObjectElement> = listOf(
        Line(x0, y0, x1, y1, RED_COLOR, THICKNESS),
        Circle(x1, y1, ARROW_RADIUS, RED_COLOR, THICKNESS, needsFill = true)
    )

    companion object {
        private const val ARROW_RADIUS = 5.0f
        private const val THICKNESS = 10.0f

        @ColorInt
        private const val RED_COLOR = 0xFFCC0000.toInt()
    }
}
