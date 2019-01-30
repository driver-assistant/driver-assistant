package io.github.driverassistant.util

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun ensureVideoFolder(name: String): File {
    val movieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val videoFolder = File(movieDir, name)

    if (!videoFolder.exists()) {
        videoFolder.mkdirs()
    }

    return videoFolder
}

fun createVideoFile(videoFolder: File): File {
    val timestamp = SimpleDateFormat("yyyy.MM.dd-HH.mm.ss", Locale.US).format(Date())
    val prefix = "session-$timestamp-"
    return File.createTempFile(prefix, ".mp4", videoFolder)
}
