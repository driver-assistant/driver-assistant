package io.github.driverassistant

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class RecognizedObjectsView : View {

    constructor(context: Context) :
            super(context)

    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    var paintables: Iterable<PaintableOnCanvas> = emptyList()

    override fun onDraw(canvas: Canvas) {
        paintables.forEach { it.paintOn(canvas) }
    }
}
