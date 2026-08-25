package com.example.llama

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
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

class ContinuousTalkActivity : NanuBaseActivity(), TextToSpeech.OnInitListener {
    private lateinit var statusTv: TextView
    private lateinit var heardTv: TextView
    private lateinit var answerTv: TextView
    private lateinit var talkBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var voiceBtn: MaterialButton
    private lateinit var continuousBtn: MaterialButton

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceReplies = true
    private var continuousMode = true
    private var loopArmed = false
    private var sessionActive = false
    private var preferOffline = true
    private lateinit var engine: InferenceEngine
    private var engineReady = false
    private var job: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startConversation()
        } else {
            sessionActive = false
            refreshTalkButton()
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_talk_rc8)

        statusTv = findViewById(R.id.talk8_status)
        heardTv = findViewById(R.id.talk8_heard)
        answerTv = findViewById(R.id.talk8_answer)
        talkBtn = findViewById(R.id.talk8_button)
        stopBtn = findViewById(R.id.talk8_stop)
        voiceBtn = findViewById(R.id.talk8_voice)
        continuousBtn = findViewById(R.id.talk8_continuous)

        findViewById<MaterialButton>(R.id.talk8_back).setOnClickListener { finish() }
        talkBtn.setOnClickListener {
            if (sessionActive) stopEverything() else requestMicAndStart()
        }
        stopBtn.setOnClickListener { stopEverything() }
        voiceBtn.setOnClickListener {
            voiceReplies = !voiceReplies
            voiceBtn.text = if (voiceReplies) "Voice reply: On" else "Voice reply: Off"
            if (!voiceReplies) {
                tts?.stop()
                if (sessionActive) {
                    if (loopArmed) scheduleListen(180L)
                    else finishSession("Voice reply stopped • tap Speak when ready")
                }
            }
        }
        continuousBtn.setOnClickListener {
            continuousMode = !continuousMode
            continuousBtn.text = if (continuousMode) "Continuous: On" else "Continuous: Off"
            loopArmed = sessionActive && continuousMode
        }

        refreshTalkButton()
        tts = TextToSpeech(this, this)
        createRecognizer()
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                engine = AiChat.getInferenceEngine(applicationContext)
                ensureModelReady()
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    statusTv.text = "Local AI unavailable: ${error.message}"
                    sessionActive = false
                    refreshTalkButton()
                }
            }
        }
    }

    private suspend fun ensureModelReady() {
        var state = waitForStableEngine()
        if (state is InferenceEngine.State.ModelReady) {
            engineReady = true
            withContext(Dispatchers.Main) { markReady("Reusing loaded local AI") }
            return
        }
        if (state is InferenceEngine.State.Error) {
            runCatching { engine.cleanUp() }
            state = waitForStableEngine(5_000L)
        }
        if (state !is InferenceEngine.State.Initialized) {
            withContext(Dispatchers.Main) {
                statusTv.text = "Local AI is busy in another screen."
                refreshTalkButton()
            }
            return
        }
        val model = getSharedPreferences("nanu_local_ai", MODE_PRIVATE)
            .getString("last_model", null)
            ?.let(::File)
            ?.takeIf { it.exists() }
        if (model == null) {
            withContext(Dispatchers.Main) {
                statusTv.text = "Load an LLM from Chat + Models first."
                refreshTalkButton()
            }
            return
        }
        withContext(Dispatchers.Main) { statusTv.text = "Loading ${model.nameWithoutExtension}…" }
        engine.loadModel(model.absolutePath)
        engine.setSystemPrompt("You are Nanu. Reply naturally and concisely for spoken conversation. Never reveal hidden chain-of-thought or <think> blocks.")
        engineReady = true
        withContext(Dispatchers.Main) { markReady("Local AI loaded") }
    }

    private suspend fun waitForStableEngine(timeoutMs: Long = 30_000L): InferenceEngine.State {
        var waited = 0L
        while (waited < timeoutMs) {
            val state = engine.state.value
            when (state) {
                is InferenceEngine.State.Uninitialized,
                is InferenceEngine.State.Initializing,
                is InferenceEngine.State.LoadingModel,
                is InferenceEngine.State.UnloadingModel,
                is InferenceEngine.State.ProcessingSystemPrompt,
                is InferenceEngine.State.ProcessingUserPrompt,
                is InferenceEngine.State.Generating,
                is InferenceEngine.State.Benchmarking -> {
                    delay(150L)
                    waited += 150L
                }
                else -> return state
            }
        }
        return engine.state.value
    }

    private fun markReady(prefix: String) {
        val speech = when {
            !SpeechRecognizer.isRecognitionAvailable(this) -> "speech unavailable"
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this) -> "on-device speech ready"
            else -> "speech service ready"
        }
        statusTv.text = "$prefix • $speech • ${if (ttsReady) "voice ready" else "voice starting"}"
        refreshTalkButton()
    }

    private fun refreshTalkButton() {
        if (!::talkBtn.isInitialized) return
        val canUse = engineReady && SpeechRecognizer.isRecognitionAvailable(this)
        talkBtn.isEnabled = canUse || sessionActive
        talkBtn.text = if (sessionActive) "Speak: ON • tap to stop" else "Speak"
        talkBtn.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (sessionActive) R.color.nanu_accent else R.color.nanu_panel_2)
        )
        talkBtn.setTextColor(
            ContextCompat.getColor(this, if (sessionActive) android.R.color.white else R.color.nanu_accent_2)
        )
        talkBtn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nanu_accent))
        talkBtn.strokeWidth = if (sessionActive) 0 else resources.displayMetrics.density.toInt().coerceAtLeast(1)
    }

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startConversation()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startConversation() {
        if (!engineReady || engine.state.value !is InferenceEngine.State.ModelReady) {
            Toast.makeText(this, "Local AI is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }
        sessionActive = true
        loopArmed = continuousMode
        refreshTalkButton()
        startListening(true)
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = runCatching {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        }.getOrElse { SpeechRecognizer.createSpeechRecognizer(this) }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!sessionActive) return
                statusTv.text = if (preferOffline) "Listening • offline preferred" else "Listening"
            }

            override fun onBeginningOfSpeech() {
                if (sessionActive) statusTv.text = "Listening…"
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (sessionActive) statusTv.text = "Processing speech…"
            }

            override fun onError(error: Int) {
                if (!sessionActive) return
                if (preferOffline && (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)) {
                    preferOffline = false
                    lifecycleScope.launch {
                        delay(250L)
                        if (sessionActive) startListening(false)
                    }
                    return
                }

                statusTv.text = speechError(error)
                val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (loopArmed && recoverable) {
                    scheduleListen(650L)
                } else {
                    finishSession("${speechError(error)} • tap Speak to try again")
                }
            }

            override fun onResults(results: Bundle?) {
                if (!sessionActive) return
                preferOffline = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    statusTv.text = "I didn't catch that."
                    if (loopArmed) scheduleListen(500L)
                    else finishSession("I didn't catch that • tap Speak to try again")
                    return
                }
                heardTv.text = text
                askNanu(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!sessionActive) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { heardTv.text = it }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startListening(offline: Boolean) {
        if (!sessionActive || !engineReady || engine.state.value !is InferenceEngine.State.ModelReady) return
        preferOffline = offline
        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure {
                if (sessionActive) finishSession("Speech recognition could not start • tap Speak to retry")
            }
    }

    private fun askNanu(prompt: String) {
        if (!sessionActive) return
        answerTv.text = "Thinking locally…"
        statusTv.text = "Nanu is thinking…"
        val raw = StringBuilder()
        job?.cancel()
        job = lifecycleScope.launch(Dispatchers.Default) {
            engine.sendUserPrompt(prompt)
                .catch { error ->
                    if (sessionActive) {
                        withContext(Dispatchers.Main) {
                            answerTv.text = "Generation error: ${error.message}"
                            finishSession("Generation stopped • tap Speak to try again")
                        }
                    }
                }
                .collect { token ->
                    raw.append(token)
                    if (sessionActive) {
                        withContext(Dispatchers.Main) {
                            answerTv.text = stripThinking(raw.toString()).ifBlank { "Thinking locally…" }
                        }
                    }
                }

            if (!sessionActive) return@launch
            val finalText = stripThinking(raw.toString()).trim()
            withContext(Dispatchers.Main) {
                if (!sessionActive) return@withContext
                if (finalText.isNotBlank() && voiceReplies && ttsReady) {
                    statusTv.text = "Speaking… • tap Speak to stop"
                    tts?.speak(finalText.take(4000), TextToSpeech.QUEUE_FLUSH, null, REPLY_UTTERANCE)
                } else if (loopArmed) {
                    statusTv.text = "Reply ready • listening again…"
                    scheduleListen(450L)
                } else {
                    finishSession("Reply ready • tap Speak when ready")
                }
            }
        }
    }

    private fun scheduleListen(delayMs: Long) {
        lifecycleScope.launch {
            delay(delayMs)
            if (sessionActive && loopArmed && continuousMode && engineReady && engine.state.value is InferenceEngine.State.ModelReady) {
                startListening(true)
            }
        }
    }

    private fun finishSession(message: String) {
        sessionActive = false
        loopArmed = false
        refreshTalkButton()
        statusTv.text = message
    }

    private fun stopEverything() {
        sessionActive = false
        loopArmed = false
        recognizer?.cancel()
        job?.cancel()
        job = null
        tts?.stop()
        refreshTalkButton()
        statusTv.text = "Stopped • tap Speak when ready"
    }

    private fun stripThinking(raw: String): String {
        var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.replace("</think>", "").trimStart()
    }

    private fun speechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
        else -> "Speech recognition stopped (code $code)"
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            return
        }
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts?.setSpeechRate(0.96f)
        val selected = listOf(Locale.getDefault(), Locale.US, Locale.UK).distinct().firstOrNull {
            (tts?.isLanguageAvailable(it) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        }
        ttsReady = selected != null &&
            (tts?.setLanguage(selected) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != REPLY_UTTERANCE) return
                runOnUiThread {
                    if (!sessionActive) return@runOnUiThread
                    if (loopArmed) scheduleListen(350L)
                    else finishSession("Reply finished • tap Speak when ready")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != REPLY_UTTERANCE) return
                runOnUiThread {
                    if (!sessionActive) return@runOnUiThread
                    if (loopArmed) scheduleListen(500L)
                    else finishSession("Voice stopped • tap Speak when ready")
                }
            }
        })
        if (engineReady) markReady("Local AI ready")
    }

    override fun onDestroy() {
        sessionActive = false
        loopArmed = false
        job?.cancel()
        recognizer?.cancel()
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REPLY_UTTERANCE = "nanu_rc8_reply"
    }
}
