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
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafetyPrivacyActivity : AppCompatActivity() {
    private lateinit var categoryEt: EditText
    private lateinit var detailsEt: EditText
    private lateinit var reportStatusTv: TextView
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_safety_privacy)

        categoryEt = findViewById(R.id.report_category)
        detailsEt = findViewById(R.id.report_details)
        reportStatusTv = findViewById(R.id.report_status)
        intent.getStringExtra(EXTRA_REPORTED_CONTENT)?.takeIf { it.isNotBlank() }?.let {
            detailsEt.setText("Reported AI output:\n$it")
        }

        findViewById<MaterialButton>(R.id.safety_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.privacy_policy_button).setOnClickListener { openUrl(PRIVACY_URL) }
        findViewById<MaterialButton>(R.id.terms_button).setOnClickListener { openUrl(TERMS_URL) }
        findViewById<MaterialButton>(R.id.save_report_button).setOnClickListener { saveReport() }
        findViewById<MaterialButton>(R.id.export_report_button).setOnClickListener { exportLatestReport() }
        findViewById<MaterialButton>(R.id.clear_reports_button).setOnClickListener {
            prefs.edit().remove(KEY_REPORTS).apply()
            refreshReportStatus()
            Toast.makeText(this, "Local safety reports cleared.", Toast.LENGTH_SHORT).show()
        }
        refreshReportStatus()
    }

    private fun saveReport() {
        val category = categoryEt.text.toString().trim().ifBlank { "AI output" }
        val details = detailsEt.text.toString().trim()
        if (details.isBlank()) {
            Toast.makeText(this, "Describe what should be reported.", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = loadReports()
        rows.put(
            JSONObject()
                .put("time", System.currentTimeMillis())
                .put("category", category.take(80))
                .put("details", details.take(8000))
        )
        while (rows.length() > 50) {
            val trimmed = JSONArray()
            for (i in 1 until rows.length()) trimmed.put(rows.get(i))
            prefs.edit().putString(KEY_REPORTS, trimmed.toString()).apply()
            detailsEt.text = null
            refreshReportStatus()
            Toast.makeText(this, "Safety report saved locally.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putString(KEY_REPORTS, rows.toString()).apply()
        detailsEt.text = null
        refreshReportStatus()
        Toast.makeText(this, "Safety report saved locally.", Toast.LENGTH_LONG).show()
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
        reportStatusTv.text = if (count == 0) {
            "No safety reports stored on this device."
        } else {
            "$count safety report${if (count == 1) "" else "s"} stored locally."
        }
    }

    private fun loadReports(): JSONArray = runCatching {
        JSONArray(prefs.getString(KEY_REPORTS, "[]"))
    }.getOrDefault(JSONArray())

    private fun formatReport(row: JSONObject): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(row.optLong("time")))
        return "Nanu AI safety report\nDate: $date\nCategory: ${row.optString("category")}\n\n${row.optString("details")}"
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
