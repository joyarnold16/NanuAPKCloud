package com.example.llama

import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import android.speech.SpeechRecognizer
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class Rc8HomeActivity : NanuBaseActivity() {
    private val prefs by lazy { getSharedPreferences("nanu_local_ai", MODE_PRIVATE) }
    private var snapshot by mutableStateOf(NanuHomeState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeLegacyTradingData()
        setContent {
            NanuVisualHome(
                state = snapshot,
                themeMode = NanuThemeController.current(this),
                showOnboarding = !snapshot.onboardingComplete,
                onFinishOnboarding = {
                    prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
                    refreshSnapshot()
                },
                onTheme = { mode -> NanuThemeController.set(this, mode) },
                onToggleOnline = {
                    prefs.edit().putBoolean("online_tools_enabled", !snapshot.onlineTools).apply()
                    refreshSnapshot()
                },
                onOpen = ::openDestination,
                onPrompt = ::openDraft
            )
        }
        refreshSnapshot()
    }

    override fun onResume() {
        super.onResume()
        ProBilling.get(this).refresh()
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        val model = prefs.getString("last_model", null)?.let(::File)?.takeIf { it.isFile }
        val free = StatFs(filesDir.absolutePath).availableBytes / (1024.0 * 1024.0 * 1024.0)
        val imageModel = File(filesDir, "image-models").walkTopDown().any { it.isFile && it.length() > 1_000_000L }
        snapshot = snapshot.copy(
            onlineTools = prefs.getBoolean("online_tools_enabled", true),
            modelName = model?.nameWithoutExtension?.let(::compact),
            freeStorageGb = String.format(Locale.US, "%.1f", free),
            imageModelReady = imageModel,
            speechReady = SpeechRecognizer.isRecognitionAvailable(this),
            proEnabled = ProEntitlement.enabled(this),
            reportReady = getString(R.string.nanu_report_endpoint).trim().startsWith("https://"),
            onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        )
        lifecycleScope.launch {
            val count = ChatStore.get(applicationContext).list(includeEmpty = false).size
            snapshot = snapshot.copy(savedChats = count)
        }
    }

    private fun openDestination(destination: NanuDestination) {
        val target = when (destination) {
            NanuDestination.CHAT -> MainActivity::class.java
            NanuDestination.TALK -> ContinuousTalkActivity::class.java
            NanuDestination.FILES -> FileChatActivity::class.java
            NanuDestination.CREATE -> CreateStudioActivity::class.java
            NanuDestination.TAROT -> TarotActivity::class.java
            NanuDestination.SAFETY -> SafetyPrivacyActivity::class.java
            NanuDestination.PRO -> ProActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    private fun openDraft(prompt: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_DRAFT_PROMPT, prompt))
    }

    /** Remove obsolete data from pre-RC8 builds once during upgrade. */
    private fun removeLegacyTradingData() {
        if (prefs.getBoolean(KEY_LEGACY_TRADING_DATA_REMOVED, false)) return
        deleteSharedPreferences("nanu_paper_trading")
        deleteSharedPreferences("nanu_trading_lab")
        prefs.edit().putBoolean(KEY_LEGACY_TRADING_DATA_REMOVED, true).apply()
    }

    private fun compact(value: String): String = value
        .replace(Regex("(?i)q4_k_m|instruct|gguf"), "")
        .replace(Regex("[-_]+"), " ")
        .trim().take(28)

    companion object {
        private const val KEY_LEGACY_TRADING_DATA_REMOVED = "legacy_trading_data_removed_v1"
        private const val KEY_ONBOARDING_COMPLETE = "visual_onboarding_complete_v1"
    }
}
