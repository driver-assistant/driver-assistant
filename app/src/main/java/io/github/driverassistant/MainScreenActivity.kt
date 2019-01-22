package io.github.driverassistant

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.CameraDevice.*
import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER
import android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION
import android.hardware.camera2.CaptureResult.CONTROL_AF_STATE
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.MediaRecorder.OutputFormat.MPEG_4
import android.media.MediaRecorder.VideoEncoder.H264
import android.media.MediaRecorder.VideoSource.SURFACE
import android.os.*
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import io.github.driverassistant.recognizer.LatestImage
import io.github.driverassistant.recognizer.RandomRecognizer
import io.github.driverassistant.recognizer.Recognizer
import io.github.driverassistant.util.LoopWithDelay
import io.github.driverassistant.util.ThreadAndHandler
import io.github.driverassistant.util.createVideoFile
import io.github.driverassistant.util.ensureVideoFolder
import kotlinx.android.synthetic.main.activity_main_screen.*
import java.io.File
import java.util.*
import kotlin.math.roundToLong
import kotlin.math.sign

class MainScreenActivity : AppCompatActivity() {

    var latestImage: LatestImage? = null

    private lateinit var recognizersRunner: LoopWithDelay

    private val recognizers: List<Recognizer> = listOf(RandomRecognizer())  // TODO: Add normal recognizers here

    private var recognizerThreadAndHandler: ThreadAndHandler? = null

    private var captureState = State.PREVIEW

