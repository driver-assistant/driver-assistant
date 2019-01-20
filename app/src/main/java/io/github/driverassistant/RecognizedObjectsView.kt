package io.github.driverassistant

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class RecognizedObjectsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    var recognizedObjects: Iterable<RecognizedObject> = listOf()

    override fun onDraw(canvas: Canvas) {
        recognizedObjects.forEach { it.paintOn(canvas) }
    }
}
