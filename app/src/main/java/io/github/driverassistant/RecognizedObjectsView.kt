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

    var paintables: Iterable<PaintableOnCanvas> = emptyList()

    override fun onDraw(canvas: Canvas) {
        paintables.forEach { it.paintOn(canvas) }
    }
}
