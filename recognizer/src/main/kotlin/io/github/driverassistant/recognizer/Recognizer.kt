package io.github.driverassistant.recognizer

interface Recognizer {
    fun recognize(imageData: ImageData): List<RecognizedObject>
}
