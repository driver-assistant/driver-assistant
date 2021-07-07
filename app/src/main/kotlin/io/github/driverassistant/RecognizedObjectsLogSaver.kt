package io.github.driverassistant

import io.github.driverassistant.recognizer.RecognizedObject
import io.github.driverassistant.recognizer.Recognizer
import java.io.File
import java.io.FileOutputStream

class RecognizedObjectsLogSaver(LogFileGetter: () -> File) {
    private val file by lazy { LogFileGetter() }

    private lateinit var fileOutputStream: FileOutputStream

    private var fileExists: Boolean = false

    private fun writeText(text: String) {
        fileOutputStream.write(text.toByteArray())
    }

    fun onBegin() {
        println("Log file: ${file.absolutePath}")

        if (file.exists()) {
            file.delete()
        }

        fileExists = file.createNewFile()

        if (fileExists) {
            fileOutputStream = FileOutputStream(file, true)

            writeText("{")
        } else {
            println("Can't create the log file")
        }
    }

    fun logEntry(recognizedObjects: Map<List<RecognizedObject>, Recognizer>) {
        if (fileExists) {
            writeText("${System.currentTimeMillis()}: [\n")

            recognizedObjects.forEach { (recognizedObjects, recognizer) ->
                val recognizerString = buildString {
                    append("{\n")
                    append("\"recognizer\": \"${normalizeName(recognizer)}\", \n")
                    append("\"objects\": [\n")
                    recognizedObjects.forEach {
                        append("\"${normalizeName(it)}\",\n")
                    }
                    append("]\n},\n")
                }

                writeText(recognizerString)
            }

            writeText("],\n")
        }
    }

    fun onEnd() {
        if (fileExists) {
            writeText("}")

            fileOutputStream.flush()
            fileOutputStream.close()
        }
    }

    private fun normalizeName(obj: Any): String {
        return obj.toString().replace("\"", "\\\"")
    }
}
