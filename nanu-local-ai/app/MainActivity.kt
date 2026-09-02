package com.example.llama

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

class MainActivity : NanuBaseActivity(), TextToSpeech.OnInitListener {

    private lateinit var modelStatusTv: TextView
    private lateinit var emptyStateTv: TextView
    private lateinit var statsTv: TextView
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var actionBtn: MaterialButton
    private lateinit var modelsBtn: MaterialButton
    private lateinit var newChatBtn: MaterialButton
    private lateinit var plusBtn: MaterialButton
    private lateinit var modeChip: MaterialButton
    private lateinit var attachmentCard: MaterialCardView
    private lateinit var attachmentNameTv: TextView
    private lateinit var attachmentRemoveTv: TextView

    private lateinit var engine: InferenceEngine
    private var engineReady = false
    private var generationJob: Job? = null
    private val chatStore by lazy { ChatStore.get(applicationContext) }
    private var conversationId: String? = null
    private var submitting = false
    private var voiceReplyId: String? = null
    private var pendingVoice = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        handleUserInput(pendingVoice)
    }
    private var askedNotifications = false
    private var downloadJob: Job? = null
    private var imageDownloadJob: Job? = null
    private var downloadDialog: AlertDialog? = null

    private val messages = mutableListOf<Message>()
    private lateinit var messageAdapter: MessageAdapter
    private var currentModelFile: File? = null
    private var currentModelDisplayName: String? = null
    private var isModelReady = false
    private var currentMode = AssistantMode.GENERAL
    private var currentAttachment: NanuAttachment? = null
    private var lastUserPrompt: String? = null

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val modelDownloader by lazy { ModelDownloadManager(applicationContext) }
    private val attachmentManager by lazy { AttachmentManager(applicationContext) }
    private val imageModelManager by lazy { ImageModelManager(applicationContext) }
    private val imageGenerator by lazy { LocalImageGenerator(applicationContext) }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var preferOfflineSpeech = true

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening(true)
        else Toast.makeText(this, "Microphone permission is required for voice chat.", Toast.LENGTH_LONG).show()
    }

    private val openModelDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleSelectedModel(uri)
    }

    private val openAttachmentDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        attachmentCard.visibility = View.VISIBLE
        attachmentNameTv.text = "Reading attachment…"
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { attachmentManager.import(uri) }
                .onSuccess { attachment ->
                    withContext(Dispatchers.Main) {
                        currentAttachment = attachment
                        refreshAttachmentUi()
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        currentAttachment = null
                        refreshAttachmentUi()
                        Toast.makeText(this@MainActivity, "Could not attach file: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_main)

        modelStatusTv = findViewById(R.id.model_status)
        emptyStateTv = findViewById(R.id.empty_state)
        statsTv = findViewById(R.id.generation_stats)
        messagesRv = findViewById(R.id.messages)
        userInputEt = findViewById(R.id.user_input)
        actionBtn = findViewById(R.id.send_button)
        modelsBtn = findViewById(R.id.models_button)
        newChatBtn = findViewById(R.id.new_chat_button)
        plusBtn = findViewById(R.id.plus_button)
        modeChip = findViewById(R.id.mode_chip)
        attachmentCard = findViewById(R.id.attachment_card)
        attachmentNameTv = findViewById(R.id.attachment_name)
        attachmentRemoveTv = findViewById(R.id.attachment_remove)

        currentMode = AssistantMode.fromId(prefs.getString(KEY_MODE, null))
        refreshModeUi()
        refreshAttachmentUi()

        messageAdapter = MessageAdapter(
            messages = messages,
            onCopy = ::copyText,
            onSpeak = ::speakText,
            onRegenerate = ::regenerateMessage,
            onShare = ::shareMessage,
            onEditPrompt = ::editMessagePrompt,
            onSaveImage = ::saveImageMessage
        )
        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter

        modelsBtn.setOnClickListener { showModelManager() }
        newChatBtn.setOnClickListener { startNewChat() }
        findViewById<View>(R.id.history_button).setOnClickListener { showHistory() }
        plusBtn.setOnClickListener { showPlusMenu() }
        modeChip.setOnClickListener { switchMode(AssistantMode.GENERAL) }
        attachmentRemoveTv.setOnClickListener {
            currentAttachment = null
            refreshAttachmentUi()
        }
        actionBtn.setOnClickListener {
            if ((LocalTaskService.active.value || submitting)) stopCurrentGeneration()
            else if (userInputEt.text.toString().trim().isNotEmpty()) handleUserInput(false)
            else requestOrStartListening()
        }
        userInputEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateComposerAction()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        tts = TextToSpeech(this, this)
        createSpeechRecognizer()
        setModelUi(null, false, "Starting local engine…")
        resumeImageModelDownloadIfNeeded()

        lifecycleScope.launch {
            LocalTaskService.recover(applicationContext)
            val existing = chatStore.list()
            val wanted = intent.getStringExtra("conversation") ?: prefs.getString("last_conversation", null)
            conversationId = existing.firstOrNull { it.id == wanted }?.id ?: chatStore.create(currentMode.id)
            prefs.edit().putString("last_conversation", conversationId).apply()
            currentMode = AssistantMode.fromId(existing.firstOrNull { it.id == conversationId }?.mode ?: currentMode.id)
            refreshModeUi()
            engineReady = true
            restoreLastModelOrShowWelcome()
            resumePendingModelDownload()
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { chatStore.changes.collect { refreshHistoryMessages() } }
                launch { LocalTaskService.active.collect {
                    updateComposerAction()
                    modelsBtn.isEnabled = !it
                    newChatBtn.isEnabled = !it
                    if (!it) refreshHistoryMessages()
                } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("conversation")?.let { id -> lifecycleScope.launch { openConversation(id) } }
    }

    private suspend fun refreshHistoryMessages() {
        val id = conversationId ?: return
        val saved = chatStore.messages(id)
        if (conversationId != id) return
        messages.clear()
        messages.addAll(saved)
        messageAdapter.notifyDataSetChanged()
        saved.firstOrNull { it.id == voiceReplyId && it.status == "Complete" }?.let {
            voiceReplyId = null
            speakText(it.content)
        }
        statsTv.text = saved.lastOrNull { !it.isUser }?.let { it.generationStats ?: it.status }.orEmpty()
        lastUserPrompt = saved.lastOrNull { it.isUser }?.sourcePrompt
        showEmptyState(saved.isEmpty(), "Start a conversation. Messages are saved on this device.")
        scrollToBottom()
    }

    private suspend fun openConversation(id: String) {
        val conversation = chatStore.list().firstOrNull { it.id == id } ?: return
        conversationId = id
        prefs.edit().putString("last_conversation", id).apply()
        switchMode(AssistantMode.fromId(conversation.mode))
        currentAttachment = null
        refreshAttachmentUi()
        userInputEt.setText("")
        refreshHistoryMessages()
    }

    private fun showHistory() {
        val panel = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val search = EditText(this).apply { hint = "Search conversations"; isSingleLine = true }
        val list = android.widget.ListView(this)
        panel.addView(search)
        panel.addView(list, android.widget.LinearLayout.LayoutParams(-1, (360 * resources.displayMetrics.density).toInt()))
        val dialog = AlertDialog.Builder(this).setTitle("Chat history").setView(panel)
            .setNegativeButton("Close", null).setNeutralButton("Clear all") { _, _ -> confirmDelete(null) }.create()
        var rows = emptyList<Conversation>()
        var searchJob: Job? = null
        fun reload() {
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                rows = chatStore.list(search.text.toString())
                val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                list.adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, rows.map { it.title + "\n" + date.format(java.util.Date(it.updated)) })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { reload() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val row = rows.getOrNull(position) ?: return@setOnItemClickListener
            AlertDialog.Builder(this).setTitle(row.title).setItems(arrayOf("Open / continue", "Rename", "Delete")) { _, action ->
                when(action) {
                    0 -> { dialog.dismiss(); lifecycleScope.launch { openConversation(row.id) } }
                    1 -> {
                        val name = EditText(this).apply { setText(row.title); isSingleLine = true }
                        AlertDialog.Builder(this).setTitle("Rename conversation").setView(name).setNegativeButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ -> lifecycleScope.launch {
                                if (name.text.toString().isNotBlank()) chatStore.rename(row.id, name.text.toString())
                                reload()
                            } }.show()
                    }
                    2 -> { dialog.dismiss(); confirmDelete(row.id) }
                }
            }.show()
        }
        dialog.setOnDismissListener { searchJob?.cancel() }
        dialog.show()
        reload()
    }

    private fun confirmDelete(id: String?) {
        AlertDialog.Builder(this).setTitle(if (id == null) "Clear all chat history?" else "Delete conversation?")
            .setMessage("This permanently removes saved messages from this device. Exported images remain in your gallery.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> lifecycleScope.launch {
                try {
                    chatStore.delete(id)
                    if (id == null || id == conversationId) {
                        conversationId = chatStore.create(currentMode.id)
                        prefs.edit().putString("last_conversation", conversationId).apply()
                        refreshHistoryMessages()
                    }
                } catch (e: Exception) { Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show() }
            } }.show()
    }

    private fun showPlusMenu() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_plus_menu, null)
        dialog.setContentView(view)

        fun choose(id: Int, mode: AssistantMode) {
            view.findViewById<View>(id).setOnClickListener {
                dialog.dismiss()
                switchMode(mode)
            }
        }
        choose(R.id.plus_general, AssistantMode.GENERAL)
        choose(R.id.plus_code, AssistantMode.CODING)
        choose(R.id.plus_academics, AssistantMode.ACADEMICS)
        choose(R.id.plus_trading, AssistantMode.TRADING)
        choose(R.id.plus_image, AssistantMode.IMAGE)
        view.findViewById<View>(R.id.plus_attach).setOnClickListener {
            dialog.dismiss()
            openAttachmentDocument.launch(
                arrayOf(
                    "image/*", "application/pdf", "text/*",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            )
        }
        dialog.show()
    }

    private fun switchMode(mode: AssistantMode) {
        currentMode = mode
        prefs.edit().putString(KEY_MODE, mode.id).apply()
        refreshModeUi()
        when (mode) {
            AssistantMode.IMAGE -> {
                userInputEt.hint = "Describe the image you want…"
                if (!imageModelReady()) modelStatusTv.text = "Create Image selected • image model needs download"
            }
            AssistantMode.TRADING -> userInputEt.hint = "Ask about a market, setup, chart, or risk…"
            AssistantMode.CODING -> userInputEt.hint = "Ask Nanu to write, debug, or explain code…"
            AssistantMode.ACADEMICS -> userInputEt.hint = "Ask a study or research question…"
            AssistantMode.GENERAL -> userInputEt.hint = "Message Nanu…"
        }
    }

    private fun refreshModeUi() {
        if (currentMode == AssistantMode.GENERAL) {
            modeChip.visibility = View.GONE
        } else {
            modeChip.visibility = View.VISIBLE
            modeChip.text = "${currentMode.label} ×"
        }
    }

    private fun refreshAttachmentUi() {
        val attachment = currentAttachment
        if (attachment == null) {
            attachmentCard.visibility = View.GONE
            attachmentNameTv.text = ""
        } else {
            attachmentCard.visibility = View.VISIBLE
            attachmentNameTv.text = "${attachment.displayName}  •  ${formatBytes(attachment.sizeBytes)}"
        }
    }

    private fun updateComposerAction() {
        actionBtn.text = when {
            (LocalTaskService.active.value || submitting) -> "Stop"
            userInputEt.text.toString().trim().isNotEmpty() -> "Send"
            else -> "Tap to talk"
        }
    }

    private fun requestOrStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening(true)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun createSpeechRecognizer() {
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        }.getOrElse { SpeechRecognizer.createSpeechRecognizer(this) }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                modelStatusTv.text = if (preferOfflineSpeech) "Listening • offline preferred" else "Listening…"
                actionBtn.text = "Listening"
            }
            override fun onBeginningOfSpeech() { modelStatusTv.text = "Listening…" }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { modelStatusTv.text = "Processing speech…" }
            override fun onError(error: Int) {
                actionBtn.text = "Tap to talk"
                if (preferOfflineSpeech && (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)) {
                    preferOfflineSpeech = false
                    lifecycleScope.launch {
                        delay(250L)
                        startListening(false)
                    }
                } else {
                    modelStatusTv.text = speechError(error)
                }
            }
            override fun onResults(results: Bundle?) {
                preferOfflineSpeech = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    modelStatusTv.text = "I didn't catch that. Try again."
                    updateComposerAction()
                    return
                }
                userInputEt.setText(text)
                userInputEt.setSelection(text.length)
                handleUserInput(true)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    userInputEt.setText(text)
                    userInputEt.setSelection(text.length)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startListening(preferOffline: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            modelStatusTv.text = "Android speech recognition is unavailable"
            return
        }
        preferOfflineSpeech = preferOffline
        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { modelStatusTv.text = "Speech recognition could not start" }
    }

    private fun speechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition stopped (code $code)"
    }

    private fun handleUserInput(fromVoice: Boolean) {
        val userMsg = userInputEt.text.toString().trim()
        if (userMsg.isEmpty()) return

        if (currentMode == AssistantMode.IMAGE) {
            if (!imageModelReady()) {
                offerImageModelDownload()
                return
            }
        } else if (!isModelReady) {
            showModelManager()
            return
        }

        if (LocalTaskService.active.value || submitting || conversationId == null) return
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotifications = true
            pendingVoice = fromVoice
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val id = conversationId ?: return
        val attachment = currentAttachment
        val mode = currentMode
        val user = Message(UUID.randomUUID().toString(), userMsg, true, attachmentName=attachment?.displayName, attachmentInfo=attachment?.let { formatBytes(it.sizeBytes) }, attachmentContext=attachment?.contextForPrompt(), sourcePrompt=userMsg)
        val reply = Message(UUID.randomUUID().toString(), "Preparing local task…", false, status="Queued", sourcePrompt=userMsg)
        submitting = true
        updateComposerAction()
        lifecycleScope.launch {
            try {
                val previous = chatStore.messages(id)
                val context = previous.filter { it.imagePath == null }.takeLast(12).joinToString("\n") {
                    (if (it.isUser) "User: " else "Assistant: ") + it.content.take(2000) + (it.attachmentContext?.let { info -> "\n" + info.take(1500) } ?: "")
                }.takeLast(14000)
                val prompt = if (mode == AssistantMode.IMAGE) {
                    userMsg + (attachment?.extractedText?.take(1200)?.let { "\nVisual context: $it" } ?: "")
                } else buildString {
                    append("[NANU MODE: ${mode.label}]\n${mode.instruction}\n")
                    if (context.isNotBlank()) append("Previous conversation (context only):\n$context\n")
                    append("User request:\n$userMsg")
                    attachment?.let { append("\n" + it.contextForPrompt()) }
                }
                val request = JSONObject().put("prompt", prompt).put("image", mode == AssistantMode.IMAGE)
                    .put("model", currentModelFile?.absolutePath.orEmpty()).put("system", BASE_SYSTEM_PROMPT).toString()
                LocalTaskService.submit(applicationContext, id, user, reply, request, mode.id)
                if (fromVoice) voiceReplyId = reply.id
                userInputEt.setText("")
                currentAttachment = null
                refreshAttachmentUi()
                refreshHistoryMessages()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message ?: "Could not save chat", Toast.LENGTH_LONG).show()
            } finally { submitting = false; updateComposerAction() }
        }
    }

    private fun stopCurrentGeneration() {
        if (LocalTaskService.active.value) LocalTaskService.stop(applicationContext)
    }

    private fun regenerateMessage(message: Message) {
        val prompt = message.sourcePrompt ?: lastUserPrompt ?: return
        if (!message.imagePath.isNullOrBlank()) switchMode(AssistantMode.IMAGE)
        userInputEt.setText(prompt)
        userInputEt.setSelection(prompt.length)
        handleUserInput(false)
    }

    private fun editMessagePrompt(message: Message) {
        val prompt = message.sourcePrompt ?: return
        switchMode(AssistantMode.IMAGE)
        userInputEt.setText(prompt)
        userInputEt.setSelection(prompt.length)
        userInputEt.requestFocus()
    }

    private fun shareMessage(message: Message) {
        val imagePath = message.imagePath
        if (!imagePath.isNullOrBlank()) {
            val file = File(imagePath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message.content)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Nanu image"))
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message.content)
            }
            startActivity(Intent.createChooser(intent, "Share Nanu response"))
        }
    }

    private fun saveImageMessage(message: Message) {
        if (message.imagePath.isNullOrBlank()) return
        Toast.makeText(this, "Nanu automatically saves completed images to Pictures/Nanu when Android allows it.", Toast.LENGTH_LONG).show()
    }

    private fun imageModelReady(): Boolean {
        val file = imageModelManager.destinationFile(ImageModelCatalog.starter)
        return file.exists() && imageModelManager.looksLikeGguf(file)
    }

    private fun offerImageModelDownload() {
        val model = ImageModelCatalog.starter
        if (imageModelReady()) return
        val active = prefs.getLong(KEY_IMAGE_DOWNLOAD_ID, -1L)
        if (active > 0L) {
            Toast.makeText(this, "Image model download is already in progress.", Toast.LENGTH_LONG).show()
            monitorImageModelDownload(active)
            return
        }
        val free = StatFs(imageModelManager.downloadDirectory.absolutePath).availableBytes
        if (free < model.sizeBytes + 1024L * 1024L * 1024L) {
            Toast.makeText(this, "Keep at least about 3 GB free before downloading the image model.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Download local image model?")
            .setMessage("${model.name} • ${model.sizeLabel}\n\nIt downloads inside Nanu once. After that, image generation works locally.")
            .setPositiveButton("Download in Nanu") { _, _ ->
                val id = imageModelManager.enqueue(model)
                prefs.edit().putLong(KEY_IMAGE_DOWNLOAD_ID, id).apply()
                monitorImageModelDownload(id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resumeImageModelDownloadIfNeeded() {
        val id = prefs.getLong(KEY_IMAGE_DOWNLOAD_ID, -1L)
        if (id > 0L) monitorImageModelDownload(id)
    }

    private fun monitorImageModelDownload(id: Long) {
        imageDownloadJob?.cancel()
        imageDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = imageModelManager.query(id)
                if (snapshot == null) {
                    prefs.edit().remove(KEY_IMAGE_DOWNLOAD_ID).apply()
                    break
                }
                when (snapshot.status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        val total = if (snapshot.totalBytes > 0L) snapshot.totalBytes else ImageModelCatalog.starter.sizeBytes
                        val percent = if (total > 0L) ((snapshot.downloadedBytes * 100L) / total).coerceIn(0L, 100L) else 0L
                        withContext(Dispatchers.Main) { modelStatusTv.text = "Image model downloading • $percent%" }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = imageModelManager.destinationFile(ImageModelCatalog.starter)
                        val ok = imageModelManager.looksLikeGguf(file) && imageModelManager.verifySha256(file, ImageModelCatalog.starter.sha256)
                        prefs.edit().remove(KEY_IMAGE_DOWNLOAD_ID).apply()
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                modelStatusTv.text = "Image model ready • generation stays local"
                                Toast.makeText(this@MainActivity, "Image model ready. Your prompt is still in the message box.", Toast.LENGTH_LONG).show()
                            } else {
                                file.delete()
                                modelStatusTv.text = "Image model verification failed"
                            }
                        }
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().remove(KEY_IMAGE_DOWNLOAD_ID).apply()
                        withContext(Dispatchers.Main) { modelStatusTv.text = "Image model download failed (reason ${snapshot.reason})" }
                        break
                    }
                }
                delay(800L)
            }
        }
    }

    private fun restoreLastModelOrShowWelcome() {
        val path = prefs.getString(KEY_LAST_MODEL, null)
        val file = path?.let(::File)
        if (file != null && file.exists() && file.isFile) {
            lifecycleScope.launch { loadModelFile(file, file.nameWithoutExtension, announce = false) }
        } else {
            setModelUi(null, false, "No LLM loaded • tap Model to download one")
            showEmptyState(true, "Private local AI.\n\nTap Model at the top right to download a recommended LLM. Use + for General, Code, Academics, Trading, Create Image, or Attach File.")
        }
    }

    private fun storedModelFiles(): List<File> {
        val directories = listOf(ensureModelsDirectory(), modelDownloader.downloadDirectory)
        return directories.flatMap { directory ->
            directory.listFiles()?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }.orEmpty()
        }.distinctBy { it.absolutePath }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun showModelManager() {
        if (!engineReady) {
            Toast.makeText(this, "The local LLM engine is still starting.", Toast.LENGTH_SHORT).show()
            return
        }
        val files = storedModelFiles()
        val ramGb = totalDeviceRamGb()
        val best = ModelCatalog.bestForRam(ramGb, if (currentMode == AssistantMode.CODING) "coding" else null)
        val activeModel = activeDownloadModel()
        val labels = mutableListOf(
            "★ Recommended • ${best.name}",
            "Import your own GGUF"
        )
        if (activeModel != null) labels += "↓ Download in progress • ${activeModel.name}"
        val fileStart = labels.size
        labels += files.map { "${it.nameWithoutExtension} • ${formatBytes(it.length())}" }
        if (files.isNotEmpty()) labels += "Delete a stored model…"

        AlertDialog.Builder(this)
            .setTitle("Local models • ${String.format(Locale.US, "%.1f", ramGb)} GB RAM")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which == 0 -> showRecommendedModelCatalog(ramGb)
                    which == 1 -> openModelDocument.launch(arrayOf("*/*"))
                    activeModel != null && which == 2 -> showActiveDownload(activeModel)
                    which in fileStart until fileStart + files.size -> {
                        val file = files[which - fileStart]
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
        val best = ModelCatalog.bestForRam(ramGb, if (currentMode == AssistantMode.CODING) "coding" else null)
        val ordered = listOf(best) + ModelCatalog.models.filter { it.id != best.id }
        val labels = ordered.map { model ->
            val downloaded = modelDownloader.destinationFile(model).let { it.exists() && modelDownloader.looksLikeGguf(it) }
            val badge = when {
                downloaded -> "✓ DOWNLOADED"
                model.id == best.id -> "★ RECOMMENDED"
                ramGb + 0.25 >= model.minimumRamGb -> "✓ Compatible"
                else -> "⚠ ${model.minimumRamGb} GB+ suggested"
            }
            "$badge\n${model.name} • ${model.quant}\n${model.sizeLabel} • ${model.speedLabel} • ${model.useCase}"
        }
        AlertDialog.Builder(this)
            .setTitle("Choose a local model")
            .setMessage("Recommended models appear first. Tap one to see size, RAM, speed, and download it directly inside Nanu.")
            .setItems(labels.toTypedArray()) { _, which -> showModelSuggestionDetail(ordered[which], ramGb, best.id) }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showModelSuggestionDetail(model: RecommendedModel, ramGb: Double, bestId: String) {
        val target = modelDownloader.destinationFile(model)
        val alreadyDownloaded = target.exists() && modelDownloader.looksLikeGguf(target)
        val status = when {
            alreadyDownloaded -> "Already downloaded"
            model.id == bestId -> "Recommended for this device"
            ramGb + 0.25 >= model.minimumRamGb -> "Compatible with this device"
            else -> "May be too heavy for this device"
        }
        val message = "$status\n\nQuant: ${model.quant}\nSize: ${model.sizeLabel}\nRAM: ${model.minimumRamGb} GB+\nSpeed: ${model.speedLabel}\nBest for: ${model.useCase}\nLicense: ${model.licenseLabel}\n\n${model.notes}"
        AlertDialog.Builder(this)
            .setTitle(model.name)
            .setMessage(message)
            .setPositiveButton(if (alreadyDownloaded) "Load" else "Download in Nanu") { _, _ ->
                if (alreadyDownloaded) lifecycleScope.launch { loadModelFile(target, model.name) } else confirmModelDownload(model)
            }
            .setNeutralButton("Source / license") { _, _ -> openModelPage(model) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmModelDownload(model: RecommendedModel) {
        val active = activeDownloadModel()
        if (active != null) {
            showActiveDownload(active)
            return
        }
        val available = StatFs(modelDownloader.downloadDirectory.absolutePath).availableBytes
        if (available < model.sizeBytes + 512L * 1024L * 1024L) {
            Toast.makeText(this, "Not enough free storage for ${model.sizeLabel} plus working space.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Download ${model.name}?")
            .setMessage("${model.sizeLabel} • ${model.speedLabel}\n\nThe model downloads inside Nanu. Inference remains on-device after download.")
            .setPositiveButton("Download") { _, _ -> beginModelDownload(model) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun beginModelDownload(model: RecommendedModel) {
        runCatching {
            val id = modelDownloader.enqueue(model)
            prefs.edit().putLong(KEY_ACTIVE_DOWNLOAD_ID, id).putString(KEY_ACTIVE_DOWNLOAD_MODEL, model.id).apply()
            showDownloadProgressDialog(model, id)
            monitorModelDownload(model, id)
        }.onFailure { Toast.makeText(this, "Could not start download: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun activeDownloadModel(): RecommendedModel? {
        val id = prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)
        val modelId = prefs.getString(KEY_ACTIVE_DOWNLOAD_MODEL, null)
        if (id <= 0L || modelId == null) return null
        val snapshot = runCatching { modelDownloader.query(id) }.getOrNull()
        if (snapshot == null || snapshot.status == DownloadManager.STATUS_FAILED) {
            clearActiveDownload()
            return null
        }
        return ModelCatalog.models.firstOrNull { it.id == modelId }
    }

    private fun showActiveDownload(model: RecommendedModel) {
        val id = prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)
        if (id <= 0L) return
        showDownloadProgressDialog(model, id)
        if (downloadJob?.isActive != true) monitorModelDownload(model, id)
    }

    private fun resumePendingModelDownload() {
        val model = activeDownloadModel() ?: return
        val id = prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)
        if (id <= 0L) return
        monitorModelDownload(model, id)
    }

    private fun showDownloadProgressDialog(model: RecommendedModel, downloadId: Long) {
        downloadDialog?.dismiss()
        downloadDialog = AlertDialog.Builder(this)
            .setTitle("Downloading ${model.name}")
            .setMessage("Starting download…")
            .setPositiveButton("Hide", null)
            .setNegativeButton("Cancel") { _, _ ->
                modelDownloader.cancel(downloadId)
                clearActiveDownload()
                downloadJob?.cancel()
            }
            .create()
        downloadDialog?.show()
    }

    private fun monitorModelDownload(model: RecommendedModel, downloadId: Long) {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = modelDownloader.query(downloadId)
                if (snapshot == null) {
                    clearActiveDownload()
                    break
                }
                when (snapshot.status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        val total = if (snapshot.totalBytes > 0L) snapshot.totalBytes else model.sizeBytes
                        val percent = if (total > 0L) ((snapshot.downloadedBytes * 100L) / total).coerceIn(0L, 100L) else 0L
                        withContext(Dispatchers.Main) {
                            downloadDialog?.setMessage("Downloading • $percent%\n${formatBytes(snapshot.downloadedBytes)} / ${formatBytes(total)}")
                            modelStatusTv.text = "${model.name} downloading • $percent%"
                        }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = modelDownloader.destinationFile(model)
                        clearActiveDownload()
                        withContext(Dispatchers.Main) { downloadDialog?.dismiss() }
                        if (modelDownloader.looksLikeGguf(file)) loadModelFile(file, model.name)
                        else {
                            file.delete()
                            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Downloaded file is not a valid GGUF model.", Toast.LENGTH_LONG).show() }
                        }
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        clearActiveDownload()
                        withContext(Dispatchers.Main) {
                            downloadDialog?.dismiss()
                            modelStatusTv.text = "Model download failed (reason ${snapshot.reason})"
                        }
                        break
                    }
                }
                delay(750L)
            }
        }
    }

    private fun clearActiveDownload() {
        prefs.edit().remove(KEY_ACTIVE_DOWNLOAD_ID).remove(KEY_ACTIVE_DOWNLOAD_MODEL).apply()
    }

    private fun openModelPage(model: RecommendedModel) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.pageUrl))) }
            .onFailure { copyText(model.pageUrl) }
    }

    private fun showDeleteModelDialog(files: List<File>) {
        AlertDialog.Builder(this)
            .setTitle("Delete stored model")
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                val file = files[which]
                if (currentModelFile?.absolutePath == file.absolutePath && isModelReady) {
                    Toast.makeText(this, "Load another model before deleting the active model.", Toast.LENGTH_LONG).show()
                } else if (file.delete()) {
                    Toast.makeText(this, "Model deleted.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleSelectedModel(uri: Uri) {
        if (!engineReady) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { setBusyUi("Reading GGUF model…") }
                val metadata = contentResolver.openInputStream(uri)?.use { GgufMetadataReader.create().readStructuredMetadata(it) } ?: error("Unable to read selected file")
                val modelName = metadata.filename() + FILE_EXTENSION_GGUF
                val modelFile = contentResolver.openInputStream(uri)?.use { input -> ensureModelFile(modelName, input) } ?: error("Unable to import selected model")
                loadModelFile(modelFile, metadata.basic.name ?: modelFile.nameWithoutExtension)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setModelUi(null, false, "Model import failed")
                    Toast.makeText(this@MainActivity, "Could not import GGUF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun ensureModelFile(modelName: String, input: InputStream): File = withContext(Dispatchers.IO) {
        val safeName = modelName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(ensureModelsDirectory(), safeName)
        if (!file.exists()) FileOutputStream(file).use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        file
    }

    private suspend fun loadModelFile(file: File, displayName: String, announce: Boolean = true) {
        if (!engineReady) return
        withContext(Dispatchers.Main) { setBusyUi("Loading ${displayName.take(28)}…") }
        try {
            require(file.isFile) { "Selected model is no longer available" }
            currentModelFile = file
            currentModelDisplayName = displayName
            isModelReady = true
            prefs.edit().putString(KEY_LAST_MODEL, file.absolutePath).apply()
            withContext(Dispatchers.Main) {
                setModelUi(displayName, true, "Ready • local • ${formatBytes(file.length())}")
                if (announce) statsTv.text = ""
                showEmptyState(messages.isEmpty(), "Nanu is ready. Use + to switch mode, attach files, or create images.")
                updateComposerAction()
            }
        } catch (e: Exception) {
            isModelReady = false
            withContext(Dispatchers.Main) {
                setModelUi(displayName, false, "Model load failed")
                Toast.makeText(this@MainActivity, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startNewChat() {
        if (LocalTaskService.active.value || submitting) return
        lifecycleScope.launch { openConversation(chatStore.create(currentMode.id)) }
    }

    private fun stripThinking(raw: String): String {
        var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.replace("</think>", "").trimStart()
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nanu", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun setBusyUi(status: String) {
        modelStatusTv.text = status
        modelStatusTv.setTextColor(getColor(R.color.nanu_accent_2))
        modelsBtn.isEnabled = false
        newChatBtn.isEnabled = false
    }

    private fun setModelUi(name: String?, ready: Boolean, status: String) {
        modelStatusTv.text = status
        modelStatusTv.setTextColor(getColor(if (ready) R.color.nanu_success else R.color.nanu_muted))
        modelsBtn.text = if (name.isNullOrBlank()) "Model ▾" else "${compactModelName(name)} ▾"
        modelsBtn.isEnabled = true
        newChatBtn.isEnabled = true
    }

    private fun compactModelName(name: String): String {
        val cleaned = name.replace(Regex("(?i)instruct|q4_k_m|gguf"), "").replace(Regex("[-_]+"), " ").trim()
        return if (cleaned.length <= 18) cleaned else cleaned.take(16) + "…"
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
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun speakText(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "nanu_reply_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            return
        }
        tts?.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        tts?.setSpeechRate(0.96f)
        val candidates = listOf(Locale.getDefault(), Locale.US, Locale.UK).distinct()
        val selected = candidates.firstOrNull { (tts?.isLanguageAvailable(it) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE }
        ttsReady = selected != null && (tts?.setLanguage(selected) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = Unit
        })
    }

    override fun onDestroy() {
        generationJob?.cancel()
        downloadJob?.cancel()
        imageDownloadJob?.cancel()
        imageGenerator.cancel()
        recognizer?.cancel()
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }

    enum class AssistantMode(val id: String, val label: String, val instruction: String) {
        GENERAL("general", "General", "Answer clearly and naturally. Be useful, accurate, and concise unless more detail is needed."),
        CODING("coding", "Code", "Act as a practical coding assistant. Write, debug, review, and explain code. Prefer correct runnable examples and clear steps."),
        ACADEMICS("study", "Academics", "Teach and research carefully. Explain concepts step by step, use examples, and distinguish established facts from uncertainty."),
        TRADING("trading", "Trading", "Act as a disciplined market-analysis assistant for forex and crypto. Discuss price action, indicators, chart patterns, risk, position sizing, and scenarios. Never guarantee profit and clearly separate analysis from financial advice."),
        IMAGE("image", "Create Image", "Create or refine a concise visual description for local image generation.");

        companion object {
            fun fromId(id: String?): AssistantMode = entries.firstOrNull { it.id == id } ?: GENERAL
        }
    }

    companion object {
        private const val PREFS_NAME = "nanu_local_ai"
        private const val KEY_LAST_MODEL = "last_model"
        private const val KEY_MODE = "assistant_mode"
        private const val KEY_ACTIVE_DOWNLOAD_ID = "active_download_id"
        private const val KEY_ACTIVE_DOWNLOAD_MODEL = "active_download_model"
        private const val KEY_IMAGE_DOWNLOAD_ID = "active_image_model_download"
        private const val DIRECTORY_MODELS = "models"
        private const val FILE_EXTENSION_GGUF = ".gguf"
        private const val BASE_SYSTEM_PROMPT = "You are Nanu, a private on-device assistant. Follow the NANU MODE instruction included with each user request. Never reveal hidden chain-of-thought, private reasoning, or <think> blocks. Return only useful final answers."
    }
}

fun GgufMetadata.filename(): String = when {
    basic.name != null -> basic.name!!.let { name -> basic.sizeLabel?.let { "$name-$it" } ?: name }
    architecture?.architecture != null -> architecture!!.architecture!!.let { arch -> basic.uuid?.let { "$arch-$it" } ?: "$arch-${java.lang.Long.toHexString(System.currentTimeMillis())}" }
    else -> "model-${java.lang.Long.toHexString(System.currentTimeMillis())}"
}
