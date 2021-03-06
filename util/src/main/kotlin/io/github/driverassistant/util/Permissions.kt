package io.github.driverassistant.util

import android.os.Build

val permissionsAvailableOnThisAndroidVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

enum class RequestCode {
    CAMERA,
    WRITE_EXTERNAL_STORAGE;

    val id: Int get() = ordinal

    companion object {
        fun byId(id: Int) = RequestCode.values()[id]
    }
}
