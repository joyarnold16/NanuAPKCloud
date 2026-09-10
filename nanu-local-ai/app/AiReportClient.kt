package com.example.llama

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AiReportClient(private val context: Context) {

    fun isConfigured(): Boolean = endpoint().startsWith("https://")

    fun submit(category: String, details: String): Result<String> = runCatching {
        val endpoint = endpoint()
        require(endpoint.startsWith("https://")) { "Developer reporting endpoint is not configured." }

        val reportId = UUID.randomUUID().toString()
        val body = JSONObject()
            .put("report_id", reportId)
            .put("app", "Nanu Local AI")
            .put("category", category.take(80))
            .put("details", details.take(8000))
            .put("created_at_ms", System.currentTimeMillis())
            .toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("User-Agent", "NanuLocalAI/1.0 Android")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) error("Reporting service returned HTTP $code")

            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                val buffer = CharArray(16_001)
                var used = 0
                while (used < buffer.size) {
                    val count = it.read(buffer, used, buffer.size - used)
                    if (count < 0) break
                    used += count
                }
                require(used <= 16_000) { "Reporting service returned an oversized response" }
                String(buffer, 0, used)
            }
            validateAcknowledgement(responseText, reportId)
            reportId
        } finally {
            connection.disconnect()
        }
    }

    private fun endpoint(): String = context.getString(R.string.nanu_report_endpoint).trim()

    companion object {
        internal fun validateAcknowledgement(text: String, reportId: String) {
            val response = runCatching { JSONObject(text) }.getOrNull()
                ?: error("Reporting service did not confirm delivery. Please try again later.")
            require(response.opt("ok") == true && response.optString("report_id") == reportId) {
                "Reporting service did not confirm this report. Please try again later."
            }
        }
    }
}
