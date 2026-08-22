package com.example.llama

import android.app.DownloadManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.View
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

    private val manager by lazy { ImageModelManager(applicationContext) }
    private val model get() = ImageModelCatalog.starter
    private val prefs by lazy { getSharedPreferences("nanu_image_gen", MODE_PRIVATE) }
    private var monitorJob: Job? = null
    private var generationJob: Job? = null

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

        findViewById<MaterialButton>(R.id.create_back).setOnClickListener { finish() }
        downloadBtn.setOnClickListener { handleModelButton() }
        generateBtn.setOnClickListener { generateImage() }
        findViewById<MaterialButton>(R.id.cancel_image_button).setOnClickListener {
            generationJob?.cancel()
            statusTv.text = "Generation cancelled"
            generateBtn.isEnabled = modelReady()
        }

        refreshModelUi()
        resumeDownloadIfNeeded()
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
            statusTv.text = "Ready for local image generation"
        } else {
            modelTv.text = "Recommended: ${model.name} • ${model.sizeLabel} • ${model.licenseLabel}"
            downloadBtn.text = "Download image model"
            generateBtn.isEnabled = false
            statusTv.text = "Download the recommended image model once, then generation can run locally."
        }
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

        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "generated").also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val output = File(outputDir, "nanu-$stamp.png")
        val negative = negativeEt.text.toString().trim()

        generateBtn.isEnabled = false
        downloadBtn.isEnabled = false
        previewIv.visibility = View.INVISIBLE
        statusTv.text = "Starting local image engine… This can take several minutes on a phone or tablet."

        generationJob?.cancel()
        generationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cmd = mutableListOf(
                    native.absolutePath,
                    "-m", modelFile.absolutePath,
                    "-p", prompt,
                    "-o", output.absolutePath,
                    "--steps", "12",
                    "-W", "512",
                    "-H", "512",
                    "--vae-tiling"
                )
                if (negative.isNotBlank()) {
                    cmd += listOf("-n", negative)
                }

                val processBuilder = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                processBuilder.environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir
                processBuilder.environment()["TMPDIR"] = cacheDir.absolutePath

                val process = processBuilder.start()
                var lastUseful = "Loading image model…"
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            lastUseful = line.takeLast(180)
                            withContext(Dispatchers.Main) {
                                statusTv.text = "Generating locally…\n$lastUseful"
                            }
                        }
                    }
                }
                val exit = process.waitFor()

                if (exit == 0 && output.exists() && output.length() > 0L) {
                    val bitmap = BitmapFactory.decodeFile(output.absolutePath)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            previewIv.setImageBitmap(bitmap)
                            previewIv.visibility = View.VISIBLE
                            statusTv.text = "Done • saved in Nanu app storage\n${output.name}"
                        } else {
                            statusTv.text = "Image file was created but Android could not preview it."
                        }
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
                withContext(Dispatchers.Main) {
                    generateBtn.isEnabled = modelReady()
                    downloadBtn.isEnabled = true
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
        else -> "${bytes / 1024L} KB"
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        generationJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "active_image_model_download"
    }
}
