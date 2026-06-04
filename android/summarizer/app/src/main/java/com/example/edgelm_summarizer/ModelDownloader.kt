package com.example.edgelm_summarizer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    const val MODEL_FILENAME     = "Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte"
    const val TOKENIZER_FILENAME = "tokenizer.model"

    private const val MODEL_URL =
        "https://huggingface.co/executorch-community/" +
                "Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8-ET/resolve/main/" +
                "Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte"

    private const val TOKENIZER_URL =
        "https://huggingface.co/executorch-community/" +
                "Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8-ET/resolve/main/" +
                "tokenizer.model"

    fun modelsExist(context: Context): Boolean {
        val modelFile     = File(context.filesDir, MODEL_FILENAME)
        val tokenizerFile = File(context.filesDir, TOKENIZER_FILENAME)
        return modelFile.exists() && tokenizerFile.exists()
    }

    suspend fun downloadAll(
        context: Context,
        onProgress: (fileName: String, percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            downloadFile(
                url      = TOKENIZER_URL,
                dest     = File(context.filesDir, TOKENIZER_FILENAME),
                onProgress = { percent -> onProgress(TOKENIZER_FILENAME, percent) }
            )
            downloadFile(
                url      = MODEL_URL,
                dest     = File(context.filesDir, MODEL_FILENAME),
                onProgress = { percent -> onProgress(MODEL_FILENAME, percent) }
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("ModelDownloader", "Download failed: ${e.message}", e)
            false
        }
    }

    private fun downloadFile(
        url: String,
        dest: File,
        onProgress: (Int) -> Unit
    ) {
        // Skip if already downloaded
        if (dest.exists()) {
            onProgress(100)
            return
        }

        val tempFile = File(dest.parent, "${dest.name}.tmp")

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout    = 30_000
        connection.connect()

        val contentLength = connection.contentLengthLong
        var downloaded    = 0L

        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytes  = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (contentLength > 0) {
                        val percent = (downloaded * 100 / contentLength).toInt()
                        onProgress(percent)
                    }
                    bytes = input.read(buffer)
                }
            }
        }

        // Rename temp file to final destination only after complete download
        tempFile.renameTo(dest)
        onProgress(100)
    }
}