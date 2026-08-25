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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class ModelDownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val isFinished: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED
}

/**
 * Resumable in-app downloader for Nanu GGUF language models.
 *
 * Large Hugging Face files can be served through several redirected storage
 * hosts. Android DownloadManager is convenient, but on some Android/network
 * combinations those multi-GB redirected transfers can stall or fail. Nanu
 * now follows redirects itself, keeps a .part file, retries transient errors,
 * and resumes an interrupted transfer when the app is reopened.
 *
 * The public API intentionally keeps DownloadManager-style status constants so
 * MainActivity can keep its existing progress UI.
 */
class ModelDownloadManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val state = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val downloadDirectory: File
        get() {
            val root = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
            return File(root, "models").also { if (!it.exists()) it.mkdirs() }
        }

    fun destinationFile(model: RecommendedModel): File = File(downloadDirectory, model.fileName)

    private fun partialFile(model: RecommendedModel): File = File(downloadDirectory, "${model.fileName}.part")

    fun enqueue(model: RecommendedModel): Long {
        val target = destinationFile(model)
        if (target.exists()) target.delete()

        val oldModelId = state.getString(KEY_MODEL_ID, null)
        if (oldModelId != null && oldModelId != model.id) {
            ModelCatalog.models.firstOrNull { it.id == oldModelId }?.let { partialFile(it).delete() }
        }

        val id = System.currentTimeMillis().coerceAtLeast(1L)
        val partial = partialFile(model)
        state.edit()
            .putLong(KEY_ID, id)
            .putString(KEY_MODEL_ID, model.id)
            .putInt(KEY_STATUS, DownloadManager.STATUS_PENDING)
            .putLong(KEY_DOWNLOADED, partial.takeIf { it.exists() }?.length() ?: 0L)
            .putLong(KEY_TOTAL, model.sizeBytes)
            .putInt(KEY_REASON, 0)
            .putBoolean(KEY_CANCELLED, false)
            .apply()
        startWorker(id, model)
        return id
    }

    fun cancel(downloadId: Long) {
        if (state.getLong(KEY_ID, -1L) != downloadId) return
        state.edit()
            .putBoolean(KEY_CANCELLED, true)
            .putInt(KEY_STATUS, DownloadManager.STATUS_FAILED)
            .putInt(KEY_REASON, DownloadManager.ERROR_UNKNOWN)
            .apply()
        workers.remove(downloadId)?.interrupt()
        storedModel()?.let { partialFile(it).delete() }
    }

    fun query(downloadId: Long): ModelDownloadSnapshot? {
        if (state.getLong(KEY_ID, -1L) != downloadId) return null
        val status = state.getInt(KEY_STATUS, DownloadManager.STATUS_PENDING)
        if (status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PAUSED
        ) {
            val model = storedModel()
            if (model == null) return null
            if (workers[downloadId]?.isAlive != true) startWorker(downloadId, model)
        }
        return ModelDownloadSnapshot(
            status = state.getInt(KEY_STATUS, status),
            downloadedBytes = state.getLong(KEY_DOWNLOADED, 0L),
            totalBytes = state.getLong(KEY_TOTAL, storedModel()?.sizeBytes ?: 0L),
            reason = state.getInt(KEY_REASON, 0)
        )
    }

    private fun storedModel(): RecommendedModel? {
        val modelId = state.getString(KEY_MODEL_ID, null) ?: return null
        return ModelCatalog.models.firstOrNull { it.id == modelId }
    }

    private fun startWorker(id: Long, model: RecommendedModel) {
        synchronized(workers) {
            if (workers[id]?.isAlive == true) return
            val thread = Thread({ runDownload(id, model) }, "nanu-llm-download-$id").apply {
                isDaemon = true
            }
            workers[id] = thread
            thread.start()
        }
    }

    private fun runDownload(id: Long, model: RecommendedModel) {
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
                    if (attempt == MAX_ATTEMPTS || (e.code in 400..499 && e.code !in setOf(408, 416, 429))) break
                    pauseForRetry(attempt, lastReason)
                } catch (_: InterruptedException) {
                    return
                } catch (_: Exception) {
                    lastReason = DownloadManager.ERROR_HTTP_DATA_ERROR
                    if (attempt == MAX_ATTEMPTS) break
                    pauseForRetry(attempt, lastReason)
                }
            }
            if (!isCancelled(id)) {
                state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_FAILED).putInt(KEY_REASON, lastReason).apply()
            }
        } finally {
            workers.remove(id)
        }
    }

    private fun pauseForRetry(attempt: Int, reason: Int) {
        state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_PAUSED).putInt(KEY_REASON, reason).apply()
        Thread.sleep((1500L * attempt).coerceAtMost(6000L))
        state.edit().putInt(KEY_STATUS, DownloadManager.STATUS_RUNNING).putInt(KEY_REASON, 0).apply()
    }

    private fun downloadAttempt(id: Long, model: RecommendedModel) {
        val part = partialFile(model)
        var existing = if (part.exists()) part.length() else 0L
        var currentUrl = model.downloadUrl.substringBefore("?download=true")
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
                currentUrl = model.downloadUrl.substringBefore("?download=true")
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
        if (!append && existing > 0L) existing = 0L

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
                    state.edit()
                        .putLong(KEY_DOWNLOADED, written)
                        .putLong(KEY_TOTAL, max(expectedTotal, written))
                        .apply()
                }
            }
        } finally {
            response.disconnect()
        }
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

    private class HttpFailure(val code: Int) : Exception("HTTP $code")

    companion object {
        private const val PREFS = "nanu_llm_download_backend"
        private const val KEY_ID = "id"
        private const val KEY_MODEL_ID = "model_id"
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
