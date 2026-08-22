package com.example.llama

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

open class NanuBaseActivity : AppCompatActivity() {
    @Suppress("UNUSED_PARAMETER")
    fun openTalk(view: View) {
        startActivity(Intent(this, TalkActivity::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun openCreate(view: View) {
        startActivity(Intent(this, CreateActivity::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun openTrading(view: View) {
        startActivity(Intent(this, TradingActivity::class.java))
    }
}
