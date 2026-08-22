package com.example.llama

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class TradingActivity : AppCompatActivity() {

    private lateinit var marketButton: MaterialButton
    private lateinit var symbolEt: EditText
    private lateinit var timeframeEt: EditText
    private lateinit var candleInputEt: EditText
    private lateinit var resultTv: TextView
    private lateinit var snapshotButton: MaterialButton

    private var marketType = MarketType.CRYPTO
    private var selectedChartImage: Uri? = null

    private val prefs by lazy { getSharedPreferences(PREFS_TRADING, MODE_PRIVATE) }

    private val chartImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedChartImage = uri
        if (uri != null) {
            AlertDialog.Builder(this)
                .setTitle("Chart image selected")
                .setMessage(
                    "The image picker is connected, but visual chart interpretation needs a local vision model. " +
                        "Nanu will keep screenshot analysis separate from the numerical OHLC engine so it never pretends to see a chart it cannot actually read."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0D12")
        window.navigationBarColor = Color.parseColor("#0B0D12")
        setContentView(R.layout.activity_trading)

        marketButton = findViewById(R.id.market_type_button)
        symbolEt = findViewById(R.id.trading_symbol)
        timeframeEt = findViewById(R.id.trading_timeframe)
        candleInputEt = findViewById(R.id.candle_input)
        resultTv = findViewById(R.id.trading_result)
        snapshotButton = findViewById(R.id.live_snapshot_button)

        marketType = MarketType.fromId(prefs.getString(KEY_MARKET_TYPE, null))
        updateMarketUi()

        findViewById<MaterialButton>(R.id.trading_back).setOnClickListener { finish() }
        marketButton.setOnClickListener {
            marketType = if (marketType == MarketType.CRYPTO) MarketType.FOREX else MarketType.CRYPTO
            prefs.edit().putString(KEY_MARKET_TYPE, marketType.id).apply()
            updateMarketUi()
        }
        findViewById<MaterialButton>(R.id.analyze_chart_button).setOnClickListener { analyzeCurrentChart(false) }
        snapshotButton.setOnClickListener { loadLiveSnapshot() }
        findViewById<MaterialButton>(R.id.scan_button).setOnClickListener { analyzeCurrentChart(true) }
        findViewById<MaterialButton>(R.id.risk_button).setOnClickListener { showRiskCalculatorChooser() }
        findViewById<MaterialButton>(R.id.patterns_button).setOnClickListener { showPatternLibrary() }
        findViewById<MaterialButton>(R.id.journal_button).setOnClickListener { showJournal() }
        findViewById<MaterialButton>(R.id.chart_image_button).setOnClickListener { chartImagePicker.launch("image/*") }
    }

    private fun updateMarketUi() {
        marketButton.text = marketType.label
        if (symbolEt.text.isNullOrBlank()) {
            symbolEt.hint = if (marketType == MarketType.CRYPTO) "BTC/USD" else "EURUSD"
        }
    }

    private fun analyzeCurrentChart(scannerOnly: Boolean) {
        val candles = TradingEngine.parseCsv(candleInputEt.text.toString())
        if (candles.size < 5) {
            Toast.makeText(
                this,
                "Paste at least 5 valid OHLC candles. 50+ candles gives much stronger analysis.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val analysis = TradingEngine.analyze(candles)
            resultTv.text = if (scannerOnly) formatScanner(analysis, candles) else formatAnalysis(analysis, candles)
        } catch (e: Exception) {
            resultTv.text = "Analysis error: ${e.message}"
        }
    }

    private fun formatAnalysis(a: MarketAnalysis, candles: List<Candle>): String {
        val symbol = symbolEt.text.toString().ifBlank { if (marketType == MarketType.CRYPTO) "Crypto" else "Forex" }
        val tf = timeframeEt.text.toString().ifBlank { "Custom" }
        val i = a.indicators

        return buildString {
            append("$symbol • $tf • ${candles.size} candles\n\n")
            append("BIAS  ${a.bias}   |   Score ${if (a.score >= 0) "+" else ""}${a.score}/100\n")
            append("Trend: ${a.trend}\n")
            append("Structure: ${a.structure}\n")
            append("Volatility: ${a.risk}\n")
            a.support?.let { append("Support: ${fmt(it)}\n") }
            a.resistance?.let { append("Resistance: ${fmt(it)}\n") }

            append("\nINDICATORS\n")
            valueLine("RSI 14", i.rsi14)?.let(::append)
            valueLine("EMA 20", i.ema20)?.let(::append)
            valueLine("EMA 50", i.ema50)?.let(::append)
            valueLine("SMA 20", i.sma20)?.let(::append)
            valueLine("SMA 50", i.sma50)?.let(::append)
            valueLine("MACD", i.macd)?.let(::append)
            valueLine("MACD signal", i.macdSignal)?.let(::append)
            valueLine("ATR 14", i.atr14)?.let(::append)
            valueLine("ADX 14", i.adx14)?.let(::append)
            valueLine("Stochastic %K", i.stochasticK)?.let(::append)
            valueLine("Stochastic %D", i.stochasticD)?.let(::append)
            valueLine("VWAP", i.vwap)?.let(::append)
            if (i.bollingerUpper != null && i.bollingerMiddle != null && i.bollingerLower != null) {
                append("Bollinger: ${fmt(i.bollingerLower)} / ${fmt(i.bollingerMiddle)} / ${fmt(i.bollingerUpper)}\n")
            }

            append("\nCANDLESTICK PATTERNS\n")
            append(if (a.candlestickPatterns.isEmpty()) "No strong latest-candle pattern detected.\n" else a.candlestickPatterns.joinToString("\n") { "• $it" } + "\n")

            append("\nCHART / STRUCTURE PATTERNS\n")
            append(if (a.chartPatterns.isEmpty()) "No strong heuristic chart pattern detected.\n" else a.chartPatterns.joinToString("\n") { "• $it" } + "\n")
            a.divergence?.let { append("• $it\n") }

            if (a.fibonacci.isNotEmpty()) {
                append("\nFIBONACCI REFERENCE\n")
                a.fibonacci.forEach { (level, price) -> append("$level  ${fmt(price)}\n") }
            }

            append("\nCONTEXT / RISK\n")
            a.notes.forEach { append("• $it\n") }
            append("\nNanu does not treat a pattern or score as a guaranteed buy/sell signal. Define invalidation and position size before any real trade.")
        }
    }

    private fun formatScanner(a: MarketAnalysis, candles: List<Candle>): String {
        val setup = when {
            a.score >= 60 -> "Strong bullish confluence candidate"
            a.score >= 35 -> "Bullish confluence candidate"
            a.score <= -60 -> "Strong bearish confluence candidate"
            a.score <= -35 -> "Bearish confluence candidate"
            else -> "No high-confluence setup"
        }
        return buildString {
            append("NANU SCANNER\n")
            append("Dataset: ${candles.size} candles\n")
            append("Result: $setup\n")
            append("Score: ${if (a.score >= 0) "+" else ""}${a.score}/100\n")
            append("Trend: ${a.trend}\n")
            append("Structure: ${a.structure}\n")
            append("Risk: ${a.risk}\n")
            a.indicators.rsi14?.let { append("RSI: ${fmt(it)}\n") }
            a.indicators.macd?.let { m -> a.indicators.macdSignal?.let { s -> append("MACD: ${if (m > s) "above" else "below"} signal\n") } }
            if (a.candlestickPatterns.isNotEmpty()) append("Candles: ${a.candlestickPatterns.joinToString()}\n")
            if (a.chartPatterns.isNotEmpty()) append("Patterns: ${a.chartPatterns.joinToString()}\n")
            a.divergence?.let { append("Divergence: $it\n") }
            append("\nScanner scores confluence; it does not execute trades or promise outcomes.")
        }
    }

    private fun loadLiveSnapshot() {
        val symbol = symbolEt.text.toString().trim()
        if (symbol.isBlank()) {
            Toast.makeText(this, "Enter a symbol first.", Toast.LENGTH_SHORT).show()
            return
        }
        snapshotButton.isEnabled = false
        snapshotButton.text = "Loading…"
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                if (marketType == MarketType.CRYPTO) MarketSnapshotClient.crypto(symbol) else MarketSnapshotClient.forex(symbol)
            }
            withContext(Dispatchers.Main) {
                snapshotButton.isEnabled = true
                snapshotButton.text = "Live snapshot"
                result.onSuccess { snap ->
                    resultTv.text = buildString {
                        append("LIVE SNAPSHOT\n\n")
                        append("${snap.symbol}: ${fmt(snap.price)}\n")
                        snap.change24h?.let { append("24h change: ${String.format(Locale.US, "%+.2f%%", it)}\n") }
                        append("Source: ${snap.source}\n\n")
                        append("This is an informational online snapshot. Technical chart analysis still uses OHLC candles calculated locally.")
                    }
                }.onFailure {
                    resultTv.text = "Could not retrieve live snapshot: ${it.message}\n\nYou can still paste OHLCV candles and analyze them fully offline."
                }
            }
        }
    }

    private fun showRiskCalculatorChooser() {
        AlertDialog.Builder(this)
            .setTitle("Risk calculator")
            .setItems(arrayOf("Forex position size", "Crypto position size")) { _, which ->
                if (which == 0) showForexRiskCalculator() else showCryptoRiskCalculator()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showForexRiskCalculator() {
        val balance = numberField("Account balance", "10000")
        val risk = numberField("Risk %", "1")
        val stop = numberField("Stop loss (pips)", "20")
        val pipValue = numberField("Pip value per 1.00 lot in account currency", "10")
        val box = verticalForm(balance, risk, stop, pipValue)

        AlertDialog.Builder(this)
            .setTitle("Forex position size")
            .setMessage("For many USD-quoted major pairs, a standard lot is often near $10/pip, but this is not universal. Enter the correct pip value for the pair/account currency.")
            .setView(box)
            .setPositiveButton("Calculate") { _, _ ->
                val b = balance.text.toString().toDoubleOrNull()
                val r = risk.text.toString().toDoubleOrNull()
                val s = stop.text.toString().toDoubleOrNull()
                val pv = pipValue.text.toString().toDoubleOrNull()
                if (b == null || r == null || s == null || pv == null || b <= 0 || r <= 0 || s <= 0 || pv <= 0) {
                    Toast.makeText(this, "Enter valid positive numbers.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val riskAmount = b * r / 100.0
                val lots = riskAmount / (s * pv)
                showCalculation("Forex risk result", "Risk amount: ${fmt(riskAmount)}\nPosition size: ${String.format(Locale.US, "%.3f", lots)} lots\nStop: ${fmt(s)} pips")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCryptoRiskCalculator() {
        val balance = numberField("Account balance", "10000")
        val risk = numberField("Risk %", "1")
        val entry = numberField("Entry price", "100")
        val stop = numberField("Stop price", "95")
        val target = numberField("Target price (optional)", "110")
        val box = verticalForm(balance, risk, entry, stop, target)

        AlertDialog.Builder(this)
            .setTitle("Crypto position size")
            .setView(box)
            .setPositiveButton("Calculate") { _, _ ->
                val b = balance.text.toString().toDoubleOrNull()
                val r = risk.text.toString().toDoubleOrNull()
                val e = entry.text.toString().toDoubleOrNull()
                val s = stop.text.toString().toDoubleOrNull()
                val t = target.text.toString().toDoubleOrNull()
                if (b == null || r == null || e == null || s == null || b <= 0 || r <= 0 || e <= 0 || s <= 0 || e == s) {
                    Toast.makeText(this, "Enter valid values and use a stop different from entry.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val riskAmount = b * r / 100.0
                val unitRisk = abs(e - s)
                val qty = riskAmount / unitRisk
                val notional = qty * e
                val rr = if (t != null && t > 0) abs(t - e) / unitRisk else null
                showCalculation(
                    "Crypto risk result",
                    buildString {
                        append("Risk amount: ${fmt(riskAmount)}\n")
                        append("Quantity: ${String.format(Locale.US, "%.6f", qty)}\n")
                        append("Position notional: ${fmt(notional)}\n")
                        rr?.let { append("Planned R:R: 1:${String.format(Locale.US, "%.2f", it)}\n") }
                        append("This ignores fees, slippage, liquidation and gap risk.")
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPatternLibrary() {
        val labels = TradingKnowledge.patterns.map { "${it.name}  •  ${it.group}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Chart + candlestick knowledge")
            .setItems(labels) { _, which ->
                val p = TradingKnowledge.patterns[which]
                AlertDialog.Builder(this)
                    .setTitle(p.name)
                    .setMessage("${p.group}\n\n${p.description}")
                    .setPositiveButton("Close", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showJournal() {
        val rows = loadJournal()
        val actions = mutableListOf("＋ Add trade journal entry")
        actions += rows.map { row ->
            "${row.optString("date")} • ${row.optString("market")} • ${row.optString("symbol")} • ${row.optString("side")}"
        }
        if (rows.isNotEmpty()) actions += "Clear journal"

        AlertDialog.Builder(this)
            .setTitle("Local trade journal")
            .setItems(actions.toTypedArray()) { _, which ->
                when {
                    which == 0 -> addJournalEntry()
                    which in 1..rows.size -> showJournalEntry(rows[which - 1], which - 1)
                    else -> confirmClearJournal()
                }
            }
            .setMessage("Saved only on this device in Nanu app storage.")
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addJournalEntry() {
        val symbol = textField("Symbol", symbolEt.text.toString())
        val side = textField("Side (Long / Short)", "Long")
        val entry = numberField("Entry", "")
        val stop = numberField("Stop", "")
        val target = numberField("Target", "")
        val notes = textField("Notes / reason", "")
        val box = verticalForm(symbol, side, entry, stop, target, notes)

        AlertDialog.Builder(this)
            .setTitle("Add journal entry")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val row = JSONObject().apply {
                    put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                    put("market", marketType.label)
                    put("symbol", symbol.text.toString().trim())
                    put("side", side.text.toString().trim())
                    put("entry", entry.text.toString().trim())
                    put("stop", stop.text.toString().trim())
                    put("target", target.text.toString().trim())
                    put("notes", notes.text.toString().trim())
                }
                val rows = loadJournal().toMutableList()
                rows += row
                saveJournal(rows)
                Toast.makeText(this, "Journal entry saved locally.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJournalEntry(row: JSONObject, index: Int) {
        val text = buildString {
            append("Date: ${row.optString("date")}\n")
            append("Market: ${row.optString("market")}\n")
            append("Symbol: ${row.optString("symbol")}\n")
            append("Side: ${row.optString("side")}\n")
            append("Entry: ${row.optString("entry")}\n")
            append("Stop: ${row.optString("stop")}\n")
            append("Target: ${row.optString("target")}\n\n")
            append(row.optString("notes"))
        }
        AlertDialog.Builder(this)
            .setTitle("Journal entry")
            .setMessage(text)
            .setNeutralButton("Delete") { _, _ ->
                val rows = loadJournal().toMutableList()
                if (index in rows.indices) {
                    rows.removeAt(index)
                    saveJournal(rows)
                    Toast.makeText(this, "Journal entry deleted.", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmClearJournal() {
        AlertDialog.Builder(this)
            .setTitle("Clear trade journal?")
            .setMessage("This removes all locally saved journal entries.")
            .setPositiveButton("Clear") { _, _ -> saveJournal(emptyList()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadJournal(): List<JSONObject> {
        val raw = prefs.getString(KEY_JOURNAL, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun saveJournal(rows: List<JSONObject>) {
        val arr = JSONArray()
        rows.forEach { arr.put(it) }
        prefs.edit().putString(KEY_JOURNAL, arr.toString()).apply()
    }

    private fun numberField(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(getColor(R.color.nanu_text))
        setHintTextColor(getColor(R.color.nanu_muted))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        setPadding(12, 10, 12, 10)
    }

    private fun textField(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(getColor(R.color.nanu_text))
        setHintTextColor(getColor(R.color.nanu_muted))
        setPadding(12, 10, 12, 10)
    }

    private fun verticalForm(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 8, 20, 0)
        views.forEach { addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    }

    private fun showCalculation(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun valueLine(label: String, value: Double?): String? = value?.let { "$label: ${fmt(it)}\n" }

    private fun fmt(value: Double): String {
        val a = abs(value)
        return when {
            a >= 1000 -> String.format(Locale.US, "%,.2f", value)
            a >= 1 -> String.format(Locale.US, "%.5f", value)
            else -> String.format(Locale.US, "%.8f", value)
        }
    }

    private enum class MarketType(val id: String, val label: String) {
        CRYPTO("crypto", "Crypto"),
        FOREX("forex", "Forex");

        companion object {
            fun fromId(id: String?): MarketType = entries.firstOrNull { it.id == id } ?: CRYPTO
        }
    }

    companion object {
        private const val PREFS_TRADING = "nanu_trading_lab"
        private const val KEY_MARKET_TYPE = "market_type"
        private const val KEY_JOURNAL = "trade_journal"
    }
}
