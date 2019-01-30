package io.github.driverassistant.util.camera

import android.media.ImageReader
import android.util.Size

class SetUpCamera(
    val onImageAvailableListener: ImageReader.OnImageAvailableListener,
    val totalRotation: Int,
    val previewSize: Size,
    val videoSize: Size,
    val imageReader: ImageReader
)
