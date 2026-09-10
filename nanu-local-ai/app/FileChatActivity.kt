package com.example.llama

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileChatActivity : AppCompatActivity() {
    private lateinit var fileNameTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var answerTv: TextView
    private lateinit var questionEt: EditText
    private lateinit var askBtn: MaterialButton

    private val attachmentManager by lazy { AttachmentManager(applicationContext) }
    private val prefs by lazy { getSharedPreferences("nanu_local_ai", MODE_PRIVATE) }
    private val historyPrefs by lazy { getSharedPreferences("nanu_file_chat", MODE_PRIVATE) }
    private var attachment: NanuAttachment? = null
    private lateinit var engine: InferenceEngine
    private var engineReady = false
    private var job: Job? = null
    private val taskSession = TaskScreenSession(this, "files_conversation") { rows ->
        if (rows.isEmpty()) answerTv.text = ""
        rows.lastOrNull { !it.isUser }?.let { answerTv.text = it.content; statusTv.text = it.status }
        askBtn.isEnabled = !LocalTaskService.active.value && engineReady
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        statusTv.text = "Reading file locally…"
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { attachmentManager.import(uri) }
                .onSuccess { imported ->
                    withContext(Dispatchers.Main) {
                        attachment = imported
                        fileNameTv.text = "${imported.displayName} • ${formatBytes(imported.sizeBytes)}"
                        statusTv.text = if (imported.extractedText.isNullOrBlank()) {
                            "File attached, but readable text could not be extracted."
                        } else {
                            "Ready • ${imported.extractedText!!.length} characters indexed locally"
                        }
                        askBtn.isEnabled = engineReady && !imported.extractedText.isNullOrBlank()
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        statusTv.text = "Could not read file: ${error.message}"
                        Toast.makeText(this@FileChatActivity, "File import failed.", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_file_chat)

        fileNameTv = findViewById(R.id.file_chat_name)
        statusTv = findViewById(R.id.file_chat_status)
        answerTv = findViewById(R.id.file_chat_answer)
        questionEt = findViewById(R.id.file_chat_question)
        askBtn = findViewById(R.id.file_chat_ask)

        findViewById<MaterialButton>(R.id.file_chat_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.file_chat_pick).setOnClickListener {
            picker.launch(
                arrayOf(
                    "application/pdf", "text/*",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            )
        }
        askBtn.setOnClickListener { askCurrentFile() }
        findViewById<MaterialButton>(R.id.file_chat_history).setOnClickListener { showHistory() }
        findViewById<MaterialButton>(R.id.file_chat_report).setOnClickListener {
            startActivity(Intent(this, SafetyPrivacyActivity::class.java).putExtra(SafetyPrivacyActivity.EXTRA_REPORTED_CONTENT, answerTv.text.toString().take(5000)))
        }

        engineReady = prefs.getString("last_model", null)?.let { File(it).isFile } == true
        statusTv.text = if (engineReady) "Local AI ready • choose a document" else "Choose a model in Chat first."
        taskSession.observe()
    }

    private fun askCurrentFile() {
        val doc = attachment ?: return
        val question = questionEt.text.toString().trim()
        if (question.isBlank()) {
            Toast.makeText(this, "Enter a question about the file.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!engineReady) return
        taskSession.submit(question, "You are Nanu's private Ask My Files assistant. Answer using the supplied document. Cite section/page wording when available. If the document does not contain the answer, say so. Never reveal hidden chain-of-thought." + SafetyGuard.SYSTEM_RULES, attachment=doc)
    }

    private fun saveHistory(file: String, question: String, answer: String) {
        val rows = loadHistory()
        rows.put(JSONObject().put("file", file).put("question", question).put("answer", answer.take(5000)).put("time", System.currentTimeMillis()))
        val trimmed = JSONArray()
        val start = (rows.length() - 12).coerceAtLeast(0)
        for (i in start until rows.length()) trimmed.put(rows.getJSONObject(i))
        historyPrefs.edit().putString("history", trimmed.toString()).apply()
    }

    private fun showHistory() {
        android.app.AlertDialog.Builder(this).setTitle("File chat history").setItems(arrayOf("All saved conversations", "Older RC8 file answers")) { _, item ->
            if (item == 0) startActivity(Intent(this, MainActivity::class.java)) else showLegacyHistory()
        }.show()
    }

    private fun showLegacyHistory() {
        val rows = loadHistory()
        if (rows.length() == 0) {
            Toast.makeText(this, "No Ask My Files history yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = (rows.length() - 1 downTo 0).map { index ->
            val row = rows.getJSONObject(index)
            "${row.optString("file")}\n${row.optString("question").take(80)}"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Recent file questions")
            .setItems(labels) { _, which ->
                val index = rows.length() - 1 - which
                val row = rows.getJSONObject(index)
                questionEt.setText(row.optString("question"))
                answerTv.text = row.optString("answer")
                statusTv.text = "History • ${row.optString("file")}"
            }
            .setNeutralButton("Clear") { _, _ -> historyPrefs.edit().remove("history").apply() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadHistory(): JSONArray = runCatching { JSONArray(historyPrefs.getString("history", "[]")) }.getOrDefault(JSONArray())

    private fun stripThinking(raw: String): String {
        var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.replace("</think>", "").trimStart()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
