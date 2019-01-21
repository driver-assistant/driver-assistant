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
import android.os.Environment.DIRECTORY_MOVIES
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import io.github.driverassistant.recognizer.*
import kotlinx.android.synthetic.main.activity_main_screen.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sign

class MainScreenActivity : AppCompatActivity() {

    var latestImage: LatestImage? = null

    val statsText: TextView by lazy { statsTextView }

    val recognizedObjects: RecognizedObjectsView by lazy { recognizedObjectsView }

    private val mRecognizers: List<Recognizer> = listOf(RandomRecognizer())  // TODO: Add normal recognizers here

    private var mRecognizersRunnerThreadChain: RecognizersRunnerThreadChain? = null

    private var mCaptureState = State.PREVIEW

    private val mSurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            setupCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false
    }

    private var mCameraDevice: CameraDevice? = null

    private val mCameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera

            if (mIsRecording) {
                createVideoFile()
                startRecording()
            } else {
                startPreview()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            mCameraDevice = null
        }
    }

    private var mCameraId: String? = null

    private var mPreviewSize: Size? = null

    private var mVideoSize: Size? = null

    private var mImageSize: Size? = null

    private var mImageReader: ImageReader? = null

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { imageReader ->
        imageReader.acquireLatestImage().apply {
            val buffer = planes[0].buffer

            val latestImageBytes = ByteArray(buffer.remaining())
            buffer.get(latestImageBytes)

            val latestImageSize = width to height

            latestImage = LatestImage(latestImageBytes, latestImageSize)

            println("Image is shoot: $latestImageSize")

            close()
        }
    }

    private var mTotalRotation: Int? = null

    private var mCaptureThread: HandlerThread? = null

    private var mCaptureThreadHandler: Handler? = null

    private var mCaptureRequestBuilder: CaptureRequest.Builder? = null

    private var mIsRecording = false

    private var mVideoFolder: File? = null

    private var mVideoFilePath: String? = null

    private val mMediaRecorder = MediaRecorder()

    private var mPreviewCaptureSession: CameraCaptureSession? = null

    private val mPreviewCaptureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)

            if (mCaptureState == State.WAIT_LOCK) {
                mCaptureState = State.PREVIEW

                val afState = result[CONTROL_AF_STATE]
                if (afState == CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                    requestToStartStillCapture()
                }
            }
        }
    }

    private var mRecordCaptureSession: CameraCaptureSession? = null

    private val mRecordCaptureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)

            if (mCaptureState == State.WAIT_LOCK) {
                mCaptureState = State.PREVIEW

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

        createVideoFolder()

        recognizerImageButton.setOnClickListener {
            val chain = mRecognizersRunnerThreadChain

            if (chain == null) {
                mRecognizersRunnerThreadChain =
                        startRecognizersRunnerThreadChain(this, 5.0, mRecognizers)
            } else {
                chain.stop()
                mRecognizersRunnerThreadChain = null

                statsTextView.text = ""
            }
        }

        videoImageButton.setOnClickListener {
            if (mIsRecording) {
                mIsRecording = false

                videoImageButton.setImageResource(R.mipmap.btn_video_online)

                mMediaRecorder.stop()
                mMediaRecorder.reset()
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

        startBackgroundThread()

        if (cameraTextureView.isAvailable) {
            setupCamera(cameraTextureView.width, cameraTextureView.height)
        } else {
            cameraTextureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        mCameraDevice!!.close()
        mCameraDevice = null

        stopBackgroundThread()

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
                    shortToast("Can't run without camera permission")
                }
            }

            RequestCode.WRITE_EXTERNAL_STORAGE -> {
                if (grantResults[0] != PERMISSION_GRANTED) {
                    shortToast("Can't run without an ability of saving video")
                } else {
                    startRecording()
                }
            }
        }
    }

    private fun setupCamera(width: Int, height: Int) {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        mCameraId = cameraManager.findBackCameraId()
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId)

        val deviceRotation = windowManager.defaultDisplay.rotation  // TODO: research video rotation (#3)
        mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceRotation)

        val swapMetrics = mTotalRotation!! == 90 || mTotalRotation!! == 270

        val (rotatedWidth, rotatedHeight) = when (swapMetrics) {
            false -> width to height
            true -> height to width
        }

        mPreviewSize = cameraCharacteristics[SCALER_STREAM_CONFIGURATION_MAP]
            .getOutputSizes(SurfaceTexture::class.java)
            .chooseOptimalSize(rotatedWidth, rotatedHeight)

        mVideoSize = cameraCharacteristics[SCALER_STREAM_CONFIGURATION_MAP]
            .getOutputSizes(MediaRecorder::class.java)
            .chooseOptimalSize(rotatedWidth, rotatedHeight)

        mImageSize = cameraCharacteristics[SCALER_STREAM_CONFIGURATION_MAP]
            .getOutputSizes(ImageFormat.JPEG)
            .chooseOptimalSize(rotatedWidth, rotatedHeight)

        mImageReader = ImageReader.newInstance(mImageSize!!.width, mImageSize!!.height, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener(mOnImageAvailableListener, mCaptureThreadHandler)
        }

        fun connectCamera() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mCaptureThreadHandler)
                } else {
                    if (shouldShowRequestPermissionRationale(CAMERA)) {
                        shortToast("Video app requires access to camera")
                    }

                    requestPermissions(arrayOf(CAMERA), RequestCode.CAMERA.ordinal)
                }
            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mCaptureThreadHandler)
            }
        }

        connectCamera()
    }

    private fun startPreview() {
        val surfaceTexture = cameraTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
        val previewSurface = Surface(surfaceTexture)

        mCaptureRequestBuilder = mCameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
        }

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                mPreviewCaptureSession = session.apply {
                    setRepeatingRequest(mCaptureRequestBuilder!!.build(), null, mCaptureThreadHandler)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                shortToast("Unable to setup camera preview")
            }
        }

        mCameraDevice!!.createCaptureSession(listOf(previewSurface, mImageReader!!.surface), stateCallback, null)
    }

    private fun requestToStartStillCapture() {
        mCaptureRequestBuilder = if (mIsRecording) {
            mCameraDevice!!.createCaptureRequest(TEMPLATE_VIDEO_SNAPSHOT)
        } else {
            mCameraDevice!!.createCaptureRequest(TEMPLATE_STILL_CAPTURE)
        }

        mCaptureRequestBuilder!!.apply {
            addTarget(mImageReader!!.surface)
            this[JPEG_ORIENTATION] = mTotalRotation
        }

        if (mIsRecording) {
            mRecordCaptureSession!!.capture(mCaptureRequestBuilder!!.build(), null, null)
        } else {
            mPreviewCaptureSession!!.capture(mCaptureRequestBuilder!!.build(), null, null)
        }
    }

    private fun startBackgroundThread() {
        with(HandlerThread("Driver Assistant Capture Thread")) {
            this.start()
            mCaptureThread = this
            mCaptureThreadHandler = Handler(this.looper)
        }
    }

    private fun stopBackgroundThread() {
        mCaptureThread!!.quitSafely()
        mCaptureThread!!.join()

        mCaptureThread = null
        mCaptureThreadHandler = null
    }

    private fun createVideoFolder() {
        val movieDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES)
        mVideoFolder = File(movieDir, "driver-assistant/")

        if (!mVideoFolder!!.exists()) {
            mVideoFolder!!.mkdirs()
        }
    }

    private fun createVideoFile() {
        val timestamp = SimpleDateFormat("yyyy.MM.dd-HH.mm.ss", Locale.US).format(Date())
        val prefix = "session-$timestamp-"
        val videoFile = File.createTempFile(prefix, ".mp4", mVideoFolder!!)
        mVideoFilePath = videoFile.absolutePath
    }

    private fun startRecording() {
        mIsRecording = true
        videoImageButton.setImageResource(R.mipmap.btn_video_busy)

        createVideoFile()

        mMediaRecorder.init()

        val surfaceTexture = cameraTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
        val previewSurface = Surface(surfaceTexture)
        val recordSurface = mMediaRecorder.surface

        mCaptureRequestBuilder = mCameraDevice!!.createCaptureRequest(TEMPLATE_RECORD).apply {
            addTarget(previewSurface)
            addTarget(recordSurface)
        }

        mCameraDevice!!.createCaptureSession(
            listOf(previewSurface, recordSurface, mImageReader!!.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mRecordCaptureSession = session.apply {
                        setRepeatingRequest(mCaptureRequestBuilder!!.build(), null, null)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    shortToast("Unable to setup camera record")
                }
            },
            null
        )

        mMediaRecorder.start()

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
                    shortToast("App needs the permission to save video")
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
        mCaptureState = State.WAIT_LOCK
        mCaptureRequestBuilder!![CONTROL_AF_TRIGGER] = CONTROL_AF_TRIGGER_START

        if (mIsRecording) {
            mRecordCaptureSession!!.capture(
                mCaptureRequestBuilder!!.build(),
                mRecordCaptureCallback,
                mCaptureThreadHandler
            )
        } else {
            mPreviewCaptureSession!!.capture(
                mCaptureRequestBuilder!!.build(),
                mPreviewCaptureCallback,
                mCaptureThreadHandler
            )
        }
    }

    private fun MediaRecorder.init() {
        setVideoSource(SURFACE)
        setOutputFormat(MPEG_4)
        setOutputFile(mVideoFilePath)
        setVideoEncodingBitRate(5_000_000)
        setVideoFrameRate(30)
        setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        setVideoEncoder(H264)
        setOrientationHint(mTotalRotation!!)
        prepare()
    }

    private fun shortToast(message: String) {
        Toast.makeText(applicationContext, message, LENGTH_SHORT).show()
    }

    companion object {

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
            val sensorOrientation = cameraCharacteristics[SENSOR_ORIENTATION]
            val deviceOrientation = deviceRotation.toOrientation()

            return (sensorOrientation + deviceOrientation + 360) % 360
        }

        private fun CameraManager.findBackCameraId() = this
            .cameraIdList
            .firstOrNull { this.cameraFacing(it) == LENS_FACING_BACK }

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