package com.example.llama

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class TalkActivity : NanuBaseActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusTv: TextView
    private lateinit var heardTv: TextView
    private lateinit var answerTv: TextView
    private lateinit var talkBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var speakToggleBtn: MaterialButton
    private lateinit var testVoiceBtn: MaterialButton

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakAnswers = true
    private var preferOfflineSpeech = true
    private lateinit var engine: InferenceEngine
    private var engineReady = false
    private var generationJob: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening(preferOffline = true)
        else Toast.makeText(this, "Microphone permission is required for Talk to Nanu.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_talk)

        statusTv = findViewById(R.id.talk_status)
        heardTv = findViewById(R.id.talk_heard)
        answerTv = findViewById(R.id.talk_answer)
        talkBtn = findViewById(R.id.talk_button)
        stopBtn = findViewById(R.id.talk_stop_button)
        speakToggleBtn = findViewById(R.id.speak_toggle_button)
        testVoiceBtn = findViewById(R.id.test_voice_button)

        findViewById<MaterialButton>(R.id.talk_back).setOnClickListener { finish() }
        talkBtn.setOnClickListener { requestOrStartListening() }
        stopBtn.setOnClickListener { stopEverything() }
        speakToggleBtn.setOnClickListener {
            speakAnswers = !speakAnswers
            speakToggleBtn.text = if (speakAnswers) "Voice reply: On" else "Voice reply: Off"
            if (!speakAnswers) tts?.stop()
        }
        testVoiceBtn.setOnClickListener {
            if (ttsReady) {
                speakText("Nanu voice is working.", "nanu_voice_test")
            } else {
                statusTv.text = "Voice engine is not ready. Check Android Text-to-speech settings."
                Toast.makeText(this, "Text-to-speech engine or voice data is unavailable.", Toast.LENGTH_LONG).show()
            }
        }

        tts = TextToSpeech(this, this)
        createSpeechRecognizer()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                ensureModelReady()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTv.text = "Local AI unavailable"
                    answerTv.text = e.message ?: "Could not start the local AI engine."
                }
            }
        }
    }

    private suspend fun ensureModelReady() {
        val prefs = getSharedPreferences("nanu_local_ai", MODE_PRIVATE)
        val path = prefs.getString("last_model", null)
        val model = path?.let(::File)

        if (engine.state.value !is InferenceEngine.State.ModelReady) {
            if (model == null || !model.exists()) {
                withContext(Dispatchers.Main) {
                    statusTv.text = "Load an LLM first"
                    answerTv.text = "Open Models in Nanu, download a recommended LLM, then return to Talk."
                }
                return
            }
            withContext(Dispatchers.Main) { statusTv.text = "Loading ${model.nameWithoutExtension}…" }
            engine.loadModel(model.absolutePath)
        }

        engine.setSystemPrompt(
            "You are Nanu in voice conversation mode. Answer naturally and concisely for spoken conversation. " +
                "Do not reveal hidden chain-of-thought or <think> blocks."
        )
        engineReady = true
        withContext(Dispatchers.Main) {
            refreshReadyStatus()
            talkBtn.isEnabled = SpeechRecognizer.isRecognitionAvailable(this@TalkActivity)
        }
    }

    private fun refreshReadyStatus() {
        if (!engineReady) return
        val speech = when {
            !SpeechRecognizer.isRecognitionAvailable(this) -> "speech recognizer unavailable"
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this) -> "on-device speech ready"
            else -> "speech service ready"
        }
        val voice = if (ttsReady) "voice ready" else "voice unavailable"
        statusTv.text = "Ready • $speech • $voice"
    }

    private fun createSpeechRecognizer() {
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusTv.text = "Android speech recognition is not available on this device."
            talkBtn.isEnabled = false
            return
        }

        recognizer = runCatching {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        }.getOrElse {
            SpeechRecognizer.createSpeechRecognizer(this)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusTv.text = if (preferOfflineSpeech) "Listening • offline preferred" else "Listening"
                talkBtn.text = "Listening"
            }

            override fun onBeginningOfSpeech() {
                statusTv.text = "Listening…"
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                statusTv.text = "Processing speech…"
            }

            override fun onError(error: Int) {
                talkBtn.text = "Tap to talk"
                if (preferOfflineSpeech && (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)) {
                    preferOfflineSpeech = false
                    statusTv.text = "Offline speech unavailable • trying installed speech service…"
                    lifecycleScope.launch {
                        delay(250L)
                        startListening(preferOffline = false)
                    }
                    return
                }
                statusTv.text = speechError(error)
            }

            override fun onResults(results: Bundle?) {
                talkBtn.text = "Tap to talk"
                preferOfflineSpeech = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    statusTv.text = "I didn't catch that. Try again."
                    return
                }
                heardTv.text = text
                askNanu(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) heardTv.text = text
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun requestOrStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening(preferOffline = true)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening(preferOffline: Boolean) {
        if (!engineReady) {
            Toast.makeText(this, "Load a local LLM first or wait for it to finish loading.", Toast.LENGTH_LONG).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusTv.text = "Speech recognition is unavailable. Install or enable an Android speech service."
            return
        }

        preferOfflineSpeech = preferOffline
        generationJob?.cancel()
        tts?.stop()
        answerTv.text = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure {
                statusTv.text = "Speech recognition could not start"
                Toast.makeText(this, it.message ?: "Speech service unavailable", Toast.LENGTH_LONG).show()
            }
    }

    private fun askNanu(prompt: String) {
        if (!engineReady) return
        answerTv.text = "Thinking locally…"
        statusTv.text = "Nanu is thinking…"
        talkBtn.isEnabled = false
        val raw = StringBuilder()

        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            engine.sendUserPrompt(prompt)
                .catch { error ->
                    withContext(Dispatchers.Main) {
                        answerTv.text = "Generation error: ${error.message ?: "unknown error"}"
                        statusTv.text = "Generation failed"
                    }
                }
                .collect { token ->
                    raw.append(token)
                    val visible = stripThinking(raw.toString()).ifBlank { "Thinking locally…" }
                    withContext(Dispatchers.Main) { answerTv.text = visible }
                }

            val finalText = stripThinking(raw.toString()).trim()
            withContext(Dispatchers.Main) {
                talkBtn.isEnabled = SpeechRecognizer.isRecognitionAvailable(this@TalkActivity)
                talkBtn.text = "Tap to talk"
                if (finalText.isNotBlank() && speakAnswers) {
                    if (ttsReady) speakText(finalText, "nanu_voice_reply")
                    else statusTv.text = "Reply ready • voice engine unavailable"
                } else {
                    refreshReadyStatus()
                }
            }
        }
    }

    private fun speakText(text: String, utteranceId: String) {
        if (!ttsReady) return
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            statusTv.text = "Text-to-speech could not start"
        } else {
            statusTv.text = "Speaking…"
        }
    }

    private fun stripThinking(raw: String): String {
        var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.replace("</think>", "").trimStart()
    }

    private fun stopEverything() {
        runCatching { recognizer?.stopListening() }
        generationJob?.cancel()
        tts?.stop()
        talkBtn.isEnabled = engineReady && SpeechRecognizer.isRecognitionAvailable(this)
        talkBtn.text = "Tap to talk"
        if (engineReady) refreshReadyStatus() else statusTv.text = "Waiting for local AI"
    }

    private fun speechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service unavailable offline and online"
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech service disconnected. Try again."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Current speech language is unavailable"
        else -> "Speech recognition stopped (code $code)"
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            testVoiceBtn.isEnabled = false
            speakToggleBtn.text = "Voice unavailable"
            statusTv.text = "Android Text-to-speech engine failed to start"
            return
        }

        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts?.setSpeechRate(0.96f)
        tts?.setPitch(1.0f)

        val candidates = listOf(Locale.getDefault(), Locale.US, Locale.UK).distinct()
        val selected = candidates.firstOrNull { locale ->
            val availability = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            availability >= TextToSpeech.LANG_AVAILABLE
        }

        if (selected == null) {
            ttsReady = false
            testVoiceBtn.isEnabled = false
            speakToggleBtn.text = "Voice data missing"
            statusTv.text = "No compatible Text-to-speech voice is installed"
            return
        }

        val languageResult = tts?.setLanguage(selected) ?: TextToSpeech.LANG_NOT_SUPPORTED
        ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        testVoiceBtn.isEnabled = ttsReady
        speakToggleBtn.isEnabled = ttsReady

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { statusTv.text = "Speaking…" }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread { refreshReadyStatus() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { statusTv.text = "Text-to-speech playback failed" }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread { statusTv.text = "Text-to-speech error $errorCode" }
            }
        })

        if (ttsReady) {
            testVoiceBtn.text = "Test voice"
            refreshReadyStatus()
        } else {
            testVoiceBtn.isEnabled = false
            speakToggleBtn.text = "Voice unavailable"
            statusTv.text = "Text-to-speech language is not supported"
        }
    }

    override fun onDestroy() {
        generationJob?.cancel()
        recognizer?.cancel()
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
