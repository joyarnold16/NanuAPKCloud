package com.example.llama

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest


data class RecommendedImageModel(
    val name: String,
    val fileName: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val pageUrl: String,
    val sha256: String,
    val licenseLabel: String
)

object ImageModelCatalog {
    val starter = RecommendedImageModel(
        name = "Stable Diffusion 1.5 Q4_0",
        fileName = "stable-diffusion-v1-5-Q4_0.gguf",
        sizeLabel = "~1.75 GB",
        sizeBytes = 1_750_000_000L,
        downloadUrl = "https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-Q4_0.gguf?download=true",
        pageUrl = "https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF/blob/main/stable-diffusion-v1-5-Q4_0.gguf",
        sha256 = "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b",
        licenseLabel = "CreativeML Open RAIL-M"
    )
}

data class ImageDownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
)

class ImageModelManager(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val downloadDirectory: File
        get() {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            return File(root, "image-models").also { if (!it.exists()) it.mkdirs() }
        }

    fun destinationFile(model: RecommendedImageModel = ImageModelCatalog.starter): File = File(downloadDirectory, model.fileName)

    fun enqueue(model: RecommendedImageModel = ImageModelCatalog.starter): Long {
        val target = destinationFile(model)
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(android.net.Uri.parse(model.downloadUrl))
            .setTitle("Nanu Create • ${model.name}")
            .setDescription("Downloading local image model")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "image-models/${model.fileName}"
            )
        return manager.enqueue(request)
    }

    fun cancel(id: Long) {
        manager.remove(id)
    }

    fun query(id: Long): ImageDownloadSnapshot? {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) return null
            return ImageDownloadSnapshot(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
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

    fun verifySha256(file: File, expected: String): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expected, ignoreCase = true)
        }.getOrDefault(false)
    }
}
