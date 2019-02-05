package io.github.driverassistant.state.common

import android.os.HandlerThread

fun startCaptureThread(): HandlerThread {
    val thread = HandlerThread("Driver Assistant Capture Thread")
    thread.start()

    return thread
}
