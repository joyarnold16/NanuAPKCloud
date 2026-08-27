#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("nanu-local-ai/app/MainActivity.kt")
text = path.read_text()

# Message-level Speak is a real toggle. MessageAdapter owns the visual state;
# MainActivity owns TextToSpeech lifecycle and interruption.
if "onSpeak = ::toggleSpeakMessage" not in text:
    old = "            onSpeak = ::speakText,"
    if old not in text:
        raise SystemExit("Could not locate MessageAdapter onSpeak binding")
    text = text.replace(old, "            onSpeak = ::toggleSpeakMessage,", 1)

if "onReport = ::reportMessage" not in text:
    old = "            onSaveImage = ::saveImageMessage\n        )"
    if old not in text:
        raise SystemExit("Could not locate MessageAdapter report binding anchor")
    text = text.replace(old, "            onSaveImage = ::saveImageMessage,\n            onReport = ::reportMessage\n        )", 1)

if "private var speakingMessageId: String?" not in text:
    old = "    private var lastUserPrompt: String? = null\n"
    if old not in text:
        raise SystemExit("Could not locate MainActivity message state anchor")
    text = text.replace(old, old + "    private var speakingMessageId: String? = null\n", 1)

# Put every downloadable model directly in the first Model dialog. Downloaded
# catalog models load from the same row; custom imported GGUF files stay below.
# Do NOT combine setMessage() with setItems() here: on some Samsung/Material
# AlertDialog implementations the message view replaces/suppresses the list.
model_manager = r'''    private fun showModelManager() {
        if (!engineReady) {
            Toast.makeText(this, "The local LLM engine is still starting.", Toast.LENGTH_SHORT).show()
            return
        }

        val ramGb = totalDeviceRamGb()
        val best = ModelCatalog.bestForRam(ramGb, if (currentMode == AssistantMode.CODING) "coding" else null)
        val activeModel = activeDownloadModel()
        val ordered = listOf(best) + ModelCatalog.models.filter { it.id != best.id }
        val catalogPaths = ModelCatalog.models.map { modelDownloader.destinationFile(it).absolutePath }.toSet()
        val allFiles = storedModelFiles()
        val customFiles = allFiles.filterNot { it.absolutePath in catalogPaths }

        val labels = ordered.map { model ->
            val downloaded = modelDownloader.destinationFile(model).let { it.exists() && modelDownloader.looksLikeGguf(it) }
            val role = when (model.id) {
                "qwen3-1.7b-q4km" -> "Everyday • fast balance"
                "qwen2.5-coder-1.5b-q4km" -> "Coding • fast"
                "qwen3-4b-q4km" -> "Better quality • slower"
                "gemma3-1b-q4km" -> "Lightweight • very fast"
                "qwen3-8b-q4km" -> "Advanced • heavy / slow"
                else -> model.useCase
            }
            val badge = when {
                activeModel?.id == model.id -> "↓ DOWNLOADING"
                downloaded -> "✓ DOWNLOADED"
                model.id == best.id -> "★ RECOMMENDED"
                ramGb + 0.25 >= model.minimumRamGb -> "○ AVAILABLE"
                else -> "⚠ HEAVY FOR THIS DEVICE"
            }
            "$badge • $role\n${model.name} • ${model.sizeLabel} • ${model.minimumRamGb} GB+ RAM"
        }.toMutableList()

        val importIndex = labels.size
        labels += "Import your own GGUF"
        val customStart = labels.size
        labels += customFiles.map { "Custom • ${it.nameWithoutExtension} • ${formatBytes(it.length())}" }
        val deleteIndex = if (allFiles.isNotEmpty()) labels.size else -1
        if (deleteIndex >= 0) labels += "Delete a stored model…"

        AlertDialog.Builder(this)
            .setTitle("Local models • ${String.format(Locale.US, "%.1f", ramGb)} GB RAM")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < ordered.size -> {
                        val model = ordered[which]
                        if (activeModel?.id == model.id) showActiveDownload(model)
                        else showModelSuggestionDetail(model, ramGb, best.id)
                    }
                    which == importIndex -> openModelDocument.launch(arrayOf("*/*"))
                    which in customStart until customStart + customFiles.size -> {
                        val file = customFiles[which - customStart]
                        lifecycleScope.launch { loadModelFile(file, file.nameWithoutExtension) }
                    }
                    deleteIndex >= 0 && which == deleteIndex -> showDeleteModelDialog(allFiles)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
'''

