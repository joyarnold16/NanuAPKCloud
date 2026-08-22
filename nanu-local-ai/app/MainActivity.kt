package com.example.llama

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var modelStatusTv: TextView
    private lateinit var modelNameTv: TextView
    private lateinit var modelDetailTv: TextView
    private lateinit var modeTv: TextView
    private lateinit var emptyStateTv: TextView
    private lateinit var statsTv: TextView
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var sendBtn: MaterialButton
    private lateinit var modelsBtn: MaterialButton
    private lateinit var newChatBtn: MaterialButton
    private lateinit var modeBtn: MaterialButton

    private lateinit var engine: InferenceEngine
    private var engineReady = false
    private var generationJob: Job? = null

    private val messages = mutableListOf<Message>()
    private lateinit var messageAdapter: MessageAdapter
    private var currentModelFile: File? = null
    private var currentModelDisplayName: String? = null
    private var isModelReady = false
    private var currentMode = AssistantMode.GENERAL

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0D12")
        window.navigationBarColor = Color.parseColor("#0B0D12")
        setContentView(R.layout.activity_main)

        modelStatusTv = findViewById(R.id.model_status)
        modelNameTv = findViewById(R.id.model_name)
        modelDetailTv = findViewById(R.id.model_detail)
        modeTv = findViewById(R.id.mode_label)
        emptyStateTv = findViewById(R.id.empty_state)
        statsTv = findViewById(R.id.generation_stats)
        messagesRv = findViewById(R.id.messages)
        userInputEt = findViewById(R.id.user_input)
        sendBtn = findViewById(R.id.send_button)
        modelsBtn = findViewById(R.id.models_button)
        newChatBtn = findViewById(R.id.new_chat_button)
        modeBtn = findViewById(R.id.mode_button)

        currentMode = AssistantMode.fromId(prefs.getString(KEY_MODE, null))
        modeTv.text = currentMode.label

        messageAdapter = MessageAdapter(
            messages = messages,
            onCopy = ::copyText,
            onReport = ::reportMessage
        )
        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter

        modelsBtn.setOnClickListener { showModelManager() }
        newChatBtn.setOnClickListener { startNewChat() }
        modeBtn.setOnClickListener { chooseAssistantMode() }
        sendBtn.setOnClickListener {
            if (generationJob?.isActive == true) {
                generationJob?.cancel()
            } else {
                handleUserInput()
            }
        }

        setModelUi(null, false, "Starting local engine…")

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                engineReady = true
                withContext(Dispatchers.Main) {
                    restoreLastModelOrShowWelcome()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setModelUi(null, false, "Engine unavailable")
                    Toast.makeText(this@MainActivity, "Local AI engine failed to start: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val openModelDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) handleSelectedModel(uri)
    }

    private fun restoreLastModelOrShowWelcome() {
        val path = prefs.getString(KEY_LAST_MODEL, null)
        val file = path?.let(::File)
        if (file != null && file.exists() && file.isFile) {
            lifecycleScope.launch { loadModelFile(file, file.nameWithoutExtension, announce = false) }
        } else {
            setModelUi(null, false, "No model loaded")
            showEmptyState(true, "Private AI that runs on your device.\n\nTap Models to see recommended LLMs or import a GGUF model.")
        }
    }

    private fun showModelManager() {
        if (!engineReady) {
            Toast.makeText(this, "The local engine is still starting.", Toast.LENGTH_SHORT).show()
            return
        }

        val files = ensureModelsDirectory().listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            .orEmpty()

        val ramGb = totalDeviceRamGb()
        val best = ModelCatalog.bestForRam(ramGb)
        val labels = mutableListOf(
            "★ Recommended LLMs  •  Best: ${best.name}",
            "Import a downloaded GGUF model"
        )
        labels += files.map { "${it.nameWithoutExtension}  •  ${formatBytes(it.length())}" }
        if (files.isNotEmpty()) labels += "Delete a stored model…"

        AlertDialog.Builder(this)
            .setTitle("Models • ${String.format(Locale.US, "%.1f", ramGb)} GB RAM")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which == 0 -> showRecommendedModelCatalog(ramGb)
                    which == 1 -> openModelDocument.launch(arrayOf("*/*"))
                    which in 2 until (2 + files.size) -> {
                        val file = files[which - 2]
                        lifecycleScope.launch { loadModelFile(file, file.nameWithoutExtension) }
                    }
                    else -> showDeleteModelDialog(files)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun totalDeviceRamGb(): Double {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem / (1024.0 * 1024.0 * 1024.0)
    }

    private fun showRecommendedModelCatalog(ramGb: Double) {
        val best = ModelCatalog.bestForRam(ramGb)
        val labels = ModelCatalog.models.map { model ->
            val badge = when {
                model.id == best.id -> "★ BEST MATCH"
                ramGb + 0.25 >= model.minimumRamGb -> "✓ Compatible"
                else -> "⚠ ${model.minimumRamGb} GB+ suggested"
            }
            "$badge\n${model.name} • ${model.quant}\n${model.sizeLabel} • ${model.speedLabel} • ${model.useCase}"
        }

        AlertDialog.Builder(this)
            .setTitle("Recommended local LLMs")
            .setMessage(
                "Nanu detected about ${String.format(Locale.US, "%.1f", ramGb)} GB RAM. " +
                    "These are GGUF suggestions for the current local engine. Downloads open in your browser so Nanu can remain offline during inference."
            )
            .setItems(labels.toTypedArray()) { _, which ->
                showModelSuggestionDetail(ModelCatalog.models[which], ramGb, best.id)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showModelSuggestionDetail(model: RecommendedModel, ramGb: Double, bestId: String) {
        val status = when {
            model.id == bestId -> "Best match for this device"
            ramGb + 0.25 >= model.minimumRamGb -> "Compatible with this device"
            else -> "May be too heavy for this device"
        }

        val message = buildString {
            append("$status\n\n")
            append("File: ${model.fileName}\n")
            append("Quant: ${model.quant}\n")
            append("Download size: ${model.sizeLabel}\n")
            append("Suggested RAM: ${model.minimumRamGb} GB+\n")
            append("Expected speed: ${model.speedLabel}\n")
            append("Best for: ${model.useCase}\n")
            append("License: ${model.licenseLabel}\n\n")
            append(model.notes)
            append("\n\nAfter downloading, return to Models → Import a downloaded GGUF model.")
        }

        AlertDialog.Builder(this)
            .setTitle(model.name)
            .setMessage(message)
            .setPositiveButton("Open model page") { _, _ -> openModelPage(model) }
            .setNeutralButton("Copy filename") { _, _ -> copyText(model.fileName) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openModelPage(model: RecommendedModel) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.pageUrl))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Model page", model.pageUrl))
            Toast.makeText(this, "No browser found. Model page link copied.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteModelDialog(files: List<File>) {
        AlertDialog.Builder(this)
            .setTitle("Delete stored model")
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                val file = files[which]
                if (currentModelFile?.absolutePath == file.absolutePath && isModelReady) {
                    Toast.makeText(this, "The active model cannot be deleted. Load another model first.", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                AlertDialog.Builder(this)
                    .setMessage("Delete ${file.name} from Nanu Local AI?")
                    .setPositiveButton("Delete") { _, _ ->
                        if (file.delete()) Toast.makeText(this, "Model deleted.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleSelectedModel(uri: Uri) {
        if (!engineReady) return
        setBusyUi("Reading GGUF model…")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val metadata = contentResolver.openInputStream(uri)?.use {
                    GgufMetadataReader.create().readStructuredMetadata(it)
                } ?: error("Unable to read selected file")

                val modelName = metadata.filename() + FILE_EXTENSION_GGUF
                val modelFile = contentResolver.openInputStream(uri)?.use { input ->
                    ensureModelFile(modelName, input)
                } ?: error("Unable to import selected model")

                loadModelFile(modelFile, metadata.basic.name ?: modelFile.nameWithoutExtension)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setModelUi(null, false, "Model import failed")
                    Toast.makeText(this@MainActivity, "Could not load this GGUF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun ensureModelFile(modelName: String, input: InputStream): File = withContext(Dispatchers.IO) {
        val safeName = modelName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(ensureModelsDirectory(), safeName)
        if (!file.exists()) {
            withContext(Dispatchers.Main) { setBusyUi("Importing model to private storage…") }
            FileOutputStream(file).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        file
    }

    private suspend fun loadModelFile(file: File, displayName: String, announce: Boolean = true) {
        if (!engineReady) return
        withContext(Dispatchers.Main) { setBusyUi("Loading local AI…") }

        try {
            generationJob?.cancelAndJoin()
            withContext(Dispatchers.IO) {
                when (engine.state.value) {
                    is InferenceEngine.State.ModelReady,
                    is InferenceEngine.State.Error -> engine.cleanUp()
                    else -> Unit
                }
                engine.loadModel(file.absolutePath)
                engine.setSystemPrompt(currentMode.systemPrompt)
            }

            currentModelFile = file
            currentModelDisplayName = displayName
            isModelReady = true
            prefs.edit()
                .putString(KEY_LAST_MODEL, file.absolutePath)
                .putString(KEY_MODE, currentMode.id)
                .apply()

            withContext(Dispatchers.Main) {
                setModelUi(displayName, true, "Ready")
                modelDetailTv.text = "${formatBytes(file.length())}  •  ${currentMode.label}  •  On-device"
                userInputEt.isEnabled = true
                userInputEt.hint = "Ask Nanu anything…"
                sendBtn.isEnabled = true
                sendBtn.text = "Send"
                if (announce) {
                    messages.clear()
                    messageAdapter.notifyDataSetChanged()
                    statsTv.text = ""
                }
                showEmptyState(messages.isEmpty(), "Nanu is ready.\nYour prompts and model stay on this device.")
            }
        } catch (e: Exception) {
            isModelReady = false
            withContext(Dispatchers.Main) {
                setModelUi(displayName, false, "Load failed")
                userInputEt.isEnabled = false
                sendBtn.isEnabled = false
                Toast.makeText(this@MainActivity, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startNewChat() {
        val file = currentModelFile
        if (!isModelReady || file == null) {
            messages.clear()
            messageAdapter.notifyDataSetChanged()
            showEmptyState(true, "Import a GGUF model to start a private chat.")
            return
        }

        lifecycleScope.launch {
            messages.clear()
            messageAdapter.notifyDataSetChanged()
            statsTv.text = ""
            showEmptyState(true, "Starting a fresh local conversation…")
            loadModelFile(file, currentModelDisplayName ?: file.nameWithoutExtension, announce = false)
        }
    }

    private fun chooseAssistantMode() {
        val modes = AssistantMode.entries.toTypedArray()
        val checked = modes.indexOf(currentMode)
        AlertDialog.Builder(this)
            .setTitle("Assistant mode")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), checked) { dialog, which ->
                val chosen = modes[which]
                dialog.dismiss()
                if (chosen == currentMode) return@setSingleChoiceItems
                currentMode = chosen
                modeTv.text = currentMode.label
                prefs.edit().putString(KEY_MODE, currentMode.id).apply()
                currentModelFile?.let { file ->
                    lifecycleScope.launch {
                        Toast.makeText(this@MainActivity, "Mode changed. Starting a fresh chat.", Toast.LENGTH_SHORT).show()
                        messages.clear()
                        messageAdapter.notifyDataSetChanged()
                        loadModelFile(file, currentModelDisplayName ?: file.nameWithoutExtension, announce = false)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleUserInput() {
        if (!isModelReady) {
            showModelManager()
            return
        }

        val userMsg = userInputEt.text.toString().trim()
        if (userMsg.isEmpty()) return

        userInputEt.text = null
        userInputEt.isEnabled = false
        sendBtn.text = "Stop"
        sendBtn.isEnabled = true
        emptyStateTv.visibility = View.GONE

        val userIndex = messages.size
        messages.add(Message(UUID.randomUUID().toString(), userMsg, true))
        messages.add(Message(UUID.randomUUID().toString(), "Thinking locally…", false))
        messageAdapter.notifyItemRangeInserted(userIndex, 2)
        scrollToBottom()

        val rawAssistant = StringBuilder()
        var emittedTokens = 0
        val startedAt = SystemClock.elapsedRealtime()

        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            engine.sendUserPrompt(userMsg)
                .catch { error ->
                    withContext(Dispatchers.Main) {
                        updateLastAssistant("Generation error: ${error.message ?: "unknown error"}")
                    }
                }
                .onCompletion {
                    val elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
                    val seconds = elapsedMs / 1000.0
                    val speed = emittedTokens / seconds
                    withContext(Dispatchers.Main) {
                        userInputEt.isEnabled = true
                        userInputEt.requestFocus()
                        sendBtn.text = "Send"
                        sendBtn.isEnabled = true
                        statsTv.text = if (emittedTokens > 0) {
                            String.format(Locale.US, "%d tokens  •  %.1f tok/s  •  %.1fs", emittedTokens, speed, seconds)
                        } else {
                            "Generation stopped"
                        }
                    }
                }
                .collect { token ->
                    emittedTokens++
                    rawAssistant.append(token)
                    val visible = stripThinking(rawAssistant.toString()).ifBlank { "Thinking locally…" }
                    withContext(Dispatchers.Main) {
                        updateLastAssistant(visible)
                        scrollToBottom()
                    }
                }
        }
    }

    private fun updateLastAssistant(text: String) {
        val index = messages.indexOfLast { !it.isUser }
        if (index < 0) return
        messages[index] = messages[index].copy(content = text)
        messageAdapter.notifyItemChanged(index)
    }

    private fun stripThinking(raw: String): String {
        var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        text = text.replace("</think>", "")

        val partialTags = listOf("<think", "<thin", "<thi", "<th", "<t", "<")
        for (partial in partialTags) {
            if (text.endsWith(partial)) {
                text = text.dropLast(partial.length)
                break
            }
        }
        return text.trimStart()
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nanu Local AI", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun reportMessage(text: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$REPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Nanu Local AI content report")
            putExtra(
                Intent.EXTRA_TEXT,
                "I would like to report this AI-generated response:\n\n$text\n\nApp version: 1.0 RC2"
            )
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            copyText(text)
            Toast.makeText(this, "No email app found. The response was copied so you can report it manually.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setBusyUi(status: String) {
        modelStatusTv.text = status
        modelStatusTv.setTextColor(getColor(R.color.nanu_accent_2))
        userInputEt.isEnabled = false
        sendBtn.isEnabled = false
        modelsBtn.isEnabled = false
        newChatBtn.isEnabled = false
        modeBtn.isEnabled = false
    }

    private fun setModelUi(name: String?, ready: Boolean, status: String) {
        modelStatusTv.text = status
        modelStatusTv.setTextColor(getColor(if (ready) R.color.nanu_success else R.color.nanu_muted))
        modelNameTv.text = name ?: "No local model"
        if (name == null) modelDetailTv.text = "Import any compatible GGUF model"
        modelsBtn.isEnabled = true
        newChatBtn.isEnabled = true
        modeBtn.isEnabled = true
    }

    private fun showEmptyState(show: Boolean, text: String) {
        emptyStateTv.text = text
        emptyStateTv.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) messagesRv.scrollToPosition(messages.size - 1)
    }

    private fun ensureModelsDirectory(): File = File(filesDir, DIRECTORY_MODELS).also {
        if (it.exists() && !it.isDirectory) it.delete()
        if (!it.exists()) it.mkdirs()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
        else -> "$bytes B"
    }

    override fun onDestroy() {
        generationJob?.cancel()
        if (isFinishing && engineReady) {
            runCatching { engine.destroy() }
        }
        super.onDestroy()
    }

    enum class AssistantMode(val id: String, val label: String, val systemPrompt: String) {
        GENERAL(
            "general",
            "General",
            "You are Nanu Local AI, a private on-device assistant. Give clear, accurate, useful answers. Do not reveal hidden chain-of-thought, private reasoning, or <think> blocks. Provide only the final answer and concise explanation when useful."
        ),
        CODING(
            "coding",
            "Coding",
            "You are Nanu Local AI in Coding mode. Help with programming, debugging, architecture, and code explanation. Prefer correct runnable examples. Do not reveal hidden chain-of-thought, private reasoning, or <think> blocks."
        ),
        STUDY(
            "study",
            "Study",
            "You are Nanu Local AI in Study mode. Teach step by step using simple language, examples, and short checks for understanding. Do not reveal hidden chain-of-thought, private reasoning, or <think> blocks."
        ),
        MARITIME(
            "maritime",
            "Maritime",
            "You are Nanu Local AI in Maritime mode. Assist with maritime study and professional reference questions. Be precise, distinguish training guidance from official requirements, and encourage verification against current official publications for safety-critical decisions. Do not reveal hidden chain-of-thought, private reasoning, or <think> blocks."
        );

        companion object {
            fun fromId(id: String?): AssistantMode = entries.firstOrNull { it.id == id } ?: GENERAL
        }
    }

    companion object {
        private const val PREFS_NAME = "nanu_local_ai"
        private const val KEY_LAST_MODEL = "last_model"
        private const val KEY_MODE = "assistant_mode"
        private const val DIRECTORY_MODELS = "models"
        private const val FILE_EXTENSION_GGUF = ".gguf"
        private const val REPORT_EMAIL = "nanuai.1991@gmail.com"
    }
}

fun GgufMetadata.filename(): String = when {
    basic.name != null -> basic.name!!.let { name -> basic.sizeLabel?.let { "$name-$it" } ?: name }
    architecture?.architecture != null -> architecture!!.architecture!!.let { arch ->
        basic.uuid?.let { "$arch-$it" } ?: "$arch-${java.lang.Long.toHexString(System.currentTimeMillis())}"
    }
    else -> "model-${java.lang.Long.toHexString(System.currentTimeMillis())}"
}
