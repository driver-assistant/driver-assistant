package io.github.driverassistant.state.common

import android.os.HandlerThread

fun stopCaptureThread(captureThread: HandlerThread) {
    captureThread.apply {
        quitSafely()
        join()
    }
}
