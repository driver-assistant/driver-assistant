package io.github.driverassistant.util.camera

import android.media.ImageReader
import android.util.Size

class SetUpCamera(
    val onImageAvailableListener: ImageReader.OnImageAvailableListener,
    val cameraId: String,
    val totalRotation: Int,
    val previewSize: Size,
    val videoSize: Size,
    val imageSize: Size,
    val imageReader: ImageReader
)
