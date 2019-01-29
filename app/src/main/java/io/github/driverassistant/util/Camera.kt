package io.github.driverassistant.util

import android.hardware.camera2.CameraCharacteristics
import android.view.Surface

fun Int.toOrientation() = when (this) {
    Surface.ROTATION_0 -> 0
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> throw IllegalArgumentException("$this")
}

fun sensorToDeviceRotation(cameraCharacteristics: CameraCharacteristics, deviceRotation: Int): Int {
    val sensorOrientation = cameraCharacteristics[CameraCharacteristics.SENSOR_ORIENTATION]!!
    val deviceOrientation = deviceRotation.toOrientation()

    return (sensorOrientation + deviceOrientation + 360) % 360
}
