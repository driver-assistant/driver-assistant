package io.github.driverassistant.util

import android.os.Build

val permissionsAvailableOnThisAndroidVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

enum class RequestCode {
    CAMERA,
    WRITE_EXTERNAL_STORAGE_FOR_RECORDING,
    WRITE_EXTERNAL_STORAGE_FOR_LOG;

    val id: Int get() = ordinal

    companion object {
        fun byId(id: Int) = RequestCode.values()[id]
    }
}
