package com.example.llama

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

open class NanuBaseActivity : AppCompatActivity() {
    @Suppress("UNUSED_PARAMETER")
    fun openHome(view: View) {
        startActivity(Intent(this, Rc8HomeActivity::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun openTalk(view: View) {
        startActivity(Intent(this, ContinuousTalkActivity::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun openCreate(view: View) {
        startActivity(Intent(this, CreateStudioActivity::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun openFiles(view: View) {
        startActivity(Intent(this, FileChatActivity::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun openSafety(view: View) {
        startActivity(Intent(this, SafetyPrivacyActivity::class.java))
    }
}
