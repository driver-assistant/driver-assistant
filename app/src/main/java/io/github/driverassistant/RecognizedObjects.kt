package io.github.driverassistant

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

sealed class RecognizedObject {
    abstract fun paintOn(canvas: Canvas)
}

// TODO: add more types of object

data class RedLine(
    private val x0: Int,
    private val y0: Int,
    private val x1: Int,
    private val y1: Int
) : RecognizedObject() {

    override fun paintOn(canvas: Canvas) {
        canvas.drawLine(x0.f, y0.f, x1.f, y1.f, redLinePaint)
    }

    companion object {
        private val redLinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 10.0f
            color = Color.parseColor("#CC0000")
        }

        private val Int.f get() = toFloat()
    }
}
