package com.example.llama

import android.app.DownloadManager
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreateActivity : NanuBaseActivity() {

    private lateinit var promptEt: EditText
    private lateinit var negativeEt: EditText
    private lateinit var statusTv: TextView
    private lateinit var modelTv: TextView
    private lateinit var previewIv: ImageView
    private lateinit var downloadBtn: MaterialButton
    private lateinit var generateBtn: MaterialButton
    private lateinit var qualityBtn: MaterialButton

    private val manager by lazy { ImageModelManager(applicationContext) }
    private val model get() = ImageModelCatalog.starter
    private val prefs by lazy { getSharedPreferences("nanu_image_gen", MODE_PRIVATE) }
    private var monitorJob: Job? = null
    private var generationJob: Job? = null
    @Volatile private var generationProcess: Process? = null
    private var fastMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        promptEt = findViewById(R.id.image_prompt)
        negativeEt = findViewById(R.id.image_negative_prompt)
        statusTv = findViewById(R.id.image_status)
        modelTv = findViewById(R.id.image_model_status)
        previewIv = findViewById(R.id.generated_image)
        downloadBtn = findViewById(R.id.image_model_button)
        generateBtn = findViewById(R.id.generate_image_button)
        qualityBtn = findViewById(R.id.image_quality_button)

        findViewById<MaterialButton>(R.id.create_back).setOnClickListener { finish() }
        downloadBtn.setOnClickListener { handleModelButton() }
        generateBtn.setOnClickListener { generateImage() }
        qualityBtn.setOnClickListener {
            fastMode = !fastMode
            refreshQualityUi()
        }
        findViewById<MaterialButton>(R.id.cancel_image_button).setOnClickListener {
            cancelGeneration("Generation cancelled")
        }

        refreshQualityUi()
        refreshModelUi()
        restoreLastImage()
        resumeDownloadIfNeeded()
    }

    private fun refreshQualityUi() {
        if (fastMode) {
            qualityBtn.text = "Mode: Fast • 384 × 384 • 8 steps"
            generateBtn.text = "Generate fast image"
        } else {
            qualityBtn.text = "Mode: Quality • 512 × 512 • 14 steps"
            generateBtn.text = "Generate quality image"
        }
    }

    private fun modelReady(): Boolean {
        val file = manager.destinationFile(model)
        return file.exists() && manager.looksLikeGguf(file)
    }

    private fun refreshModelUi() {
        if (modelReady()) {
            modelTv.text = "${model.name} • ${model.sizeLabel} • downloaded"
            downloadBtn.text = "Model ready"
            generateBtn.isEnabled = true
            if (prefs.getString(KEY_LAST_IMAGE, null).isNullOrBlank()) {
                statusTv.text = "Ready • Fast mode is recommended for Android CPU generation"
            }
        } else {
            modelTv.text = "Recommended: ${model.name} • ${model.sizeLabel} • ${model.licenseLabel}"
            downloadBtn.text = "Download image model"
            generateBtn.isEnabled = false
            statusTv.text = "Download the recommended image model once, then generation can run locally."
        }
    }

    private fun restoreLastImage() {
        val path = prefs.getString(KEY_LAST_IMAGE, null) ?: return
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) {
            prefs.edit().remove(KEY_LAST_IMAGE).apply()
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        previewIv.setImageBitmap(bitmap)
        previewIv.visibility = View.VISIBLE
        statusTv.text = "Last generated image • ${file.name}\nSaved to Gallery when available"
    }

    private fun handleModelButton() {
        if (modelReady()) {
            Toast.makeText(this, "The local image model is already installed.", Toast.LENGTH_SHORT).show()
            return
        }

        val active = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (active > 0L) {
            monitorDownload(active)
            return
        }

        val dir = manager.downloadDirectory
        val free = StatFs(dir.absolutePath).availableBytes
        val reserve = 1024L * 1024L * 1024L
        if (free < model.sizeBytes + reserve) {
            Toast.makeText(this, "Not enough free storage. Keep at least about 3 GB free for the image model and generation files.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val id = manager.enqueue(model)
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
            downloadBtn.text = "Downloading…"
            statusTv.text = "Starting ${model.sizeLabel} download…"
            monitorDownload(id)
        } catch (e: Exception) {
            statusTv.text = "Download could not start"
            Toast.makeText(this, e.message ?: "Download error", Toast.LENGTH_LONG).show()
        }
    }

    private fun resumeDownloadIfNeeded() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) monitorDownload(id)
    }

    private fun monitorDownload(id: Long) {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val s = manager.query(id)
                if (s == null) {
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    withContext(Dispatchers.Main) {
                        statusTv.text = "Image model download not found"
                        downloadBtn.text = "Download image model"
                    }
                    break
                }

                when (s.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING -> {
                        val total = if (s.totalBytes > 0L) s.totalBytes else model.sizeBytes
                        val percent = if (total > 0L) ((s.downloadedBytes * 100L) / total).coerceIn(0L, 100L) else 0L
                        withContext(Dispatchers.Main) {
                            statusTv.text = "Downloading image model • $percent% • ${formatBytes(s.downloadedBytes)} / ${formatBytes(total)}"
                            downloadBtn.text = "$percent% downloaded"
                        }
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        val file = manager.destinationFile(model)
                        withContext(Dispatchers.Main) {
                            statusTv.text = "Download complete • checking model…"
                            downloadBtn.text = "Checking model…"
                        }
                        val headerOk = manager.looksLikeGguf(file)
                        val hashOk = headerOk && manager.verifySha256(file, model.sha256)
                        if (!hashOk) {
                            file.delete()
                            withContext(Dispatchers.Main) {
                                statusTv.text = "Image model verification failed. Please download again."
                                downloadBtn.text = "Download image model"
                                generateBtn.isEnabled = false
                            }
                        } else {
                            withContext(Dispatchers.Main) { refreshModelUi() }
                        }
                        break
                    }

                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        withContext(Dispatchers.Main) {
                            statusTv.text = "Image model download failed (Android reason ${s.reason})"
                            downloadBtn.text = "Download image model"
                        }
                        break
                    }
                }
                delay(800L)
            }
        }
    }

    private fun generateImage() {
        val prompt = promptEt.text.toString().trim()
        if (prompt.isBlank()) {
            Toast.makeText(this, "Enter an image prompt first.", Toast.LENGTH_SHORT).show()
            return
        }
        val modelFile = manager.destinationFile(model)
        if (!modelReady()) {
            Toast.makeText(this, "Download the recommended image model first.", Toast.LENGTH_LONG).show()
            return
        }

        val native = File(applicationInfo.nativeLibraryDir, "libsd.so")
        if (!native.exists()) {
            statusTv.text = "Image engine is missing from this build."
            return
        }

        cancelGeneration(null)

        val width = if (fastMode) 384 else 512
        val height = width
        val steps = if (fastMode) 8 else 14
        val timeoutMinutes = if (fastMode) 30 else 60
        val modeLabel = if (fastMode) "Fast" else "Quality"

        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "generated").also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val output = File(outputDir, "nanu-$stamp.png")
        val logFile = File(cacheDir, "nanu-image-$stamp.log")
        val negative = negativeEt.text.toString().trim()

        generateBtn.isEnabled = false
        downloadBtn.isEnabled = false
        qualityBtn.isEnabled = false
        previewIv.visibility = View.INVISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusTv.text = "$modeLabel mode • ${width}×$height • $steps steps\nStarting local image engine… Keep Nanu open; the screen will stay awake."

        generationJob = lifecycleScope.launch(Dispatchers.IO) {
            val started = SystemClock.elapsedRealtime()
            var timedOut = false
            try {
                val cmd = mutableListOf(
                    native.absolutePath,
                    "-m", modelFile.absolutePath,
                    "-p", prompt,
                    "-o", output.absolutePath,
                    "--steps", steps.toString(),
                    "-W", width.toString(),
                    "-H", height.toString(),
                    "--vae-tiling"
                )
                if (negative.isNotBlank()) cmd += listOf("-n", negative)

                val processBuilder = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile)
                processBuilder.environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir
                processBuilder.environment()["TMPDIR"] = cacheDir.absolutePath

                val process = processBuilder.start()
                generationProcess = process

                while (process.isAlive) {
                    val elapsedMs = SystemClock.elapsedRealtime() - started
                    if (elapsedMs >= timeoutMinutes * 60_000L) {
                        timedOut = true
                        runCatching { process.destroyForcibly() }
                        break
                    }
                    val detail = lastUsefulLogLine(logFile)
                    withContext(Dispatchers.Main) {
                        statusTv.text = buildString {
                            append("Generating locally • ${formatDuration(elapsedMs)}\n")
                            append("$modeLabel • ${width}×$height • $steps steps")
                            if (detail.isNotBlank()) append("\n${detail.takeLast(160)}")
                        }
                    }
                    delay(2_000L)
                }

                if (process.isAlive) runCatching { process.destroyForcibly() }
                val exit = runCatching { process.waitFor() }.getOrDefault(-1)
                val elapsedMs = SystemClock.elapsedRealtime() - started
                val lastUseful = lastUsefulLogLine(logFile).ifBlank { "No engine detail was returned." }

                if (!timedOut && exit == 0 && output.exists() && output.length() > 0L) {
                    prefs.edit().putString(KEY_LAST_IMAGE, output.absolutePath).apply()
                    val gallerySaved = saveToGallery(output)
                    val bitmap = BitmapFactory.decodeFile(output.absolutePath)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            previewIv.setImageBitmap(bitmap)
                            previewIv.visibility = View.VISIBLE
                            statusTv.text = buildString {
                                append("Done in ${formatDuration(elapsedMs)}")
                                if (gallerySaved) append(" • saved to Gallery → Pictures/Nanu")
                                else append(" • saved in Nanu app storage")
                                append("\n${output.name}")
                            }
                        } else {
                            statusTv.text = "Image file was created but Android could not preview it.\n${output.name}"
                        }
                    }
                } else if (timedOut) {
                    withContext(Dispatchers.Main) {
                        statusTv.text = "Generation stopped after $timeoutMinutes minutes because the engine did not finish.\nTry Fast mode or a simpler prompt.\n$lastUseful"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusTv.text = "Image generation failed (engine exit $exit).\n$lastUseful"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTv.text = "Image generation error: ${e.message ?: "unknown error"}"
                }
            } finally {
                generationProcess = null
                withContext(Dispatchers.Main) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    generateBtn.isEnabled = modelReady()
                    downloadBtn.isEnabled = true
                    qualityBtn.isEnabled = true
                }
            }
        }
    }

    private fun cancelGeneration(message: String?) {
        runCatching { generationProcess?.destroyForcibly() }
        generationProcess = null
        generationJob?.cancel()
        generationJob = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (message != null) {
            statusTv.text = message
            generateBtn.isEnabled = modelReady()
            downloadBtn.isEnabled = true
            qualityBtn.isEnabled = true
        }
    }

    private fun lastUsefulLogLine(file: File): String {
        if (!file.exists() || file.length() == 0L) return "Loading model and preparing tensors…"
        return runCatching {
            file.useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .lastOrNull()
                    .orEmpty()
            }
        }.getOrDefault("")
    }

    private fun saveToGallery(source: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nanu")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            false
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
        else -> "${bytes / 1024L} KB"
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        cancelGeneration(null)
        super.onDestroy()
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "active_image_model_download"
        private const val KEY_LAST_IMAGE = "last_generated_image"
    }
}
