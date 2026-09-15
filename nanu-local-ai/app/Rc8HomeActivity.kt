package com.example.llama

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.StatFs
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class Rc8HomeActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("nanu_local_ai", MODE_PRIVATE) }
    private lateinit var onlineTools: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeLegacyTradingData()
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_rc8_home)

        bind(R.id.home_chat, MainActivity::class.java)
        bind(R.id.home_talk, ContinuousTalkActivity::class.java)
        bind(R.id.home_files, FileChatActivity::class.java)
        bind(R.id.home_create, CreateStudioActivity::class.java)
        bind(R.id.home_tarot, TarotActivity::class.java)
        bind(R.id.home_safety, SafetyPrivacyActivity::class.java)
        bind(R.id.home_pro, ProActivity::class.java)
        findViewById<MaterialButton>(R.id.home_quick_weather).setOnClickListener {
            openDraft("What is the current weather in Kanpur?")
        }
        findViewById<MaterialButton>(R.id.home_quick_btc).setOnClickListener {
            openDraft("What is the current price of BTC?")
        }
        findViewById<MaterialButton>(R.id.home_quick_news).setOnClickListener {
            openDraft("Show me the latest AI news")
        }
        findViewById<MaterialButton>(R.id.home_quick_tarot).setOnClickListener {
            startActivity(Intent(this, TarotActivity::class.java))
        }
        onlineTools = findViewById(R.id.home_online_tools)
        onlineTools.setOnClickListener {
            val enabled = !prefs.getBoolean("online_tools_enabled", true)
            prefs.edit().putBoolean("online_tools_enabled", enabled).apply()
            renderStatus()
        }
        reveal(findViewById(R.id.home_hero), 0L)
        reveal(findViewById(R.id.home_status_card), 90L)
        reveal(findViewById(R.id.home_agent_section), 160L)
        reveal(findViewById(R.id.home_workspace_section), 220L)
    }

    override fun onResume() {
        super.onResume()
        ProBilling.get(this).refresh()
        renderStatus()
    }

    private fun renderStatus() {
        val modelPath = prefs.getString("last_model", null)
        val model = modelPath?.let(::File)?.takeIf { it.exists() }
        val free = StatFs(filesDir.absolutePath).availableBytes / (1024.0 * 1024.0 * 1024.0)
        val online = prefs.getBoolean("online_tools_enabled", true)
        val status = buildString {
            append("RC8.4 • local-first AI • online tools ${if (online) "ON" else "OFF"}")
            if (model != null) append(" • ${compact(model.nameWithoutExtension)} ready") else append(" • choose a model in Chat")
            append("\n${String.format(Locale.US, "%.1f", free)} GB app storage free • fast live tools need no LLM")
        }
        val statusView = findViewById<TextView>(R.id.home_status)
        statusView.text = status
        lifecycleScope.launch {
            val count = ChatStore.get(applicationContext).list(includeEmpty = false).size
            statusView.text = "$status\n$count saved conversation${if (count == 1) "" else "s"} • private on this device"
        }
        onlineTools.text = if (online) {
            "🌐   Online Tools: ON\n       Weather, prices, news, web & image search"
        } else {
            "○   Online Tools: OFF\n       Tap to allow read-only live sources"
        }
        onlineTools.strokeColor = ColorStateList.valueOf(getColor(if (online) R.color.nanu_success else R.color.nanu_border))
    }

    private fun bind(id: Int, target: Class<*>) {
        findViewById<MaterialButton>(id).setOnClickListener { startActivity(Intent(this, target)) }
    }

    private fun openDraft(prompt: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_DRAFT_PROMPT, prompt))
    }

    private fun reveal(view: View, delay: Long) {
        view.alpha = 0f
        view.translationY = 18f * resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420L).start()
    }

    /**
     * RC8 no longer contains trading features. Remove data left by older test
     * builds once, so an upgrade does not silently retain obsolete journals.
     */
    private fun removeLegacyTradingData() {
        if (prefs.getBoolean(KEY_LEGACY_TRADING_DATA_REMOVED, false)) return
        deleteSharedPreferences("nanu_paper_trading")
        deleteSharedPreferences("nanu_trading_lab")
        prefs.edit().putBoolean(KEY_LEGACY_TRADING_DATA_REMOVED, true).apply()
    }

    private fun compact(value: String): String = value
        .replace(Regex("(?i)q4_k_m|instruct|gguf"), "")
        .replace(Regex("[-_]+"), " ")
        .trim()
        .take(24)

    companion object {
        private const val KEY_LEGACY_TRADING_DATA_REMOVED = "legacy_trading_data_removed_v1"
    }
}
