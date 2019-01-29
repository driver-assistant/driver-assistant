package io.github.driverassistant

import android.app.Activity
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaRecorder
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast

data class SetUpCamera(
    val onImageAvailableListener: OnImageAvailableListener,
    val cameraId: String,
    val totalRotation: Int,
    val previewSize: Size,
    val videoSize: Size,
    val imageSize: Size,
    val imageReader: ImageReader
)

data class PreviewingCamera(
    val cameraDevice: CameraDevice,
    val captureRequestBuilder: CaptureRequest.Builder
)

data class RecordingCamera(
    val previewSurface: Surface,
    val recordSurface: Surface,
    val mediaRecorder: MediaRecorder
)

enum class State {
    PREVIEW,
    WAIT_LOCK;
}
