package com.example.llama

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Offline tarot library and reflection tool. No reading or question leaves the device. */
class TarotActivity : NanuBaseActivity() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var question: EditText
    private lateinit var result: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tarot)

        question = findViewById(R.id.tarot_question)
        result = findViewById(R.id.tarot_result)
        status = findViewById(R.id.tarot_history_status)
        findViewById<MaterialButton>(R.id.tarot_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.tarot_one).setOnClickListener { draw(1) }
        findViewById<MaterialButton>(R.id.tarot_three).setOnClickListener { draw(3) }
        findViewById<MaterialButton>(R.id.tarot_library).setOnClickListener { showLibrary() }
        findViewById<MaterialButton>(R.id.tarot_history).setOnClickListener { showHistory() }
        findViewById<MaterialButton>(R.id.tarot_clear_history).setOnClickListener { confirmClearHistory() }
        updateHistoryStatus()
    }

    private fun draw(count: Int) {
        val reading = TarotDeck.reading(question.text?.toString().orEmpty(), count)
        result.text = reading
        save(reading)
        updateHistoryStatus()
    }

    private fun showLibrary() {
        val labels = TarotDeck.cards.map { "${it.symbol}  ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("All 78 tarot cards")
            .setItems(labels) { _, index ->
                result.text = TarotDeck.details(TarotDeck.cards[index]) + DISCLAIMER
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHistory() {
        val rows = history()
        if (rows.length() == 0) {
            Toast.makeText(this, "No saved tarot readings yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index)
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(row?.optLong("created", 0L) ?: 0L))
            val heading = row?.optString("reading").orEmpty().lineSequence().firstOrNull().orEmpty()
            "$date • $heading"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Private reading history")
            .setItems(labels) { _, index -> result.text = rows.optJSONObject(index)?.optString("reading").orEmpty() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmClearHistory() {
        if (history().length() == 0) return
        AlertDialog.Builder(this)
            .setTitle("Clear tarot history?")
            .setMessage("This permanently removes saved readings from this device.")
            .setPositiveButton("Clear") { _, _ ->
                prefs.edit().remove(KEY_HISTORY).apply()
                updateHistoryStatus()
                Toast.makeText(this, "Tarot history cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(reading: String) {
        val old = history()
        val updated = JSONArray().put(JSONObject().put("created", System.currentTimeMillis()).put("reading", reading))
        for (index in 0 until minOf(old.length(), MAX_HISTORY - 1)) updated.put(old.optJSONObject(index))
        prefs.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    private fun history(): JSONArray = runCatching {
        JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun updateHistoryStatus() {
        val count = history().length()
        status.text = "$count private reading${if (count == 1) "" else "s"} saved locally • 78-card deck"
    }

    companion object {
        private const val PREFS = "nanu_tarot"
        private const val KEY_HISTORY = "reading_history"
        private const val MAX_HISTORY = 30
        private const val DISCLAIMER = "\n\nUse tarot for reflection or entertainment, not as factual prediction or medical, legal, safety or trading advice."
    }
}
