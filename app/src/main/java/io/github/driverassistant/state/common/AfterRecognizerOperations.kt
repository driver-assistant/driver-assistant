package io.github.driverassistant.state.common

import android.widget.TextView
import io.github.driverassistant.RecognizedObjectsView
import io.github.driverassistant.util.handler
import io.github.driverassistant.util.postApply
import kotlinx.android.synthetic.main.activity_main_screen.*

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
