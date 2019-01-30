package io.github.driverassistant.util.camera

import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER
import android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION
import android.hardware.camera2.CaptureResult.CONTROL_AF_STATE
import android.os.HandlerThread
import io.github.driverassistant.util.handler

class ShootingCamera(
    private val captureSession: CameraCaptureSession,
    requestTemplateType: Int,
    private val setUpCamera: SetUpCamera,
    private val previewingCamera: PreviewingCamera,
    private val captureThread: HandlerThread
) {
    private val focusRequest = previewingCamera.cameraDevice.createCaptureRequest(requestTemplateType).apply {
        addTarget(previewingCamera.previewSurface)
        this[CONTROL_AF_TRIGGER] = CONTROL_AF_TRIGGER_START
    }.build()

    private val shotImageRequest = previewingCamera.cameraDevice.createCaptureRequest(requestTemplateType).apply {
        addTarget(setUpCamera.imageReader.surface)
        this[JPEG_ORIENTATION] = setUpCamera.totalRotation
    }.build()

    private var captureState = State.PREVIEW

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)

            if (captureState == State.WAIT_LOCK) {
                captureState = State.PREVIEW

                val afState = result[CONTROL_AF_STATE]
                if (afState == CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                    captureSession.capture(shotImageRequest, null, null)
                }
            }
        }
    }

    fun submitRequestForNextShot() {
        captureState = State.WAIT_LOCK

        captureSession.capture(focusRequest, captureCallback, captureThread.handler)
    }

    companion object {
        // TODO: Rename
        enum class State {
            PREVIEW,
            WAIT_LOCK;
        }
    }
}
