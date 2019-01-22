package io.github.driverassistant.recognizer

import android.support.annotation.ColorInt

sealed class RecognizedObjectElement

data class Line(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    @ColorInt val color: Int,
    val thickness: Float
) : RecognizedObjectElement()

data class Circle(
    val x: Float,
    val y: Float,
    val r: Float,
    @ColorInt val color: Int,
    val strokeThickness: Float?,
    val needsFill: Boolean
) : RecognizedObjectElement()
