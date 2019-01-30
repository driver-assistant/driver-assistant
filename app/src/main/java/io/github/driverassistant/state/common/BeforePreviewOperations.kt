package io.github.driverassistant.state.common

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import android.view.TextureView
import io.github.driverassistant.util.camera.PreviewingCamera

fun startPreview(
    cameraTextureView: TextureView,
    previewSize: Size,
    cameraDevice: CameraDevice,
    imageReader: ImageReader,
    previewCaptureSessionStateCallback: CameraCaptureSession.StateCallback
): PreviewingCamera {
    val surfaceTexture = cameraTextureView.surfaceTexture.apply {
        setDefaultBufferSize(previewSize.width, previewSize.height)
    }
    val previewSurface = Surface(surfaceTexture)

    val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
        addTarget(previewSurface)
    }

    cameraDevice.createCaptureSession(
        listOf(previewSurface, imageReader.surface),
        previewCaptureSessionStateCallback,
        null
    )

    return PreviewingCamera(
        captureRequestBuilder = captureRequestBuilder,
        cameraDevice = cameraDevice,
        previewSurface = previewSurface
    )
}
