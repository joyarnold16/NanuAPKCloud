package com.example.llama

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

enum class NanuThemeMode(val storedValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun from(value: String?): NanuThemeMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

/** Keeps XML and Compose screens on one user-selected light/dark setting. */
object NanuThemeController {
    private const val PREFS = "nanu_local_ai"
    private const val KEY = "visual_theme"

    fun current(context: Context): NanuThemeMode = NanuThemeMode.from(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, NanuThemeMode.SYSTEM.storedValue)
    )

    fun apply(context: Context) {
        val mode = when (current(context)) {
            NanuThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            NanuThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            NanuThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun set(context: Context, mode: NanuThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.storedValue).apply()
        apply(context)
    }

    fun syncSystemBars(activity: Activity) {
        val window = activity.window
        val background = ContextCompat.getColor(activity, R.color.nanu_bg)
        window.statusBarColor = background
        window.navigationBarColor = background
        val night = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }
}
