package com.example.llama

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.StatFs
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale

class Rc8HomeActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("nanu_local_ai", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_rc8_home)

        bind(R.id.home_chat, MainActivity::class.java)
        bind(R.id.home_talk, ContinuousTalkActivity::class.java)
        bind(R.id.home_files, FileChatActivity::class.java)
        bind(R.id.home_create, CreateStudioActivity::class.java)
        bind(R.id.home_markets, TradingActivity::class.java)
        bind(R.id.home_paper, PaperTradingActivity::class.java)
        bind(R.id.home_safety, SafetyPrivacyActivity::class.java)
    }

    override fun onResume() {
        super.onResume()
        val modelPath = prefs.getString("last_model", null)
        val model = modelPath?.let(::File)?.takeIf { it.exists() }
        val free = StatFs(filesDir.absolutePath).availableBytes / (1024.0 * 1024.0 * 1024.0)
        findViewById<TextView>(R.id.home_status).text = buildString {
            append("RC8 • private local AI")
            if (model != null) append(" • ${compact(model.nameWithoutExtension)} ready") else append(" • choose a model in Chat")
            append("\n${String.format(Locale.US, "%.1f", free)} GB app storage free • API 36 ready")
        }
    }

    private fun bind(id: Int, target: Class<*>) {
        findViewById<MaterialButton>(id).setOnClickListener { startActivity(Intent(this, target)) }
    }

    private fun compact(value: String): String = value
        .replace(Regex("(?i)q4_k_m|instruct|gguf"), "")
        .replace(Regex("[-_]+"), " ")
        .trim()
        .take(24)
}
