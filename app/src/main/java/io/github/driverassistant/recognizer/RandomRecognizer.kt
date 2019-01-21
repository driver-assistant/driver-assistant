package io.github.driverassistant.recognizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random
import kotlin.random.nextInt

class RandomRecognizer : Recognizer {
    override fun recognize(image: LatestImage): List<PaintableOnCanvas> {
        val objects = mutableListOf<PaintableOnCanvas>()

        val (w, h) = image.size

        for (i in 1..objectCount) {
            val orientationId = Random.nextInt(diff.indices)

            val x0 = Random.nextInt(delta..(w - delta))
            val y0 = Random.nextInt(delta..(h - delta))

            val x1 = x0 + diff[orientationId][0]
            val y1 = y0 + diff[orientationId][1]

            objects.add(RedArrow(y0, x0, y1, x1))
        }

        return objects
    }

    companion object {
        private const val objectCount = 10

        private const val delta = 50

        private val diff = listOf(
            listOf(delta, delta),
            listOf(delta, 0),
            listOf(delta, -delta),
            listOf(0, -delta),
            listOf(-delta, delta),
            listOf(-delta, 0),
            listOf(-delta, -delta),
            listOf(0, -delta)
        )
    }
}

data class RedArrow(
    private val x0: Int,
    private val y0: Int,
    private val x1: Int,
    private val y1: Int
) : PaintableOnCanvas {

    override fun paintOn(canvas: Canvas) {
        // Draw line:
        canvas.drawLine(x0.f, y0.f, x1.f, y1.f, redPaint)

        // Draw arrow:
        canvas.drawCircle(x1.f, y1.f, arrowRadius, redPaint)
    }

    companion object {
        private const val arrowRadius = 5.0f

        private val redPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 10.0f
            color = Color.parseColor("#CC0000")
        }

        private val Int.f get() = toFloat()
    }
}
