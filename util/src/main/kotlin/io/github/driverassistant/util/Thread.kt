package io.github.driverassistant.util

import android.os.Handler
import android.os.HandlerThread

val HandlerThread.handler get() = Handler(this.looper)

fun Handler.postDelayed(delay: Long, block: () -> Unit) = this.postDelayed(block, delay)
