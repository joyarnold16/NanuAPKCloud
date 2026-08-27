package com.example.llama

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max


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
        // Use Hugging Face's documented resolve URL. The downloader below
        // follows the storage/CDN redirects itself and can resume with Range.
        downloadUrl = "https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-Q4_0.gguf",
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

/**
 * Resumable in-app downloader for the image model.
 *
 * RC8 originally delegated this 1.75 GB Hugging Face/Xet-backed file to
 * Android DownloadManager. On some Android/network combinations the signed
 * redirected storage URL can stall or fail. This implementation keeps the
 * existing UI contract (DownloadManager-style status constants), but performs
 * the HTTP transfer itself so Nanu can:
 *  - follow Hugging Face/CDN redirects explicitly;
 *  - resume from a .part file with HTTP Range;
 *  - retry transient failures from the original resolve URL;
 *  - resume after the app process is recreated when the screen queries status.
 */
class ImageModelManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val state = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val downloadDirectory: File
        get() {
            val root = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
            return File(root, "image-models").also { if (!it.exists()) it.mkdirs() }
        }

    fun destinationFile(model: RecommendedImageModel = ImageModelCatalog.starter): File =
        File(downloadDirectory, model.fileName)

    private fun partialFile(model: RecommendedImageModel = ImageModelCatalog.starter): File =
        File(downloadDirectory, "${model.fileName}.part")

    fun enqueue(model: RecommendedImageModel = ImageModelCatalog.starter): Long {
        val target = destinationFile(model)
        if (target.exists()) target.delete()

        val id = System.currentTimeMillis().coerceAtLeast(1L)
        state.edit()
            .putLong(KEY_ID, id)
            .putInt(KEY_STATUS, DownloadManager.STATUS_PENDING)
            .putLong(KEY_DOWNLOADED, partialFile(model).takeIf { it.exists() }?.length() ?: 0L)
            .putLong(KEY_TOTAL, model.sizeBytes)
            .putInt(KEY_REASON, 0)
            .putBoolean(KEY_CANCELLED, false)
            .apply()
        startWorker(id, model)
        return id
    }

    fun cancel(id: Long) {
        if (state.getLong(KEY_ID, -1L) != id) return
        state.edit()
            .putBoolean(KEY_CANCELLED, true)
            .putInt(KEY_STATUS, DownloadManager.STATUS_FAILED)
            .putInt(KEY_REASON, DownloadManager.ERROR_UNKNOWN)
            .apply()
        workers.remove(id)?.interrupt()
        partialFile().delete()
    }

    fun query(id: Long): ImageDownloadSnapshot? {
        if (state.getLong(KEY_ID, -1L) != id) return null
        val status = state.getInt(KEY_STATUS, DownloadManager.STATUS_PENDING)
        if (status in setOf(
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED
            ) && workers[id]?.isAlive != true
        ) {
            // Activity/process recreation: resume from the .part file.
            startWorker(id, ImageModelCatalog.starter)
        }
        return ImageDownloadSnapshot(
            status = state.getInt(KEY_STATUS, status),
            downloadedBytes = state.getLong(KEY_DOWNLOADED, 0L),
            totalBytes = state.getLong(KEY_TOTAL, ImageModelCatalog.starter.sizeBytes),
            reason = state.getInt(KEY_REASON, 0)
        )
    }

    private fun startWorker(id: Long, model: RecommendedImageModel) {
        synchronized(workers) {
            if (workers[id]?.isAlive == true) return
            val thread = Thread({ runDownload(id, model) }, "nanu-image-model-$id").apply {
                isDaemon = true
            }
            workers[id] = thread
            thread.start()
        }
    }

    private fun runDownload(id: Long, model: RecommendedImageModel) {
        state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_RUNNING).putInt(KEY_REASON, 0).apply()
        var lastReason = DownloadManager.ERROR_HTTP_DATA_ERROR
        try {
            for (attempt in 1..MAX_ATTEMPTS) {
                if (isCancelled(id)) return
                try {
                    downloadAttempt(id, model)
                    val part = partialFile(model)
                    val target = destinationFile(model)
                    if (!part.exists() || part.length() <= 0L) error("Downloaded file is empty")
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) {
                        part.inputStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
                        }
                        part.delete()
                    }
                    state.edit()
                        .putLong(KEY_DOWNLOADED, target.length())
                        .putLong(KEY_TOTAL, target.length())
                        .putInt(KEY_STATUS, DownloadManager.STATUS_SUCCESSFUL)
                        .putInt(KEY_REASON, 0)
                        .apply()
                    return
                } catch (e: HttpFailure) {
                    lastReason = if (e.code in 500..599 || e.code == 408 || e.code == 429) {
                        DownloadManager.ERROR_HTTP_DATA_ERROR
                    } else {
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE
                    }
                    if (attempt == MAX_ATTEMPTS || e.code in 400..499 && e.code !in setOf(408, 416, 429)) break
                    state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_PAUSED).putInt(KEY_REASON, lastReason).apply()
                    sleepBeforeRetry(attempt)
                    state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_RUNNING).apply()
                } catch (_: InterruptedException) {
                    if (!isCancelled(id)) {
                        lastReason = DownloadManager.ERROR_HTTP_DATA_ERROR
                    }
                    return
                } catch (_: Exception) {
                    lastReason = DownloadManager.ERROR_HTTP_DATA_ERROR
                    if (attempt == MAX_ATTEMPTS) break
                    state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_PAUSED).putInt(KEY_REASON, lastReason).apply()
                    sleepBeforeRetry(attempt)
                    state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_RUNNING).apply()
                }
            }
            if (!isCancelled(id)) {
                state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_FAILED).putInt(KEY_REASON, lastReason).apply()
            }
        } finally {
            workers.remove(id)
        }
    }

    private fun downloadAttempt(id: Long, model: RecommendedImageModel) {
        val part = partialFile(model)
        var existing = if (part.exists()) part.length() else 0L
        var currentUrl = model.downloadUrl
        var redirects = 0
        var connection: HttpURLConnection? = null

        while (true) {
            if (isCancelled(id)) throw InterruptedException("cancelled")
            connection?.disconnect()
            connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream,*/*")
                setRequestProperty("Accept-Encoding", "identity")
                if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw HttpFailure(code)
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                if (redirects > MAX_REDIRECTS) throw HttpFailure(code)
                continue
            }
            if (code == 416) {
                connection.disconnect()
                part.delete()
                existing = 0L
                currentUrl = model.downloadUrl
                redirects = 0
                continue
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw HttpFailure(code)
            }
            break
        }

        val response = connection ?: error("No HTTP connection")
        val append = existing > 0L && response.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!append && existing > 0L) {
            // Server ignored Range and returned a fresh 200 response.
            existing = 0L
        }
        val responseLength = max(0L, response.contentLengthLong)
        val total = if (append) existing + responseLength else responseLength
        val expectedTotal = if (total > 0L) total else model.sizeBytes
        state.edit().putLong(KEY_TOTAL, expectedTotal).apply()

        try {
            BufferedInputStream(response.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = if (append) existing else 0L
                    var lastReported = written
                    while (true) {
                        if (isCancelled(id) || Thread.currentThread().isInterrupted) throw InterruptedException("cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        written += count
                        if (written - lastReported >= PROGRESS_GRANULARITY || written >= expectedTotal) {
                            lastReported = written
                            state.edit()
                                .putLong(KEY_DOWNLOADED, written)
                                .putLong(KEY_TOTAL, max(expectedTotal, written))
                                .putInt(KEY_STATUS, DownloadManager.STATUS_RUNNING)
                                .apply()
                        }
                    }
                    output.fd.sync()
                    state.edit().putLong(KEY_DOWNLOADED, written).putLong(KEY_TOTAL, max(expectedTotal, written)).apply()
                }
            }
        } finally {
            response.disconnect()
        }
    }

    private fun sleepBeforeRetry(attempt: Int) {
        Thread.sleep((1500L * attempt).coerceAtMost(6000L))
    }

    private fun isCancelled(id: Long): Boolean =
        state.getLong(KEY_ID, -1L) != id || state.getBoolean(KEY_CANCELLED, false)

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

    private class HttpFailure(val code: Int) : Exception("HTTP $code")

    companion object {
        private const val PREFS = "nanu_image_download_backend"
        private const val KEY_ID = "id"
        private const val KEY_STATUS = "status"
        private const val KEY_DOWNLOADED = "downloaded"
        private const val KEY_TOTAL = "total"
        private const val KEY_REASON = "reason"
        private const val KEY_CANCELLED = "cancelled"
        private const val USER_AGENT = "NanuLocalAI/1.0-rc8 Android"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 256 * 1024
        private const val PROGRESS_GRANULARITY = 1024L * 1024L
        private const val MAX_REDIRECTS = 8
        private const val MAX_ATTEMPTS = 4
        private val workers = ConcurrentHashMap<Long, Thread>()
    }
}
