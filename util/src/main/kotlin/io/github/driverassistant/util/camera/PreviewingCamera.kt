package io.github.driverassistant.util.camera

import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.view.Surface

class PreviewingCamera(
    val previewSurface: Surface,
    val cameraDevice: CameraDevice,
    val captureRequestBuilder: CaptureRequest.Builder
)
