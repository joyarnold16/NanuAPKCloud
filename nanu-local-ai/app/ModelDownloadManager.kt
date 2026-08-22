package com.example.llama

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream

data class ModelDownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val isFinished: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED
}

class ModelDownloadManager(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val downloadDirectory: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models").also {
            if (!it.exists()) it.mkdirs()
        }

    fun destinationFile(model: RecommendedModel): File = File(downloadDirectory, model.fileName)

    fun enqueue(model: RecommendedModel): Long {
        val target = destinationFile(model)
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("Nanu Local AI • ${model.name}")
            .setDescription("Downloading ${model.fileName}")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "models/${model.fileName}"
            )

        return manager.enqueue(request)
    }

    fun cancel(downloadId: Long) {
        manager.remove(downloadId)
    }

    fun query(downloadId: Long): ModelDownloadSnapshot? {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return ModelDownloadSnapshot(status, downloaded, total, reason)
        }
    }

    fun looksLikeGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF"
            }
        }.getOrDefault(false)
    }
}
