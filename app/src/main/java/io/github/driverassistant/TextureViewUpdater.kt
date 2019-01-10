package io.github.driverassistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.util.Size
import android.view.Surface
import android.view.TextureView

/*
https://github.com/googlesamples/android-Camera2Video
*/
class TextureViewUpdater(
    private val cameraTextureView: TextureView,
    private val mainScreenActivity: MainScreenActivity
) {

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            tryOpenCamera()
        }
    }

    val cameraCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
        }

        override fun onConfigured(session: CameraCaptureSession) {
            if (cameraDevice == null) {
                return
            }

            cameraCaptureSession = session
            updatePreview()
        }
    }

    private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera

            val texture = requireNotNull(cameraTextureView.surfaceTexture).apply {
                setDefaultBufferSize(imageDimension.width, imageDimension.height)
            }

            val surface = Surface(texture)

            captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            camera.createCaptureSession(listOf(surface), cameraCaptureSessionStateCallback, null)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private lateinit var cameraId: String
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var imageDimension: Size

    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    init {
        cameraTextureView.surfaceTextureListener = textureListener
    }

    private fun updatePreview() {
        if (cameraDevice == null) {
            return
        }

        captureRequestBuilder[CaptureRequest.CONTROL_MODE] = CaptureRequest.CONTROL_MODE_AUTO

        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler)
    }

    fun onResume() {
        mBackgroundThread = HandlerThread("Camera Background Thread").also {
            it.start()
            mBackgroundHandler = Handler(it.looper)
        }

        if (cameraTextureView.isAvailable) {
            tryOpenCamera()
        } else {
            cameraTextureView.surfaceTextureListener = textureListener
        }
    }

    fun onPause() {
        mBackgroundThread?.quitSafely()

        mBackgroundThread?.join()
        mBackgroundThread = null
        mBackgroundHandler = null
    }

    private fun tryOpenCamera() {
        val manager = mainScreenActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraId = manager.findBackCameraId()
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = requireNotNull(characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP])

        imageDimension = map.findMaxSupportedResolutionForSurfaceTexture()

        if (ActivityCompat.checkSelfPermission(mainScreenActivity, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                mainScreenActivity,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )

            return
        }

        manager.openCamera(cameraId, cameraDeviceStateCallback, null)
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 200

        private fun CameraManager.findBackCameraId() = this
            .cameraIdList
            .map { it to this.getCameraCharacteristics(it)[CameraCharacteristics.LENS_FACING] }
            .first { (_, facing) -> facing == CameraCharacteristics.LENS_FACING_BACK }
            .first

        private fun StreamConfigurationMap.findMaxSupportedResolutionForSurfaceTexture() = this
            .getOutputSizes(SurfaceTexture::class.java)[0]
    }
}
