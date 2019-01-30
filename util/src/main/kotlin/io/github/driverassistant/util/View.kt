package io.github.driverassistant.util

import android.view.View
import android.view.View.*

fun <ViewType : View> ViewType.postApply(block: ViewType.() -> Unit) {
    this.post { this.block() }
}

private const val VISIBILITY_WITHOUT_BARS =
    SYSTEM_UI_FLAG_LAYOUT_STABLE or
            SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            SYSTEM_UI_FLAG_FULLSCREEN or
            SYSTEM_UI_FLAG_HIDE_NAVIGATION

fun View.hideStatusAndNavigationBars() {
    this.postApply {
        systemUiVisibility = VISIBILITY_WITHOUT_BARS
    }
}
