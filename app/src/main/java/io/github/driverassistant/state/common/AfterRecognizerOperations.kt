package io.github.driverassistant.state.common

import android.widget.TextView
import io.github.driverassistant.RecognizedObjectsView
import io.github.driverassistant.util.postApply

fun cleanRecognizersScreen(
    recognizedObjectsView: RecognizedObjectsView,
    statsTextView: TextView
) {
    recognizedObjectsView.postApply {
        paintables = emptyList()
        invalidate()
    }
    statsTextView.postApply { text = "" }
}