    private val surfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            setupCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false
    }

    private var cameraDevice: CameraDevice? = null

    private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera

            if (isRecording) {
                videoFilePath = createVideoFile(videoFolder).absolutePath
                startRecording()
            } else {
                startPreview()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private lateinit var cameraId: String

    private lateinit var previewSize: Size

    private lateinit var videoSize: Size

    private lateinit var imageSize: Size

    private lateinit var imageReader: ImageReader

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { imageReader ->
        imageReader.acquireLatestImage().apply {
            val buffer = planes[0].buffer

            val latestImageBytes = ByteArray(buffer.remaining())
            buffer.get(latestImageBytes)

            latestImage = LatestImage(latestImageBytes, width, height)

            println("Image is shoot: $width x $height")

            close()
        }
    }

    private var totalRotation = 0

    private var captureThreadAndHandler: ThreadAndHandler? = null

    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    private var isRecording = false

    private lateinit var videoFolder: File

    private lateinit var videoFilePath: String

    private val mediaRecorder = MediaRecorder()

    private lateinit var previewCaptureSession: CameraCaptureSession

    private val previewCaptureCallback = object : CaptureCallback() {
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
                    requestToStartStillCapture()
                }
            }
        }
    }

    private lateinit var recordCaptureSession: CameraCaptureSession

    private val recordCaptureCallback = object : CaptureCallback() {
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
                    requestToStartStillCapture()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_screen)

        videoFolder = ensureVideoFolder(FOLDER_NAME)

        recognizerImageButton.setOnClickListener {
            val recognizer = recognizerThreadAndHandler

            if (recognizer == null) {
                startRecognizerThread(FPS)
            } else {
                stopRecognizerThread()
            }
        }

        videoImageButton.setOnClickListener {
            if (isRecording) {
                isRecording = false

                videoImageButton.setImageResource(R.mipmap.btn_video_online)

                mediaRecorder.stop()
                mediaRecorder.reset()
                startPreview()

                chronometer.stop()
                chronometer.visibility = INVISIBLE
            } else {
                checkWriteStoragePermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        startCaptureThread()

        if (cameraTextureView.isAvailable) {
            setupCamera(cameraTextureView.width, cameraTextureView.height)
        } else {
            cameraTextureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        cameraDevice!!.close()
        cameraDevice = null

        stopCaptureThread()

        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            /* Auto hide status and navigation bars: */
            window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (RequestCode.values()[requestCode]) {
            RequestCode.CAMERA -> {
                if (grantResults[0] != PERMISSION_GRANTED) {
                    shortToast(R.string.no_camera_permission)
                }
            }

            RequestCode.WRITE_EXTERNAL_STORAGE -> {
                if (grantResults[0] != PERMISSION_GRANTED) {
                    shortToast(R.string.no_write_external_storage_permission)
                } else {
                    startRecording()
                }
            }
        }
    }

    private fun setupCamera(width: Int, height: Int) {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        cameraId = cameraManager.findBackCameraId()
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

        val deviceRotation = windowManager.defaultDisplay.rotation  // TODO: research video rotation (#3)
        totalRotation = sensorToDeviceRotation(
            cameraCharacteristics,
            deviceRotation
        )

        val swapMetrics = totalRotation == 90 || totalRotation == 270

        val (rotatedWidth, rotatedHeight) = when (swapMetrics) {
            false -> width to height
            true -> height to width
        }

        previewSize = cameraCharacteristics[SCALER_STREAM_CONFIGURATION_MAP]!!
            .getOutputSizes(SurfaceTexture::class.java)
            .chooseOptimalSize(rotatedWidth, rotatedHeight)

        videoSize = cameraCharacteristics[SCALER_STREAM_CONFIGURATION_MAP]!!
            .getOutputSizes(MediaRecorder::class.java)
            .chooseOptimalSize(rotatedWidth, rotatedHeight)

        imageSize = cameraCharacteristics[SCALER_STREAM_CONFIGURATION_MAP]!!
            .getOutputSizes(ImageFormat.JPEG)
            .chooseOptimalSize(rotatedWidth, rotatedHeight)

        imageReader = ImageReader.newInstance(imageSize.width, imageSize.height, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener(onImageAvailableListener, captureThreadAndHandler!!.handler)
        }

        fun connectCamera() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED) {
                    cameraManager.openCamera(cameraId, cameraDeviceStateCallback, captureThreadAndHandler!!.handler)
                } else {
                    if (shouldShowRequestPermissionRationale(CAMERA)) {
                        shortToast(R.string.no_camera_permission)
                    }

                    requestPermissions(arrayOf(CAMERA), RequestCode.CAMERA.ordinal)
                }
            } else {
                cameraManager.openCamera(cameraId, cameraDeviceStateCallback, captureThreadAndHandler!!.handler)
            }
        }

        connectCamera()
    }

    private fun startPreview() {
        val surfaceTexture = cameraTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
        }

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                previewCaptureSession = session.apply {
                    setRepeatingRequest(captureRequestBuilder.build(), null, captureThreadAndHandler!!.handler)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                shortToast(R.string.unable_to_start_preview)
            }
        }

        cameraDevice!!.createCaptureSession(listOf(previewSurface, imageReader.surface), stateCallback, null)
    }

    private fun requestToStartStillCapture() {
        captureRequestBuilder = if (isRecording) {
            cameraDevice!!.createCaptureRequest(TEMPLATE_VIDEO_SNAPSHOT)
        } else {
            cameraDevice!!.createCaptureRequest(TEMPLATE_STILL_CAPTURE)
        }

        captureRequestBuilder.apply {
            addTarget(imageReader.surface)
            this[JPEG_ORIENTATION] = totalRotation
        }

        if (isRecording) {
            recordCaptureSession.capture(captureRequestBuilder.build(), null, null)
        } else {
            previewCaptureSession.capture(captureRequestBuilder.build(), null, null)
        }
    }

    private fun startCaptureThread() {
        val thread = HandlerThread("Driver Assistant Capture Thread")
        thread.start()
        val handler = Handler(thread.looper)

        captureThreadAndHandler = ThreadAndHandler(thread, handler)
    }

    private fun stopCaptureThread() {
        captureThreadAndHandler!!.thread.apply {
            quitSafely()
            join()
        }

        captureThreadAndHandler = null
    }

    private fun startRecognizerThread(fps: Double) {
        val thread = HandlerThread("Driver Assistant Recognizer Thread")
        thread.start()
        val handler = Handler(thread.looper)

        recognizersRunner = object : LoopWithDelay(handler, (1000 / fps).roundToLong()) {
            override fun iterate() {
                lockFocusToTakeShot()
                val latestImage = waitForImage()

                val statsText = with(latestImage) { "${bytes.size} bytes, $wight x $height" }
                statsTextView.post { statsTextView.text = statsText }

                val paintables = recognizers
                    .flatMap { recognizer -> recognizer.recognize(latestImage) }
                    .flatMap { recognizedObject -> recognizedObject.elements }
                    .map { recognizedObjectElement -> recognizedObjectElement.toPaintableOnCanvas() }

                recognizedObjectsView.paintables = paintables
                recognizedObjectsView.invalidate()
            }

            private fun waitForImage(): LatestImage {
                val activity = this@MainScreenActivity

                while (true) {
                    val latestImage = activity.latestImage

                    if (latestImage != null) {
                        activity.latestImage = null

                        return latestImage
                    }
                }
            }
        }

        handler.post(recognizersRunner)

        recognizerThreadAndHandler = ThreadAndHandler(thread, handler)
    }

    private fun stopRecognizerThread() {
        recognizerThreadAndHandler!!.apply {
            handler.removeCallbacks(recognizersRunner)

            thread.quitSafely()
            thread.join()
        }

        recognizedObjectsView.paintables = emptyList()
        recognizedObjectsView.invalidate()
        statsTextView.text = ""

        recognizerThreadAndHandler = null
    }

    private fun startRecording() {
        isRecording = true
        videoImageButton.setImageResource(R.mipmap.btn_video_busy)

        videoFilePath = createVideoFile(videoFolder).absolutePath

        mediaRecorder.init()

        val surfaceTexture = cameraTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(surfaceTexture)
        val recordSurface = mediaRecorder.surface

        captureRequestBuilder = cameraDevice!!.createCaptureRequest(TEMPLATE_RECORD).apply {
            addTarget(previewSurface)
            addTarget(recordSurface)
        }

        cameraDevice!!.createCaptureSession(
            listOf(previewSurface, recordSurface, imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    recordCaptureSession = session.apply {
                        setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    shortToast(R.string.unable_to_start_recording)
                }
            },
            null
        )

        mediaRecorder.start()

        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.visibility = VISIBLE
        chronometer.start()
    }

    private fun checkWriteStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
                startRecording()
            } else {
                if (shouldShowRequestPermissionRationale(WRITE_EXTERNAL_STORAGE)) {
                    shortToast(R.string.no_write_external_storage_permission)
                }
                requestPermissions(
                    arrayOf(WRITE_EXTERNAL_STORAGE),
                    RequestCode.WRITE_EXTERNAL_STORAGE.ordinal
                )
            }
        } else {
            startRecording()
        }
    }

    fun lockFocusToTakeShot() {
        captureState = State.WAIT_LOCK
        captureRequestBuilder[CONTROL_AF_TRIGGER] = CONTROL_AF_TRIGGER_START

        if (isRecording) {
            recordCaptureSession.capture(
                captureRequestBuilder.build(),
                recordCaptureCallback,
                captureThreadAndHandler!!.handler
            )
        } else {
            previewCaptureSession.capture(
                captureRequestBuilder.build(),
                previewCaptureCallback,
                captureThreadAndHandler!!.handler
            )
        }
    }

    private fun MediaRecorder.init() {
        setVideoSource(SURFACE)
        setOutputFormat(MPEG_4)
        setOutputFile(videoFilePath)
        setVideoEncodingBitRate(5_000_000)
        setVideoFrameRate(30)
        setVideoSize(videoSize.width, videoSize.height)
        setVideoEncoder(H264)
        setOrientationHint(totalRotation)
        prepare()
    }

    private fun shortToast(resId: Int) {
        Toast.makeText(applicationContext, resId, LENGTH_SHORT).show()
    }

    companion object {
        private const val FPS = 5.0

        private const val FOLDER_NAME = "driver-assistant/"

        private val Size.area get() = this.width.toLong() * this.height.toLong()

        private val sizeComparatorByArea = Comparator<Size> { lhs, rhs -> (lhs.area - rhs.area).sign }

        private fun Int.toOrientation() = when (this) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> throw IllegalArgumentException("$this")
        }

        private fun sensorToDeviceRotation(cameraCharacteristics: CameraCharacteristics, deviceRotation: Int): Int {
            val sensorOrientation = cameraCharacteristics[SENSOR_ORIENTATION]!!
            val deviceOrientation = deviceRotation.toOrientation()

            return (sensorOrientation + deviceOrientation + 360) % 360
        }

        private fun CameraManager.findBackCameraId() = this
            .cameraIdList
            .first { this.cameraFacing(it) == LENS_FACING_BACK }

        private fun CameraManager.cameraFacing(cameraId: String) = this
            .getCameraCharacteristics(cameraId)[LENS_FACING]

        private fun Array<Size>.chooseOptimalSize(width: Int, height: Int) = this
            .filter { it.width >= width && it.height >= height }
            .minWith(sizeComparatorByArea)
            ?: this.first()

        enum class RequestCode {
            CAMERA,
            WRITE_EXTERNAL_STORAGE;
        }

        enum class State {
            PREVIEW,
            WAIT_LOCK;
        }
    }
}
