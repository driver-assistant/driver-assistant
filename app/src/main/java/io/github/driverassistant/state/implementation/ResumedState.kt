package io.github.driverassistant.state.implementation

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.ImageFormat.JPEG
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import android.util.Size
import io.github.driverassistant.*
import io.github.driverassistant.R
import io.github.driverassistant.state.ActivityPausedAction
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.SurfaceTextureAvailableAction
import io.github.driverassistant.state.common.stopCaptureThread
import io.github.driverassistant.util.*
import io.github.driverassistant.util.camera.SetUpCamera
import io.github.driverassistant.util.camera.chooseOptimalSize
import io.github.driverassistant.util.camera.findBackCameraId
import io.github.driverassistant.util.camera.sensorToDeviceRotation
import kotlin.math.sign

class ResumedState(private val captureThread: HandlerThread) : MainScreenActivityState() {
    override fun consume(action: MainScreenActivityAction): MainScreenActivityState = when (action) {
        is ActivityPausedAction -> {
            stopCaptureThread(captureThread)

            PausedState()
        }

        is SurfaceTextureAvailableAction -> {
            val setUpCamera = setupCamera(
                width = action.width,
                height = action.height,
                onImageAvailableListener = action.onImageAvailableListener,
                activity = action.activity,
                cameraDeviceStateCallback = action.cameraDeviceStateCallback,
                captureThread = captureThread
            )

            ResumedCameraState(captureThread, setUpCamera)
        }

        else -> super.consume(action)
    }

    companion object {
        private fun setupCamera(
            width: Int,
            height: Int,
            onImageAvailableListener: ImageReader.OnImageAvailableListener,
            captureThread: HandlerThread,
            activity: Activity,
            cameraDeviceStateCallback: CameraDevice.StateCallback
        ): SetUpCamera {
            val cameraManager = activity.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager

            val cameraId = cameraManager.findBackCameraId()
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            val deviceRotation = activity.windowManager.defaultDisplay.rotation  // TODO: research video rotation (#3)
            val totalRotation =
                sensorToDeviceRotation(cameraCharacteristics, deviceRotation)

            val swapMetrics = totalRotation == 90 || totalRotation == 270

            val (rotatedWidth, rotatedHeight) = when (swapMetrics) {
                false -> width to height
                true -> height to width
            }

            val previewSize = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!
                .getOutputSizes(SurfaceTexture::class.java)
                .chooseOptimalSize(rotatedWidth, rotatedHeight)

            val videoSize = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!
                .getOutputSizes(MediaRecorder::class.java)
                .chooseOptimalSize(rotatedWidth, rotatedHeight)

            val imageSize = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!
                .getOutputSizes(JPEG)
                .chooseOptimalSize(rotatedWidth, rotatedHeight)

            val imageReader = ImageReader.newInstance(imageSize.width, imageSize.height, JPEG, 1).apply {
                setOnImageAvailableListener(onImageAvailableListener, captureThread.handler)
            }

            while (
                permissionsAvailableOnThisAndroidVersion &&
                checkSelfPermission(activity, CAMERA) != PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA)) {
                    activity.shortToast(R.string.no_camera_permission)
                }

                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(CAMERA),
                    RequestCode.CAMERA.id
                )
            }

            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, captureThread.handler)

            return SetUpCamera(
                onImageAvailableListener = onImageAvailableListener,
                cameraId = cameraId,
                totalRotation = totalRotation,
                previewSize = previewSize,
                videoSize = videoSize,
                imageSize = imageSize,
                imageReader = imageReader
            )
        }
    }
}
