package com.example.llama

import android.app.DownloadManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
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
import kotlin.math.roundToInt

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
    private val imageInputs by lazy { ImageEditInput(applicationContext) }
    private var selectedImage: SelectedImage? = null
    private var changeStrength = 0.45
    private var importingImage = false
    private var missingInput = false
    private lateinit var inputPreview: ImageView
    private lateinit var addImageBtn: MaterialButton
    private lateinit var strengthLabel: TextView
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importImage(uri)
    }
    private val taskSession = TaskScreenSession(this, "studio_conversation") { rows ->
        if (rows.isEmpty()) { imageView.setImageDrawable(null); imageView.visibility = android.view.View.GONE; statusTv.text = ""; displayedImage = null }
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
        inputPreview = findViewById(R.id.studio_input_preview)
        addImageBtn = findViewById(R.id.studio_add_image)
        strengthLabel = findViewById(R.id.studio_strength_label)
        addImageBtn.setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        findViewById<MaterialButton>(R.id.studio_remove_image).setOnClickListener {
            selectedImage = null
            missingInput = false
            prefs.edit().remove(KEY_SOURCE_IMAGE).apply()
            if (aspectIndex == 3) aspectIndex = 0
            refreshInputUi()
        }
        findViewById<SeekBar>(R.id.studio_strength).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                changeStrength = (progress + 10) / 100.0
                strengthLabel.text = "Change strength: ${progress + 10}%"
                prefs.edit().putFloat(KEY_STRENGTH, changeStrength.toFloat()).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        findViewById<MaterialButton>(R.id.studio_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.studio_model_button).setOnClickListener { downloadOrShowModel() }
        qualityBtn.setOnClickListener { quality = !quality; refreshModeButtons() }
        aspectBtn.setOnClickListener { aspectIndex = (aspectIndex + 1) % (if (selectedImage == null) 3 else ASPECTS.size); refreshModeButtons() }
        generateBtn.setOnClickListener { generate() }
        findViewById<MaterialButton>(R.id.studio_cancel).setOnClickListener { if (LocalTaskService.active.value) LocalTaskService.stop(applicationContext); statusTv.text = "Generation cancelled"; refreshModelUi() }
        findViewById<MaterialButton>(R.id.studio_history).setOnClickListener { showHistory() }
        findViewById<MaterialButton>(R.id.studio_report).setOnClickListener {
            startActivity(android.content.Intent(this, SafetyPrivacyActivity::class.java).putExtra(SafetyPrivacyActivity.EXTRA_REPORTED_CONTENT, "Image prompt: ${promptEt.text.toString().take(2500)}"))
        }

        taskSession.observe()
        negativeEt.setText("blurry, distorted, low quality, malformed")
        selectedImage = imageInputs.restore(prefs.getString(KEY_SOURCE_IMAGE, null))
        missingInput = prefs.getString(KEY_SOURCE_IMAGE, null) != null && selectedImage == null
        if (selectedImage != null) aspectIndex = 3
        changeStrength = prefs.getFloat(KEY_STRENGTH, 0.45f).toDouble().coerceIn(0.1, 0.9)
        restoreEditRequest(intent)
        refreshInputUi()
        refreshModelUi()
        val active = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (active > 0L) monitorDownload(active)
    }

    private fun refreshModeButtons() {
        val aspect = ASPECTS[aspectIndex]
        qualityBtn.text = if (quality) "Quality: High" else "Quality: Fast"
        aspectBtn.text = "Aspect: ${aspect.label}"
        val (w, h) = dimensions()
        generateBtn.text = "${if (selectedImage == null) "Generate" else "Edit image"} • ${w}×${h} • ${if (quality) 14 else 8} steps"
    }

    private fun dimensions(): Pair<Int, Int> {
        selectedImage?.takeIf { aspectIndex == 3 }?.let { return ImageEditInput.dimensions(it.width, it.height, quality) }
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
        generateBtn.isEnabled = ready && !LocalTaskService.active.value && !importingImage
        addImageBtn.isEnabled = !importingImage
    }

    private fun importImage(uri: Uri) {
        if (importingImage) return
        importingImage = true
        refreshModelUi()
        statusTv.text = "Preparing selected photo…"
        lifecycleScope.launch {
            var copy: SelectedImage? = null
            var accepted = false
            try {
                withContext(Dispatchers.IO) { copy = imageInputs.import(uri) }
                selectedImage = copy
                missingInput = false
                accepted = true
                aspectIndex = 3
                prefs.edit().putString(KEY_SOURCE_IMAGE, copy!!.path).apply()
                refreshInputUi()
                statusTv.text = "Photo ready. Describe how the edited image should look."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                statusTv.text = "Could not open photo. Try a valid JPEG, PNG, WebP or HEIC image."
                Toast.makeText(this@CreateStudioActivity, statusTv.text, Toast.LENGTH_LONG).show()
            } finally {
                if (!accepted) copy?.let { File(it.path).delete() }
                importingImage = false
                refreshModelUi()
            }
        }
    }

    private fun refreshInputUi() {
        val selected = selectedImage
        findViewById<View>(R.id.studio_edit_options).visibility = if (selected == null && !missingInput) View.GONE else View.VISIBLE
        inputPreview.setImageBitmap(selected?.let { BitmapFactory.decodeFile(it.path) })
        addImageBtn.text = if (selected == null) "Add image from device" else "Replace image"
        findViewById<SeekBar>(R.id.studio_strength).progress = (changeStrength * 100).roundToInt() - 10
        strengthLabel.text = "Change strength: ${(changeStrength * 100).roundToInt()}%"
        promptEt.hint = if (selected == null) "A cinematic lighthouse in a storm at night…" else "Describe the finished picture, e.g. the same lighthouse at sunset, warm light, calm sea…"
        refreshModeButtons()
    }

    private fun restoreEditRequest(intent: Intent) {
        val raw = intent.getStringExtra(EXTRA_IMAGE_OPTIONS) ?: return
        runCatching {
            val options = JSONObject(raw)
            val input = options.optString("inputImage").takeIf { it.isNotBlank() }
            selectedImage = imageInputs.restore(input)
            missingInput = input != null && selectedImage == null
            if (input != null && selectedImage == null) Toast.makeText(this, "The original photo is missing. Please add it again.", Toast.LENGTH_LONG).show()
            prefs.edit().putString(KEY_SOURCE_IMAGE, input).apply()
            changeStrength = options.optDouble("strength", 0.45).takeIf { it.isFinite() }?.coerceIn(0.1, 0.9) ?: 0.45
            quality = options.optBoolean("quality")
            val w = options.optInt("width", 384)
            val h = options.optInt("height", 384)
            val fallbackAspect = if (selectedImage != null) 3 else if (w == h) 0 else if (w > h) 1 else 2
            aspectIndex = options.optInt("aspect", fallbackAspect).coerceIn(0, if (selectedImage != null) 3 else 2)
            negativeEt.setText(options.optString("negative", "blurry, distorted, low quality, malformed"))
            promptEt.setText(intent.getStringExtra(EXTRA_IMAGE_PROMPT).orEmpty())
        }.onFailure { Toast.makeText(this, "Could not restore image settings.", Toast.LENGTH_LONG).show() }
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
        if (importingImage) return
        if (missingInput) {
            Toast.makeText(this, "The original photo is missing. Add it again, or tap Remove image to start without it.", Toast.LENGTH_LONG).show()
            return
        }
        if (!modelReady()) { downloadOrShowModel(); return }
        if (selectedImage != null && imageInputs.restore(selectedImage!!.path) == null) {
            Toast.makeText(this, "Selected photo is unavailable. Add it again.", Toast.LENGTH_LONG).show()
            return
        }
        val (w, h) = dimensions()
        taskSession.submit(prompt, SafetyGuard.SYSTEM_RULES, org.json.JSONObject()
            .put("image", true).put("negative", negativeEt.text.toString().trim())
            .put("quality", quality).put("width", w).put("height", h).put("steps", if (quality) 14 else 8)
            .put("inputImage", selectedImage?.path).put("strength", changeStrength).put("aspect", aspectIndex))
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
        private const val KEY_SOURCE_IMAGE = "selected_source_image"
        private const val KEY_STRENGTH = "change_strength"
        const val EXTRA_IMAGE_OPTIONS = "image_options"
        const val EXTRA_IMAGE_PROMPT = "image_prompt"
        private val ASPECTS = listOf(Aspect("1:1"), Aspect("16:9"), Aspect("9:16"), Aspect("Original"))
    }
}
