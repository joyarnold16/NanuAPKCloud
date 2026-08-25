package com.example.llama

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafetyPrivacyActivity : AppCompatActivity() {
    private lateinit var categoryEt: EditText
    private lateinit var detailsEt: EditText
    private lateinit var reportStatusTv: TextView
    private lateinit var submitBtn: MaterialButton
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val reportClient by lazy { AiReportClient(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_safety_privacy)

        categoryEt = findViewById(R.id.report_category)
        detailsEt = findViewById(R.id.report_details)
        reportStatusTv = findViewById(R.id.report_status)
        submitBtn = findViewById(R.id.submit_report_button)
        intent.getStringExtra(EXTRA_REPORTED_CONTENT)?.takeIf { it.isNotBlank() }?.let {
            detailsEt.setText("Reported AI output:\n$it")
        }

        findViewById<MaterialButton>(R.id.safety_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.privacy_policy_button).setOnClickListener { openUrl(PRIVACY_URL) }
        findViewById<MaterialButton>(R.id.terms_button).setOnClickListener { openUrl(TERMS_URL) }
        submitBtn.setOnClickListener { submitReport() }
        findViewById<MaterialButton>(R.id.save_report_button).setOnClickListener { saveReport() }
        findViewById<MaterialButton>(R.id.export_report_button).setOnClickListener { exportLatestReport() }
        findViewById<MaterialButton>(R.id.clear_reports_button).setOnClickListener {
            prefs.edit().remove(KEY_REPORTS).apply()
            refreshReportStatus()
            Toast.makeText(this, "Local safety reports cleared.", Toast.LENGTH_SHORT).show()
        }
        refreshReportStatus()
    }

    private fun currentReport(): Pair<String, String>? {
        val category = categoryEt.text.toString().trim().ifBlank { "AI output" }
        val details = detailsEt.text.toString().trim()
        if (details.isBlank()) {
            Toast.makeText(this, "Describe what should be reported.", Toast.LENGTH_SHORT).show()
            return null
        }
        return category.take(80) to details.take(8000)
    }

    private fun submitReport() {
        val (category, details) = currentReport() ?: return
        if (!reportClient.isConfigured()) {
            reportStatusTv.text = "Developer reporting endpoint is not configured in this build."
            Toast.makeText(this, "This build cannot submit reports to the developer yet.", Toast.LENGTH_LONG).show()
            return
        }

        submitBtn.isEnabled = false
        submitBtn.text = "Submitting…"
        reportStatusTv.text = "Sending report securely…"
        lifecycleScope.launch(Dispatchers.IO) {
            val result = reportClient.submit(category, details)
            withContext(Dispatchers.Main) {
                submitBtn.isEnabled = true
                submitBtn.text = "Submit to developer"
                result.onSuccess { reportId ->
                    appendLocalReport(category, details, submitted = true, reportId = reportId)
                    detailsEt.text = null
                    reportStatusTv.text = "Submitted to developer • reference ${reportId.take(8)}"
                    Toast.makeText(this@SafetyPrivacyActivity, "Report submitted. Thank you.", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    reportStatusTv.text = "Submission failed • ${error.message ?: "network error"}"
                    Toast.makeText(this@SafetyPrivacyActivity, "Could not submit report. You can try again or save a local copy.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveReport() {
        val (category, details) = currentReport() ?: return
        appendLocalReport(category, details, submitted = false, reportId = null)
        detailsEt.text = null
        refreshReportStatus()
        Toast.makeText(this, "Safety report saved locally.", Toast.LENGTH_LONG).show()
    }

    private fun appendLocalReport(category: String, details: String, submitted: Boolean, reportId: String?) {
        var rows = loadReports()
        rows.put(
            JSONObject()
                .put("time", System.currentTimeMillis())
                .put("category", category.take(80))
                .put("details", details.take(8000))
                .put("submitted", submitted)
                .put("report_id", reportId ?: JSONObject.NULL)
        )
        if (rows.length() > 50) {
            val trimmed = JSONArray()
            for (i in rows.length() - 50 until rows.length()) trimmed.put(rows.get(i))
            rows = trimmed
        }
        prefs.edit().putString(KEY_REPORTS, rows.toString()).apply()
    }

    private fun exportLatestReport() {
        val rows = loadReports()
        if (rows.length() == 0) {
            Toast.makeText(this, "No local report to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val row = rows.getJSONObject(rows.length() - 1)
        val text = formatReport(row)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nanu safety report", text))
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nanu AI safety report")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Export safety report"))
    }

    private fun refreshReportStatus() {
        val count = loadReports().length()
        val endpointStatus = if (reportClient.isConfigured()) "Developer reporting ready." else "Developer reporting not configured in this test build."
        reportStatusTv.text = if (count == 0) {
            endpointStatus
        } else {
            "$count local report${if (count == 1) "" else "s"} stored • $endpointStatus"
        }
    }

    private fun loadReports(): JSONArray = runCatching {
        JSONArray(prefs.getString(KEY_REPORTS, "[]"))
    }.getOrDefault(JSONArray())

    private fun formatReport(row: JSONObject): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(row.optLong("time")))
        val submitted = if (row.optBoolean("submitted", false)) "yes" else "no"
        val id = row.optString("report_id").takeIf { it.isNotBlank() && it != "null" }
        return buildString {
            append("Nanu AI safety report\nDate: $date\nCategory: ${row.optString("category")}\nSubmitted: $submitted")
            if (id != null) append("\nReference: $id")
            append("\n\n${row.optString("details")}")
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Could not open link.", Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val EXTRA_REPORTED_CONTENT = "reported_content"
        private const val PREFS = "nanu_safety"
        private const val KEY_REPORTS = "reports"
        private const val PRIVACY_URL = "https://github.com/joyarnold16/NanuAPKCloud/blob/main/PRIVACY_POLICY.md"
        private const val TERMS_URL = "https://github.com/joyarnold16/NanuAPKCloud/blob/main/TERMS_OF_USE.md"
    }
}
