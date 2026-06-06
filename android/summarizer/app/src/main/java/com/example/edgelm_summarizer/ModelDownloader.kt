package com.example.edgelm_summarizer

import androidx.core.content.edit
import androidx.core.net.toUri
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

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

    private const val PREFS_NAME         = "edgelm_prefs"
    private const val KEY_TOKENIZER_ID   = "tokenizer_download_id"
    private const val KEY_MODEL_ID       = "model_download_id"

    // ── Persistence ───────────────────────────────────────────────────────

    fun saveDownloadIds(context: Context, tokenizerId: Long, modelId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_TOKENIZER_ID, tokenizerId)
            putLong(KEY_MODEL_ID, modelId)
        }
    }

    fun loadDownloadIds(context: Context): Pair<Long, Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Pair(
            prefs.getLong(KEY_TOKENIZER_ID, -1L),
            prefs.getLong(KEY_MODEL_ID, -1L)
        )
    }

    fun clearDownloadIds(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_TOKENIZER_ID)
            remove(KEY_MODEL_ID)
        }
    }

    // ── Existence checks ──────────────────────────────────────────────────

    fun modelsExist(context: Context): Boolean {
        return modelExists(context) && tokenizerExists(context)
    }

    fun modelExists(context: Context): Boolean =
        File(context.filesDir, MODEL_FILENAME).exists()

    fun tokenizerExists(context: Context): Boolean =
        File(context.filesDir, TOKENIZER_FILENAME).exists()

    // ── Download ──────────────────────────────────────────────────────────

    fun enqueueDownloads(context: Context): Pair<Long, Long> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Only enqueue tokenizer if not already downloaded
        val tokenizerId = if (!tokenizerExists(context)) {
            val request = DownloadManager.Request(TOKENIZER_URL.toUri())
                .setTitle("EdgeLM — tokenizer")
                .setDescription("Downloading tokenizer.model")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    TOKENIZER_FILENAME
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            dm.enqueue(request)
        } else {
            -1L  // Already exists, skip
        }

        // Only enqueue model if not already downloaded
        val modelId = if (!modelExists(context)) {
            val request = DownloadManager.Request(MODEL_URL.toUri())
                .setTitle("EdgeLM — model")
                .setDescription("Downloading Llama 3.2 1B (~1.1 GB)")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    MODEL_FILENAME
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            dm.enqueue(request)
        } else {
            -1L  // Already exists, skip
        }

        return Pair(tokenizerId, modelId)
    }

    // ── Progress ──────────────────────────────────────────────────────────

    fun getProgress(context: Context, downloadId: Long): Int {
        if (downloadId == -1L) return 100
        val dm     = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query  = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)

        if (cursor.moveToFirst()) {
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            cursor.close()
            return if (total > 0) (downloaded * 100 / total).toInt() else 0
        }
        cursor.close()
        return 0
    }

    fun isDownloadComplete(context: Context, downloadId: Long): Boolean {
        if (downloadId == -1L) return true  // Was already on disk, treat as done
        val dm     = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query  = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)

        if (cursor.moveToFirst()) {
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            cursor.close()
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
        cursor.close()
        return false
    }

    fun isDownloadFailed(context: Context, downloadId: Long): Boolean {
        if (downloadId == -1L) return false
        val dm     = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query  = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)

        if (cursor.moveToFirst()) {
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            cursor.close()
            return status == DownloadManager.STATUS_FAILED
        }
        cursor.close()
        return false
    }

    // ── Copy from Downloads to filesDir ───────────────────────────────────

    fun copyFromDownloadsToFilesDir(context: Context) {
        listOf(MODEL_FILENAME, TOKENIZER_FILENAME).forEach { filename ->
            val src  = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                filename
            )
            val dest = File(context.filesDir, filename)
            if (src.exists() && !dest.exists()) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
        }
    }
}