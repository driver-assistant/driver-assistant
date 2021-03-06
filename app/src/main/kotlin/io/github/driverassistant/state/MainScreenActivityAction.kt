package io.github.driverassistant.state

import android.app.Activity
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.media.ImageReader
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.Chronometer
import android.widget.ImageButton
import io.github.driverassistant.RecognizersRunner
import io.github.driverassistant.recognizer.ImageData
import io.github.driverassistant.recognizer.Recognizer
import io.github.driverassistant.util.state.Action

sealed class MainScreenActivityAction : Action()

class ActivityResumedAction(
    val cameraTextureView: TextureView,
    val surfaceTextureListener: SurfaceTextureListener
) : MainScreenActivityAction()

object ActivityPausedAction : MainScreenActivityAction()

class SurfaceTextureAvailableAction(
    val width: Int,
    val height: Int,
    val onImageAvailableListener: ImageReader.OnImageAvailableListener,
    val activity: Activity,
    val cameraDeviceStateCallback: CameraDevice.StateCallback
) : MainScreenActivityAction()

class CameraOpenedAction(
    val cameraDevice: CameraDevice,
    val cameraTextureView: TextureView,
    val previewCaptureSessionStateCallback: CameraCaptureSession.StateCallback
) : MainScreenActivityAction()

class CameraClosedAction(
    val cameraDevice: CameraDevice,
    val errorCode: Int? = null
) : MainScreenActivityAction()

class RecognizerImageButtonClickedAction(
    val fps: Double,
    val recognizersRunnerListener: RecognizersRunner.Companion.RecognizersRunnerListener,
    val recognizers: Iterable<Recognizer>
) : MainScreenActivityAction()

class RecordSwitchAction(
    val activity: Activity,
    val videoFolderName: String,
    val videoImageButton: ImageButton,
    val recordingCaptureSessionStateCallback: CameraCaptureSession.StateCallback,
    val previewCaptureSessionStateCallback: CameraCaptureSession.StateCallback,
    val chronometer: Chronometer,
    val cameraTextureView: TextureView
) : MainScreenActivityAction()

class PreviewCaptureSessionConfiguredAction(
    val previewCaptureSession: CameraCaptureSession
) : MainScreenActivityAction()

class RecordingCaptureSessionConfiguredAction(
    val recordingCaptureSession: CameraCaptureSession
) : MainScreenActivityAction()

class ImageShotAction(
    val imageData: ImageData
) : MainScreenActivityAction()
