package io.github.driverassistant.recognizer

import android.os.Environment.DIRECTORY_DCIM
import android.os.Environment.getExternalStoragePublicDirectory
import android.support.annotation.ColorInt
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc.*
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs

class LinesRecognizer : OpenCvRecognizer() {
    override fun recognize(imageData: ImageData): List<RecognizedObject> {
        val filename = "$dir/last.jpg"

        FileOutputStream(filename).apply {
            write(imageData.bytes)
            close()
        }

        val image = Imgcodecs.imread(filename)

        val canny = canny(image)
        val regionOfInterest = regionOfInterest(canny)

        val linesMatrix = Mat().also {
            HoughLinesP(
                regionOfInterest,
                it,
                2.0,
                PI / 180,
                100,
                40.0,
                5.0
            )
        }

        val lines = (0 until linesMatrix.height())
            .map { linesMatrix[it, 0] }
            .map {
                SimpleLine(
                    it[1],
                    it[0],
                    it[3],
                    it[2]
                )
            }

        return lines
            .filter {
                val dx = it.x1 - it.x0
                val dy = it.y1 - it.y0

                if (dx == 0.0) {
                    return@filter false
                }

                val k = dy / dx

                abs(k) <= 2
            }
            .map {
                with(it) {
                    GreenLine(
                        x0.toFloat() / imageData.width,
                        y0.toFloat() / imageData.height,
                        x1.toFloat() / imageData.width,
                        y1.toFloat() / imageData.height
                    )
                }
            }
    }

    companion object {
        private val dir: String by lazy { getExternalStoragePublicDirectory(DIRECTORY_DCIM).absolutePath }

        fun canny(image: Mat): Mat {
            val gray = Mat().also { cvtColor(image, it, COLOR_RGB2GRAY) }
            val blur = Mat().also { GaussianBlur(gray, it, Size(5.0, 5.0), 0.0) }
            return Mat().also { Canny(blur, it, 50.0, 150.0) }
        }

        fun regionOfInterest(image: Mat): Mat {
            val width = image.width().toDouble()
            val height = image.height().toDouble()

            val triangleVertices = MatOfPoint(
                Point(0.0, height),
                Point(width, height),
                Point(width / 2.0, height / 3.0)
            )

            val mask = Mat(Size(width, height), CvType.CV_8UC1, Scalar(0.0)).also {
                fillPoly(it, listOf(triangleVertices), Scalar(255.0))
            }

            return Mat().also { Core.bitwise_and(image, mask, it) }
        }
    }
}

data class SimpleLine(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double
)

data class GreenLine(
    private val x0: Float,
    private val y0: Float,
    private val x1: Float,
    private val y1: Float
) : RecognizedObject() {

    override val elements: List<RecognizedObjectElement> = listOf(
        Line(x0, y0, x1, y1, GREEN_COLOR, THICKNESS)
    )

    companion object {
        private const val THICKNESS = 10.0f

        @ColorInt
        private const val GREEN_COLOR = 0xFF00CC00.toInt()
    }
}
