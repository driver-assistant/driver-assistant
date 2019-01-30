package io.github.driverassistant.util.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.LENS_FACING
import android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
import android.util.Size
import android.view.Surface
import kotlin.math.sign

fun Int.toOrientation() = when (this) {
    Surface.ROTATION_0 -> 0
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> throw IllegalArgumentException("$this")
}

fun sensorToDeviceRotation(cameraCharacteristics: CameraCharacteristics, deviceRotation: Int): Int {
    val sensorOrientation = cameraCharacteristics[SENSOR_ORIENTATION]!!
    val deviceOrientation = deviceRotation.toOrientation()

    return (sensorOrientation + deviceOrientation + 360) % 360
}

fun CameraManager.findBackCameraId() = this
    .cameraIdList
    .first { this.cameraFacing(it) == LENS_FACING_BACK }!!

private fun CameraManager.cameraFacing(cameraId: String) = this
    .getCameraCharacteristics(cameraId)[LENS_FACING]!!

private val Size.area get() = this.width.toLong() * this.height.toLong()

private val sizeComparatorByArea = Comparator<Size> { lhs, rhs -> (lhs.area - rhs.area).sign }

fun Array<Size>.chooseOptimalSize(width: Int, height: Int) = this
    .filter { it.width >= width && it.height >= height }
    .minWith(sizeComparatorByArea)
    ?: this.first()
