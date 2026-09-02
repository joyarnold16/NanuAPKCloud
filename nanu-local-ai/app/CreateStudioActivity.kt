package com.example.llama

import android.app.DownloadManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CreateStudioActivity : AppCompatActivity() {
    private lateinit var modelStatusTv: TextView
    private lateinit var promptEt: EditText
    private lateinit var negativeEt: EditText
    private lateinit var qualityBtn: MaterialButton
    private lateinit var aspectBtn: MaterialButton
    private lateinit var generateBtn: MaterialButton
    private lateinit var statusTv: TextView
    private lateinit var imageView: ImageView

    private val manager by lazy { ImageModelManager(applicationContext) }
    private val generator by lazy { LocalImageGenerator(applicationContext) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var quality = false
    private var aspectIndex = 0
    private var generationJob: Job? = null
    private var displayedImage: String? = null
    private val taskSession = TaskScreenSession(this, "studio_conversation") { rows ->
        rows.lastOrNull { !it.isUser }?.let { reply ->
            statusTv.text = reply.status
            reply.imagePath?.takeIf { it != displayedImage }?.let { path ->
                displayedImage = path
                imageView.setImageBitmap(BitmapFactory.decodeFile(path))
                imageView.visibility = android.view.View.VISIBLE
            }
        }
        refreshModelUi()
    }
    private var downloadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_create_studio)

        modelStatusTv = findViewById(R.id.studio_model_status)
        promptEt = findViewById(R.id.studio_prompt)
        negativeEt = findViewById(R.id.studio_negative)
        qualityBtn = findViewById(R.id.studio_quality)
        aspectBtn = findViewById(R.id.studio_aspect)
        generateBtn = findViewById(R.id.studio_generate)
        statusTv = findViewById(R.id.studio_status)
        imageView = findViewById(R.id.studio_image)

        findViewById<MaterialButton>(R.id.studio_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.studio_model_button).setOnClickListener { downloadOrShowModel() }
        qualityBtn.setOnClickListener { quality = !quality; refreshModeButtons() }
        aspectBtn.setOnClickListener { aspectIndex = (aspectIndex + 1) % ASPECTS.size; refreshModeButtons() }
        generateBtn.setOnClickListener { generate() }
        findViewById<MaterialButton>(R.id.studio_cancel).setOnClickListener { if (LocalTaskService.active.value) LocalTaskService.stop(applicationContext); statusTv.text = "Generation cancelled"; refreshModelUi() }
        findViewById<MaterialButton>(R.id.studio_history).setOnClickListener { showHistory() }
        findViewById<MaterialButton>(R.id.studio_report).setOnClickListener {
            startActivity(android.content.Intent(this, SafetyPrivacyActivity::class.java).putExtra(SafetyPrivacyActivity.EXTRA_REPORTED_CONTENT, "Image prompt: ${promptEt.text.toString().take(2500)}"))
        }

        taskSession.observe()
        negativeEt.setText("blurry, distorted, low quality, malformed")
        refreshModeButtons()
        refreshModelUi()
        val active = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (active > 0L) monitorDownload(active)
    }

    private fun refreshModeButtons() {
        val aspect = ASPECTS[aspectIndex]
        qualityBtn.text = if (quality) "Quality: High" else "Quality: Fast"
        aspectBtn.text = "Aspect: ${aspect.label}"
        val (w, h) = dimensions()
        generateBtn.text = "Generate • ${w}×${h} • ${if (quality) 14 else 8} steps"
    }

    private fun dimensions(): Pair<Int, Int> {
        return when (ASPECTS[aspectIndex].label) {
            "16:9" -> 512 to 288
            "9:16" -> 288 to 512
            else -> if (quality) 512 to 512 else 384 to 384
        }
    }

    private fun modelReady(): Boolean {
        val file = manager.destinationFile()
        return file.exists() && manager.looksLikeGguf(file)
    }

    private fun refreshModelUi() {
        val ready = modelReady()
        modelStatusTv.text = if (ready) "${ImageModelCatalog.starter.name} • ready locally" else "Image model not downloaded • ${ImageModelCatalog.starter.sizeLabel}"
        generateBtn.isEnabled = ready && !LocalTaskService.active.value
    }

    private fun downloadOrShowModel() {
        if (modelReady()) {
            AlertDialog.Builder(this)
                .setTitle("Image model ready")
                .setMessage("${ImageModelCatalog.starter.name}\n${ImageModelCatalog.starter.sizeLabel}\nLicense: ${ImageModelCatalog.starter.licenseLabel}\n\nGeneration stays on this device.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val existing = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existing > 0L) { monitorDownload(existing); return }
        val free = StatFs(manager.downloadDirectory.absolutePath).availableBytes
        if (free < ImageModelCatalog.starter.sizeBytes + 1024L * 1024L * 1024L) {
            Toast.makeText(this, "Keep at least about 3 GB free before downloading the image model.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Download image model?")
            .setMessage("${ImageModelCatalog.starter.name} • ${ImageModelCatalog.starter.sizeLabel}\n\nOne-time download. Image generation is local after download.")
            .setPositiveButton("Download in Nanu") { _, _ ->
                val id = manager.enqueue()
                prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
                monitorDownload(id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun monitorDownload(id: Long) {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val snap = manager.query(id)
                if (snap == null) { prefs.edit().remove(KEY_DOWNLOAD_ID).apply(); break }
                when (snap.status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        val total = if (snap.totalBytes > 0) snap.totalBytes else ImageModelCatalog.starter.sizeBytes
                        val percent = if (total > 0) ((snap.downloadedBytes * 100L) / total).coerceIn(0, 100) else 0
                        withContext(Dispatchers.Main) { modelStatusTv.text = "Downloading image model • $percent%" }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = manager.destinationFile()
                        val ok = manager.looksLikeGguf(file) && manager.verifySha256(file, ImageModelCatalog.starter.sha256)
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        if (!ok) file.delete()
                        withContext(Dispatchers.Main) {
                            if (ok) statusTv.text = "Image model verified and ready" else statusTv.text = "Image model verification failed"
                            refreshModelUi()
                        }
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                        withContext(Dispatchers.Main) { modelStatusTv.text = "Download failed • reason ${snap.reason}"; refreshModelUi() }
                        break
                    }
                }
                delay(850L)
            }
        }
    }

    private fun generate() {
        val prompt = promptEt.text.toString().trim()
        if (prompt.isBlank()) { Toast.makeText(this, "Describe the image you want.", Toast.LENGTH_SHORT).show(); return }
        if (!modelReady()) { downloadOrShowModel(); return }
        val (w, h) = dimensions()
        taskSession.submit(prompt, SafetyGuard.SYSTEM_RULES, org.json.JSONObject()
            .put("image", true).put("negative", negativeEt.text.toString().trim())
            .put("quality", quality).put("width", w).put("height", h).put("steps", if (quality) 14 else 8))
    }

    private fun showHistory() {
        val root = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "generated")
        val files = root.listFiles()?.filter { it.isFile && it.extension.equals("png", true) }?.sortedByDescending { it.lastModified() }.orEmpty().take(12)
        if (files.isEmpty()) { Toast.makeText(this, "No generated images yet.", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle("Recent Nanu images")
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                val file = files[which]
                imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                imageView.visibility = android.view.View.VISIBLE
                statusTv.text = "History • ${file.name}"
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroy() {
        generationJob?.cancel()
        downloadJob?.cancel()
        generator.cancel()
        super.onDestroy()
    }

    private data class Aspect(val label: String)
    companion object {
        private const val PREFS = "nanu_create_rc8"
        private const val KEY_DOWNLOAD_ID = "image_download_id"
        private val ASPECTS = listOf(Aspect("1:1"), Aspect("16:9"), Aspect("9:16"))
    }
}
