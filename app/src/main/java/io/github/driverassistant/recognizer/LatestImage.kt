package io.github.driverassistant.recognizer

// TODO: choose unified image format (suitable for all recognizers)
data class LatestImage(
    val bytes: ByteArray,
    val size: Pair<Int, Int>
)
