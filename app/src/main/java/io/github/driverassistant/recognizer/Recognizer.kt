package io.github.driverassistant.recognizer

interface Recognizer {
    fun recognize(image: LatestImage): List<PaintableOnCanvas>
}
