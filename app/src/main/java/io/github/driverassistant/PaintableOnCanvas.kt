package io.github.driverassistant

import android.graphics.Canvas
import android.graphics.Paint
import io.github.driverassistant.recognizer.Circle
import io.github.driverassistant.recognizer.Line
import io.github.driverassistant.recognizer.RecognizedObjectElement

interface PaintableOnCanvas {
    fun paintOn(canvas: Canvas)
}

object InvisiblePaintable : PaintableOnCanvas {
    override fun paintOn(canvas: Canvas) {}
}

fun RecognizedObjectElement.toPaintableOnCanvas(): PaintableOnCanvas = when (this) {
    is Line -> this.toPaintableOnCanvas()
    is Circle -> this.toPaintableOnCanvas()
}

fun Line.toPaintableOnCanvas(): PaintableOnCanvas {
    val line = this

    val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = line.thickness
        color = line.color
    }

    return object : PaintableOnCanvas {
        override fun paintOn(canvas: Canvas) {
            // TODO: research orientation (#4)
            canvas.drawLine(line.y0, line.x0, line.y1, line.x1, paint)
        }
    }
}

fun Circle.toPaintableOnCanvas(): PaintableOnCanvas {
    val (x, y, r, color, strokeThickness, needsFill) = this  // destruct only for smart casts work

    if (!needsFill && strokeThickness == null) {
        return InvisiblePaintable
    }

    val paint = Paint().apply {
        this.color = color

        when {
            strokeThickness == null -> {
                style = Paint.Style.FILL
            }

            !needsFill -> {
                style = Paint.Style.STROKE
                strokeWidth = strokeThickness
            }

            else -> {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = strokeThickness
            }
        }
    }

    return object : PaintableOnCanvas {
        override fun paintOn(canvas: Canvas) {
            // TODO: research orientation (#4)
            canvas.drawCircle(y, x, r, paint)
        }
    }
}
