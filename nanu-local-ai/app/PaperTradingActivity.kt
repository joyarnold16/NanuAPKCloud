package com.example.llama

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
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

class PaperTradingActivity : AppCompatActivity() {
    private lateinit var symbolEt: EditText
    private lateinit var priceEt: EditText
    private lateinit var qtyEt: EditText
    private lateinit var sideBtn: MaterialButton
    private lateinit var summaryTv: TextView
    private lateinit var positionsTv: TextView
    private var side = "LONG"
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#060B12")
        window.navigationBarColor = Color.parseColor("#060B12")
        setContentView(R.layout.activity_paper_trading)

        symbolEt = findViewById(R.id.paper_symbol)
        priceEt = findViewById(R.id.paper_price)
        qtyEt = findViewById(R.id.paper_qty)
        sideBtn = findViewById(R.id.paper_side)
        summaryTv = findViewById(R.id.paper_summary)
        positionsTv = findViewById(R.id.paper_positions)

        if (!prefs.contains(KEY_BALANCE)) prefs.edit().putString(KEY_BALANCE, "10000.0").apply()
        findViewById<MaterialButton>(R.id.paper_back).setOnClickListener { finish() }
        sideBtn.setOnClickListener { side = if (side == "LONG") "SHORT" else "LONG"; sideBtn.text = "Side: $side" }
        findViewById<MaterialButton>(R.id.paper_open).setOnClickListener { openPosition() }
        findViewById<MaterialButton>(R.id.paper_close).setOnClickListener { choosePositionToClose() }
        findViewById<MaterialButton>(R.id.paper_history).setOnClickListener { showClosedHistory() }
        findViewById<MaterialButton>(R.id.paper_reset).setOnClickListener { confirmReset() }
        refresh()
    }

    private fun openPosition() {
        val symbol = symbolEt.text.toString().trim().uppercase(Locale.US)
        val price = priceEt.text.toString().toDoubleOrNull()
        val qty = qtyEt.text.toString().toDoubleOrNull()
        if (symbol.isBlank() || price == null || qty == null || price <= 0 || qty <= 0) {
            Toast.makeText(this, "Enter a symbol, positive entry price and quantity.", Toast.LENGTH_LONG).show()
            return
        }
        val rows = positions()
        rows.put(JSONObject().put("id", System.currentTimeMillis()).put("symbol", symbol).put("side", side).put("entry", price).put("qty", qty).put("opened", System.currentTimeMillis()))
        prefs.edit().putString(KEY_POSITIONS, rows.toString()).apply()
        priceEt.text = null
        qtyEt.text = null
        refresh()
        Toast.makeText(this, "Virtual $side position opened. No real trade was placed.", Toast.LENGTH_LONG).show()
    }

    private fun choosePositionToClose() {
        val rows = positions()
        if (rows.length() == 0) { Toast.makeText(this, "No open paper positions.", Toast.LENGTH_SHORT).show(); return }
        val labels = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            "${row.optString("symbol")} • ${row.optString("side")} • ${fmt(row.optDouble("qty"))} @ ${fmt(row.optDouble("entry"))}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Close paper position")
            .setItems(labels) { _, which -> askExitPrice(which) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askExitPrice(index: Int) {
        val field = EditText(this).apply { hint = "Exit price"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        AlertDialog.Builder(this)
            .setTitle("Enter virtual exit price")
            .setView(field)
            .setPositiveButton("Close position") { _, _ ->
                val exit = field.text.toString().toDoubleOrNull()
                if (exit == null || exit <= 0) { Toast.makeText(this, "Invalid exit price.", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                closePosition(index, exit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closePosition(index: Int, exit: Double) {
        val rows = positions()
        if (index !in 0 until rows.length()) return
        val row = rows.getJSONObject(index)
        val entry = row.optDouble("entry")
        val qty = row.optDouble("qty")
        val sign = if (row.optString("side") == "LONG") 1.0 else -1.0
        val pnl = (exit - entry) * qty * sign
        val next = JSONArray()
        for (i in 0 until rows.length()) if (i != index) next.put(rows.getJSONObject(i))
        val history = closedHistory()
        row.put("exit", exit).put("pnl", pnl).put("closed", System.currentTimeMillis())
        history.put(row)
        val balance = balance() + pnl
        prefs.edit().putString(KEY_POSITIONS, next.toString()).putString(KEY_HISTORY, trim(history, 100).toString()).putString(KEY_BALANCE, balance.toString()).apply()
        refresh()
        AlertDialog.Builder(this)
            .setTitle("Paper trade closed")
            .setMessage("${row.optString("symbol")} • ${row.optString("side")}\nVirtual P/L: ${signed(pnl)}\nVirtual balance: ${fmt(balance)}\n\nNo real-money order was sent.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun refresh() {
        val rows = positions()
        val history = closedHistory()
        val realized = (0 until history.length()).sumOf { history.getJSONObject(it).optDouble("pnl", 0.0) }
        summaryTv.text = "Virtual balance: ${fmt(balance())}\nRealized P/L: ${signed(realized)}\nOpen positions: ${rows.length()} • Closed trades: ${history.length()}"
        positionsTv.text = if (rows.length() == 0) {
            "No open paper positions.\n\nUse virtual entries to practise planning and discipline without placing a real trade."
        } else {
            buildString {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    append("${i + 1}. ${row.optString("symbol")} • ${row.optString("side")}\n")
                    append("   ${fmt(row.optDouble("qty"))} units @ ${fmt(row.optDouble("entry"))}\n\n")
                }
            }.trim()
        }
    }

    private fun showClosedHistory() {
        val rows = closedHistory()
        if (rows.length() == 0) { Toast.makeText(this, "No closed paper trades yet.", Toast.LENGTH_SHORT).show(); return }
        val labels = (rows.length() - 1 downTo 0).map { i ->
            val row = rows.getJSONObject(i)
            "${row.optString("symbol")} • ${row.optString("side")} • ${signed(row.optDouble("pnl"))}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Paper trade history").setItems(labels, null).setNegativeButton("Close", null).show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset paper account?")
            .setMessage("This deletes open and closed virtual trades and restores the virtual balance to 10,000. No real financial account is affected.")
            .setPositiveButton("Reset") { _, _ -> prefs.edit().putString(KEY_BALANCE, "10000.0").remove(KEY_POSITIONS).remove(KEY_HISTORY).apply(); refresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun positions(): JSONArray = parse(KEY_POSITIONS)
    private fun closedHistory(): JSONArray = parse(KEY_HISTORY)
    private fun parse(key: String): JSONArray = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
    private fun balance(): Double = prefs.getString(KEY_BALANCE, "10000.0")?.toDoubleOrNull() ?: 10000.0

    private fun trim(source: JSONArray, max: Int): JSONArray {
        val out = JSONArray()
        val start = (source.length() - max).coerceAtLeast(0)
        for (i in start until source.length()) out.put(source.getJSONObject(i))
        return out
    }

    private fun fmt(value: Double): String = if (kotlin.math.abs(value) >= 1000) String.format(Locale.US, "%,.2f", value) else String.format(Locale.US, "%.4f", value)
    private fun signed(value: Double): String = String.format(Locale.US, "%+.2f", value)

    companion object {
        private const val PREFS = "nanu_paper_trading"
        private const val KEY_BALANCE = "balance"
        private const val KEY_POSITIONS = "positions"
        private const val KEY_HISTORY = "history"
    }
}
