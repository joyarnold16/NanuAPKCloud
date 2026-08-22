package com.example.llama

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakAnswers = true
    private lateinit var engine: InferenceEngine
    private var engineReady = false
    private var generationJob: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else Toast.makeText(this, "Microphone permission is required for Talk to Nanu.", Toast.LENGTH_LONG).show()
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

        findViewById<MaterialButton>(R.id.talk_back).setOnClickListener { finish() }
        talkBtn.setOnClickListener { requestOrStartListening() }
        stopBtn.setOnClickListener { stopEverything() }
        speakToggleBtn.setOnClickListener {
            speakAnswers = !speakAnswers
            speakToggleBtn.text = if (speakAnswers) "Voice reply: On" else "Voice reply: Off"
            if (!speakAnswers) tts?.stop()
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
            statusTv.text = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this@TalkActivity)) {
                "Ready • on-device speech available"
            } else {
                "Ready • offline speech preferred when supported"
            }
            talkBtn.isEnabled = true
        }
    }

    private fun createSpeechRecognizer() {
        recognizer?.destroy()
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
                statusTv.text = "Listening…"
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
                statusTv.text = speechError(error)
            }

            override fun onResults(results: Bundle?) {
                talkBtn.text = "Tap to talk"
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
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!engineReady) {
            Toast.makeText(this, "Load a local LLM first or wait for it to finish loading.", Toast.LENGTH_LONG).show()
            return
        }
        generationJob?.cancel()
        tts?.stop()
        answerTv.text = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
                talkBtn.isEnabled = true
                talkBtn.text = "Tap to talk"
                statusTv.text = "Ready"
                if (finalText.isNotBlank() && speakAnswers && ttsReady) {
                    tts?.speak(finalText, TextToSpeech.QUEUE_FLUSH, null, "nanu_voice_reply")
                }
            }
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
        talkBtn.isEnabled = engineReady
        talkBtn.text = "Tap to talk"
        statusTv.text = if (engineReady) "Ready" else "Waiting for local AI"
    }

    private fun speechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Offline speech service unavailable"
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition stopped (code $code)"
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.0f)
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
