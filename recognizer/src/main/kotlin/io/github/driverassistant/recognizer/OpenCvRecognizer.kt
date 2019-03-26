package io.github.driverassistant.recognizer

import android.util.Log
import org.opencv.android.OpenCVLoader

abstract class OpenCvRecognizer : Recognizer {
    companion object {
        private const val TAG = "OpenCV"

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Unable to load OpenCV.")
            } else {
                Log.d(TAG, "OpenCV loaded.")
            }
        }
    }
}
