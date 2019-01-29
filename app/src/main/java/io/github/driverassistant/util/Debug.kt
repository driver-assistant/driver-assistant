package io.github.driverassistant.util

import android.app.Activity
import android.widget.Toast

fun Activity.shortToast(resId: Int) {
    Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show()
}
