package io.github.driverassistant

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import io.github.driverassistant.recognizer.PaintableOnCanvas

class RecognizedObjectsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    var paintableObjects: Iterable<PaintableOnCanvas> = emptyList()

    override fun onDraw(canvas: Canvas) {
        paintableObjects.forEach { it.paintOn(canvas) }
    }
}
