package io.github.driverassistant.util

import android.os.Handler

abstract class LoopWithDelay(private val handler: Handler, private val delay: Long) : Runnable {
    final override fun run() {
        iterate()

        // TODO: Maybe remember startTime and post (delay - (currentTime - startTime))?
        handler.postDelayed(this, delay)
    }

    protected abstract fun iterate()
}
