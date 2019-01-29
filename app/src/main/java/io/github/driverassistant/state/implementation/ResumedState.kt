package io.github.driverassistant.state.implementation

import android.Manifest
import android.Manifest.permission.CAMERA
import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.ImageFormat
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
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import android.util.Size
import io.github.driverassistant.*
import io.github.driverassistant.state.ActivityPausedAction
import io.github.driverassistant.state.MainScreenActivityAction
import io.github.driverassistant.state.MainScreenActivityState
import io.github.driverassistant.state.SurfaceTextureAvailableAction
import io.github.driverassistant.state.common.stopCaptureThread
import io.github.driverassistant.util.*
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
            val totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceRotation)

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
                setOnImageAvailableListener(onImageAvailableListener, captureThread.handler)  // TODO: use another thread
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
                    RequestCode.CAMERA.ordinal
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

        private fun CameraManager.findBackCameraId() = this
            .cameraIdList
            .first { this.cameraFacing(it) == CameraMetadata.LENS_FACING_BACK }

        private fun CameraManager.cameraFacing(cameraId: String) = this
            .getCameraCharacteristics(cameraId)[CameraCharacteristics.LENS_FACING]

        private val Size.area get() = this.width.toLong() * this.height.toLong()

        private val sizeComparatorByArea = Comparator<Size> { lhs, rhs -> (lhs.area - rhs.area).sign }

        private fun Array<Size>.chooseOptimalSize(width: Int, height: Int) = this
            .filter { it.width >= width && it.height >= height }
            .minWith(sizeComparatorByArea)
            ?: this.first()
    }
}