pattern = r"    private fun showModelManager\(\) \{.*?\n    \}\n\n    private fun totalDeviceRamGb"
replacement = model_manager + "\n    private fun totalDeviceRamGb"
text, count = re.subn(pattern, lambda _: replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit(f"Could not patch MainActivity model manager; matches={count}")

if "private fun reportMessage(message: Message)" not in text:
    anchor = "    private fun shareMessage(message: Message) {\n"
    if anchor not in text:
        raise SystemExit("Could not locate MainActivity report method anchor")
    report_method = r'''    private fun reportMessage(message: Message) {
        val reportText = buildString {
            append(message.content.take(8000))
            message.sourcePrompt?.takeIf { it.isNotBlank() }?.let {
                append("\n\nUser prompt:\n")
                append(it.take(2000))
            }
            if (!message.imagePath.isNullOrBlank()) append("\n\nThis response included a locally generated image.")
        }
        startActivity(Intent(this, SafetyPrivacyActivity::class.java).apply {
            putExtra(SafetyPrivacyActivity.EXTRA_REPORTED_CONTENT, reportText)
        })
    }

'''
    text = text.replace(anchor, report_method + anchor, 1)

if "private fun toggleSpeakMessage(message: Message)" not in text:
    anchor = "    private fun speakText(text: String) {\n"
    if anchor not in text:
        raise SystemExit("Could not locate MainActivity speakText")
    addition = r'''    private fun toggleSpeakMessage(message: Message) {
        if (!ttsReady || message.content.isBlank()) {
            Toast.makeText(this, "Voice is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        if (speakingMessageId == message.id && tts?.isSpeaking == true) {
            tts?.stop()
            clearSpeakingMessageUi()
            return
        }

        tts?.stop()
        speakingMessageId = message.id
        messageAdapter.setSpeakingMessage(message.id)
        val result = tts?.speak(
            message.content.take(3500),
            TextToSpeech.QUEUE_FLUSH,
            null,
            MESSAGE_SPEAK_PREFIX + message.id
        ) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) clearSpeakingMessageUi()
    }

    private fun clearSpeakingMessageUi() {
        speakingMessageId = null
        if (::messageAdapter.isInitialized) messageAdapter.setSpeakingMessage(null)
    }

'''
    text = text.replace(anchor, addition + anchor, 1)

old_speak = '''    private fun speakText(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "nanu_reply_${System.currentTimeMillis()}")
    }
'''
new_speak = '''    private fun speakText(text: String) {
        if (!ttsReady || text.isBlank()) return
        clearSpeakingMessageUi()
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "nanu_reply_${System.currentTimeMillis()}")
    }
'''
if old_speak in text:
    text = text.replace(old_speak, new_speak, 1)

old_listener = '''        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = Unit
        })
'''
new_listener = '''        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith(MESSAGE_SPEAK_PREFIX) == true) {
                    runOnUiThread { clearSpeakingMessageUi() }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith(MESSAGE_SPEAK_PREFIX) == true) {
                    runOnUiThread { clearSpeakingMessageUi() }
                }
            }
        })
'''
if old_listener in text:
    text = text.replace(old_listener, new_listener, 1)
elif "startsWith(MESSAGE_SPEAK_PREFIX)" not in text:
    raise SystemExit("Could not patch TTS utterance listener")

if "MESSAGE_SPEAK_PREFIX" not in text.split("companion object {", 1)[-1]:
    anchor = '        private const val BASE_SYSTEM_PROMPT = '
    idx = text.find(anchor)
    if idx < 0:
        raise SystemExit("Could not locate MainActivity companion constants")
    text = text[:idx] + '        private const val MESSAGE_SPEAK_PREFIX = "nanu_message_"\n' + text[idx:]

for marker in [
    "onSpeak = ::toggleSpeakMessage",
    "onReport = ::reportMessage",
    "Everyday • fast balance",
    ".setItems(labels.toTypedArray())",
    "private fun reportMessage(message: Message)",
    "private fun toggleSpeakMessage(message: Message)",
    "messageAdapter.setSpeakingMessage(message.id)",
    "MESSAGE_SPEAK_PREFIX",
]:
    if marker not in text:
        raise SystemExit(f"RC8 MainActivity patch missing marker: {marker}")

path.write_text(text)
print("RC8 MainActivity model picker + Speak toggle + Report action patch applied.")
