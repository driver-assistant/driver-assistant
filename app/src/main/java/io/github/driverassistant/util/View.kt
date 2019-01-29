package io.github.driverassistant.util

import android.view.View

fun <ViewType : View> ViewType.postApply(block: ViewType.() -> Unit) {
    this.post { this.block() }
}
