package com.example.llama

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Deterministic technical-analysis engine used by Nanu Trading Lab.
 *
 * This code intentionally keeps indicator and pattern calculations separate from the LLM.
 * The LLM can explain results, but it is not trusted to invent indicator values or patterns.
 */
data class Candle(
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0
)

data class IndicatorSnapshot(
    val sma20: Double?,
    val sma50: Double?,
    val ema20: Double?,
    val ema50: Double?,
    val rsi14: Double?,
    val macd: Double?,
    val macdSignal: Double?,
    val atr14: Double?,
    val adx14: Double?,
    val stochasticK: Double?,
    val stochasticD: Double?,
    val bollingerUpper: Double?,
    val bollingerMiddle: Double?,
    val bollingerLower: Double?,
    val vwap: Double?
)

data class MarketAnalysis(
    val trend: String,
    val structure: String,
    val support: Double?,
    val resistance: Double?,
    val indicators: IndicatorSnapshot,
    val candlestickPatterns: List<String>,
    val chartPatterns: List<String>,
    val divergence: String?,
    val fibonacci: Map<String, Double>,
    val score: Int,
    val bias: String,
    val risk: String,
    val notes: List<String>
)

object TradingEngine {

    fun parseCsv(text: String): List<Candle> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val p = line.split(',').map { it.trim() }
                if (p.size < 5) return@mapNotNull null

                val offset = if (p.size >= 6 && p[0].toDoubleOrNull() == null) 1 else 0
                if (p.size < offset + 5) return@mapNotNull null

                val open = p[offset].toDoubleOrNull() ?: return@mapNotNull null
                val high = p[offset + 1].toDoubleOrNull() ?: return@mapNotNull null
                val low = p[offset + 2].toDoubleOrNull() ?: return@mapNotNull null
                val close = p[offset + 3].toDoubleOrNull() ?: return@mapNotNull null
                val volume = p.getOrNull(offset + 4)?.toDoubleOrNull() ?: 0.0
                val time = if (offset == 1) p[0] else "${System.currentTimeMillis()}"

                if (high < max(open, close) || low > min(open, close) || high < low) return@mapNotNull null
                Candle(time, open, high, low, close, volume)
            }
            .toList()
    }

    fun analyze(candles: List<Candle>): MarketAnalysis {
        require(candles.size >= 5) { "At least 5 candles are required. 50+ gives much better analysis." }

        val closes = candles.map { it.close }
        val indicators = IndicatorSnapshot(
            sma20 = sma(closes, 20),
            sma50 = sma(closes, 50),
            ema20 = ema(closes, 20),
            ema50 = ema(closes, 50),
            rsi14 = rsi(closes, 14),
            macd = macd(closes)?.first,
            macdSignal = macd(closes)?.second,
            atr14 = atr(candles, 14),
            adx14 = adx(candles, 14),
            stochasticK = stochastic(candles, 14)?.first,
            stochasticD = stochastic(candles, 14)?.second,
            bollingerUpper = bollinger(closes, 20)?.first,
            bollingerMiddle = bollinger(closes, 20)?.second,
            bollingerLower = bollinger(closes, 20)?.third,
            vwap = vwap(candles)
        )

        val prior = if (candles.size > 1) candles.dropLast(1) else candles
        val support = support(prior)
        val resistance = resistance(prior)
        val trend = detectTrend(candles, indicators)
        val structure = detectStructure(candles)
        val candlesFound = detectCandlestickPatterns(candles)
        val chartFound = detectChartPatterns(candles, support, resistance)
        val divergence = detectDivergence(candles)
        val fib = fibonacci(candles)
        val score = confluenceScore(candles, indicators, trend, candlesFound, chartFound, support, resistance)
        val bias = when {
            score >= 35 -> "Bullish"
            score <= -35 -> "Bearish"
            else -> "Neutral / mixed"
        }

        val atrPercent = indicators.atr14?.let { it / candles.last().close * 100.0 }
        val risk = when {
            atrPercent == null -> "Unknown"
            atrPercent >= 4.0 -> "Very high volatility"
            atrPercent >= 2.0 -> "High volatility"
            atrPercent >= 0.8 -> "Medium volatility"
            else -> "Lower volatility"
        }

        val notes = buildList {
            if (candles.size < 20) add("Short dataset: use at least 20 candles; 50-200 is preferred.")
            if (candles.all { it.volume <= 0.0 }) add("No volume supplied, so VWAP/volume confirmation is limited.")
            if (indicators.rsi14 != null && indicators.rsi14 < 30.0) add("RSI is below 30; oversold does not guarantee a reversal.")
            if (indicators.rsi14 != null && indicators.rsi14 > 70.0) add("RSI is above 70; overbought does not guarantee a reversal.")
            add("Patterns are mathematical heuristics. Confirm with market context, liquidity, timeframe and risk limits.")
        }

        return MarketAnalysis(
            trend = trend,
            structure = structure,
            support = support,
            resistance = resistance,
            indicators = indicators,
            candlestickPatterns = candlesFound,
            chartPatterns = chartFound,
            divergence = divergence,
            fibonacci = fib,
            score = score,
            bias = bias,
            risk = risk,
            notes = notes
        )
    }

    private fun sma(values: List<Double>, period: Int): Double? {
        if (values.size < period) return null
        return values.takeLast(period).average()
    }

    private fun ema(values: List<Double>, period: Int): Double? = emaSeries(values, period).lastOrNull()

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val out = MutableList(values.size) { 0.0 }
        out[0] = values[0]
        for (i in 1 until values.size) out[i] = values[i] * k + out[i - 1] * (1.0 - k)
        return out
    }

    private fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d >= 0) gains += d else losses -= d
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            val gain = max(d, 0.0)
            val loss = max(-d, 0.0)
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun macd(values: List<Double>): Pair<Double, Double>? {
        if (values.size < 26) return null
        val e12 = emaSeries(values, 12)
        val e26 = emaSeries(values, 26)
        val line = values.indices.map { e12[it] - e26[it] }
        val signal = emaSeries(line, 9)
        return line.last() to signal.last()
    }

    private fun trueRange(c: Candle, prevClose: Double): Double = max(
        c.high - c.low,
        max(abs(c.high - prevClose), abs(c.low - prevClose))
    )

    private fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size <= period) return null
        val trs = (1 until candles.size).map { i -> trueRange(candles[i], candles[i - 1].close) }
        if (trs.size < period) return null
        var avg = trs.take(period).average()
        for (i in period until trs.size) avg = (avg * (period - 1) + trs[i]) / period
        return avg
    }

    private fun adx(candles: List<Candle>, period: Int): Double? {
        if (candles.size < period * 2 + 1) return null
        val trs = mutableListOf<Double>()
        val plus = mutableListOf<Double>()
        val minus = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val up = candles[i].high - candles[i - 1].high
            val down = candles[i - 1].low - candles[i].low
            trs += trueRange(candles[i], candles[i - 1].close)
            plus += if (up > down && up > 0) up else 0.0
            minus += if (down > up && down > 0) down else 0.0
        }
        var trSm = trs.take(period).sum()
        var pSm = plus.take(period).sum()
        var mSm = minus.take(period).sum()
        val dx = mutableListOf<Double>()
        for (i in period until trs.size) {
            if (i > period) {
                trSm = trSm - trSm / period + trs[i]
                pSm = pSm - pSm / period + plus[i]
                mSm = mSm - mSm / period + minus[i]
            }
            if (trSm == 0.0) continue
            val pdi = 100.0 * pSm / trSm
            val mdi = 100.0 * mSm / trSm
            val denom = pdi + mdi
            if (denom > 0) dx += 100.0 * abs(pdi - mdi) / denom
        }
        return if (dx.size >= period) dx.takeLast(period).average() else null
    }

    private fun stochastic(candles: List<Candle>, period: Int): Pair<Double, Double>? {
        if (candles.size < period + 2) return null
        fun kAt(end: Int): Double? {
            val start = end - period + 1
            if (start < 0) return null
            val slice = candles.subList(start, end + 1)
            val hh = slice.maxOf { it.high }
            val ll = slice.minOf { it.low }
            if (hh == ll) return 50.0
            return 100.0 * (candles[end].close - ll) / (hh - ll)
        }
        val ks = (candles.size - 3 until candles.size).mapNotNull(::kAt)
        if (ks.isEmpty()) return null
        return ks.last() to ks.average()
    }

    private fun bollinger(values: List<Double>, period: Int): Triple<Double, Double, Double>? {
        if (values.size < period) return null
        val slice = values.takeLast(period)
        val mean = slice.average()
        val variance = slice.sumOf { (it - mean).pow(2) } / period
        val sd = sqrt(variance)
        return Triple(mean + 2.0 * sd, mean, mean - 2.0 * sd)
    }

    private fun vwap(candles: List<Candle>): Double? {
        val withVolume = candles.filter { it.volume > 0.0 }
        if (withVolume.isEmpty()) return null
        val pv = withVolume.sumOf { ((it.high + it.low + it.close) / 3.0) * it.volume }
        val volume = withVolume.sumOf { it.volume }
        return if (volume > 0.0) pv / volume else null
    }

    private fun support(candles: List<Candle>): Double? {
        if (candles.isEmpty()) return null
        val slice = candles.takeLast(min(60, candles.size))
        val pivots = pivotLows(slice, 2)
        return pivots.takeLast(3).map { it.second }.averageOrNull() ?: slice.minOf { it.low }
    }

    private fun resistance(candles: List<Candle>): Double? {
        if (candles.isEmpty()) return null
        val slice = candles.takeLast(min(60, candles.size))
        val pivots = pivotHighs(slice, 2)
        return pivots.takeLast(3).map { it.second }.averageOrNull() ?: slice.maxOf { it.high }
    }

    private fun detectTrend(candles: List<Candle>, i: IndicatorSnapshot): String {
        val close = candles.last().close
        val e20 = i.ema20
        val e50 = i.ema50
        return when {
            e20 != null && e50 != null && close > e20 && e20 > e50 -> "Uptrend"
            e20 != null && e50 != null && close < e20 && e20 < e50 -> "Downtrend"
            candles.size >= 10 && candles.last().close > candles[candles.size - 10].close -> "Weak / short-term uptrend"
            candles.size >= 10 && candles.last().close < candles[candles.size - 10].close -> "Weak / short-term downtrend"
            else -> "Range / unclear"
        }
    }

    private fun detectStructure(candles: List<Candle>): String {
        val highs = pivotHighs(candles, 2).takeLast(2)
        val lows = pivotLows(candles, 2).takeLast(2)
        if (highs.size < 2 || lows.size < 2) return "Insufficient swing structure"
        val higherHigh = highs[1].second > highs[0].second
        val higherLow = lows[1].second > lows[0].second
        return when {
            higherHigh && higherLow -> "HH + HL"
            !higherHigh && !higherLow -> "LH + LL"
            higherHigh && !higherLow -> "Expansion / mixed structure"
            else -> "Compression / mixed structure"
        }
    }

    private fun detectCandlestickPatterns(c: List<Candle>): List<String> {
        val out = mutableListOf<String>()
        val a = c.last()
        val range = (a.high - a.low).coerceAtLeast(1e-12)
        val body = abs(a.close - a.open)
        val upper = a.high - max(a.open, a.close)
        val lower = min(a.open, a.close) - a.low

        if (body / range <= 0.1) out += "Doji"
        if (lower >= body * 2.0 && upper <= max(body, range * 0.15) && a.close >= a.open) out += "Hammer / bullish rejection"
        if (upper >= body * 2.0 && lower <= max(body, range * 0.15) && a.close <= a.open) out += "Shooting star / bearish rejection"

        if (c.size >= 2) {
            val p = c[c.lastIndex - 1]
            if (p.close < p.open && a.close > a.open && a.open <= p.close && a.close >= p.open) out += "Bullish engulfing"
            if (p.close > p.open && a.close < a.open && a.open >= p.close && a.close <= p.open) out += "Bearish engulfing"
        }

        if (c.size >= 3) {
            val x = c[c.lastIndex - 2]
            val y = c[c.lastIndex - 1]
            val z = a
            val yBody = abs(y.close - y.open)
            val xBody = abs(x.close - x.open)
            if (x.close < x.open && yBody < xBody * 0.5 && z.close > z.open && z.close > (x.open + x.close) / 2.0) out += "Morning star"
            if (x.close > x.open && yBody < xBody * 0.5 && z.close < z.open && z.close < (x.open + x.close) / 2.0) out += "Evening star"

            val last3 = c.takeLast(3)
            if (last3.all { it.close > it.open } && last3.zipWithNext().all { it.second.close > it.first.close }) out += "Three white soldiers"
            if (last3.all { it.close < it.open } && last3.zipWithNext().all { it.second.close < it.first.close }) out += "Three black crows"
        }
        return out.distinct()
    }

    private fun detectChartPatterns(c: List<Candle>, support: Double?, resistance: Double?): List<String> {
        val out = mutableListOf<String>()
        val highs = pivotHighs(c, 2)
        val lows = pivotLows(c, 2)
        val last = c.last().close
        val tolerance = max(last * 0.012, 1e-9)

        if (highs.size >= 2) {
            val h = highs.takeLast(2)
            if (abs(h[0].second - h[1].second) <= tolerance && h[1].first - h[0].first >= 3) out += "Double top"
        }
        if (lows.size >= 2) {
            val l = lows.takeLast(2)
            if (abs(l[0].second - l[1].second) <= tolerance && l[1].first - l[0].first >= 3) out += "Double bottom"
        }
        if (highs.size >= 3) {
            val h = highs.takeLast(3)
            val shoulderTolerance = max(last * 0.02, 1e-9)
            if (h[1].second > h[0].second && h[1].second > h[2].second && abs(h[0].second - h[2].second) <= shoulderTolerance) out += "Head and shoulders candidate"
        }
        if (lows.size >= 3) {
            val l = lows.takeLast(3)
            val shoulderTolerance = max(last * 0.02, 1e-9)
            if (l[1].second < l[0].second && l[1].second < l[2].second && abs(l[0].second - l[2].second) <= shoulderTolerance) out += "Inverse head and shoulders candidate"
        }

        val recent = c.takeLast(min(20, c.size))
        if (recent.size >= 8) {
            val highSlope = linearSlope(recent.mapIndexed { idx, x -> idx.toDouble() to x.high })
            val lowSlope = linearSlope(recent.mapIndexed { idx, x -> idx.toDouble() to x.low })
            val scale = recent.map { it.close }.average().coerceAtLeast(1e-9)
            val flat = scale * 0.0008
            when {
                abs(highSlope) <= flat && lowSlope > flat -> out += "Ascending triangle / compression"
                abs(lowSlope) <= flat && highSlope < -flat -> out += "Descending triangle / compression"
                highSlope < -flat && lowSlope > flat -> out += "Symmetrical triangle / pennant-like compression"
                highSlope > flat && lowSlope > flat && highSlope < lowSlope -> out += "Rising wedge candidate"
                highSlope < -flat && lowSlope < -flat && abs(highSlope) > abs(lowSlope) -> out += "Falling wedge candidate"
                highSlope > flat && lowSlope > flat && abs(highSlope - lowSlope) <= max(abs(highSlope), abs(lowSlope)) * 0.35 -> out += "Ascending channel"
                highSlope < -flat && lowSlope < -flat && abs(highSlope - lowSlope) <= max(abs(highSlope), abs(lowSlope)) * 0.35 -> out += "Descending channel"
            }
        }

        if (resistance != null && last > resistance * 1.001) out += "Resistance breakout"
        if (support != null && last < support * 0.999) out += "Support breakdown"
        return out.distinct()
    }

    private fun detectDivergence(c: List<Candle>): String? {
        if (c.size < 20) return null
        val lows = pivotLows(c, 2).takeLast(2)
        val highs = pivotHighs(c, 2).takeLast(2)
        val closes = c.map { it.close }

        if (lows.size == 2) {
            val r1 = rsi(closes.take(lows[0].first + 1), 14)
            val r2 = rsi(closes.take(lows[1].first + 1), 14)
            if (r1 != null && r2 != null && lows[1].second < lows[0].second && r2 > r1 + 2.0) return "Possible bullish RSI divergence"
        }
        if (highs.size == 2) {
            val r1 = rsi(closes.take(highs[0].first + 1), 14)
            val r2 = rsi(closes.take(highs[1].first + 1), 14)
            if (r1 != null && r2 != null && highs[1].second > highs[0].second && r2 < r1 - 2.0) return "Possible bearish RSI divergence"
        }
        return null
    }

    private fun fibonacci(c: List<Candle>): Map<String, Double> {
        val recent = c.takeLast(min(100, c.size))
        val hi = recent.maxOf { it.high }
        val lo = recent.minOf { it.low }
        val d = hi - lo
        if (d <= 0.0) return emptyMap()
        return linkedMapOf(
            "0%" to hi,
            "23.6%" to (hi - d * 0.236),
            "38.2%" to (hi - d * 0.382),
            "50%" to (hi - d * 0.5),
            "61.8%" to (hi - d * 0.618),
            "78.6%" to (hi - d * 0.786),
            "100%" to lo,
            "127.2% ext" to (hi + d * 0.272),
            "161.8% ext" to (hi + d * 0.618)
        )
    }

    private fun confluenceScore(
        c: List<Candle>,
        i: IndicatorSnapshot,
        trend: String,
        candlePatterns: List<String>,
        chartPatterns: List<String>,
        support: Double?,
        resistance: Double?
    ): Int {
        var s = 0
        if (trend.contains("Uptrend")) s += 25
        if (trend.contains("Downtrend")) s -= 25
        i.rsi14?.let {
            if (it in 50.0..68.0) s += 8
            if (it in 32.0..<50.0) s -= 5
            if (it < 30.0) s += 3
            if (it > 70.0) s -= 3
        }
        if (i.macd != null && i.macdSignal != null) s += if (i.macd > i.macdSignal) 10 else -10
        if (i.adx14 != null && i.adx14 >= 25.0) s += if (trend.contains("Up")) 8 else if (trend.contains("Down")) -8 else 0

        candlePatterns.forEach {
            val p = it.lowercase()
            if (p.contains("bullish") || p.contains("hammer") || p.contains("morning") || p.contains("white soldiers")) s += 8
            if (p.contains("bearish") || p.contains("shooting") || p.contains("evening") || p.contains("black crows")) s -= 8
        }
        chartPatterns.forEach {
            val p = it.lowercase()
            if (p.contains("double bottom") || p.contains("inverse head") || p.contains("resistance breakout") || p.contains("falling wedge")) s += 12
            if (p.contains("double top") || (p.contains("head and shoulders") && !p.contains("inverse")) || p.contains("support breakdown") || p.contains("rising wedge")) s -= 12
        }

        val last = c.last().close
        support?.let { if (abs(last - it) / last <= 0.005) s += 4 }
        resistance?.let { if (abs(last - it) / last <= 0.005) s -= 4 }
        return s.coerceIn(-100, 100)
    }

    private fun pivotHighs(c: List<Candle>, window: Int): List<Pair<Int, Double>> {
        if (c.size < window * 2 + 1) return emptyList()
        val out = mutableListOf<Pair<Int, Double>>()
        for (i in window until c.size - window) {
            val h = c[i].high
            if ((i - window..i + window).all { idx -> idx == i || c[idx].high <= h }) out += i to h
        }
        return out
    }

    private fun pivotLows(c: List<Candle>, window: Int): List<Pair<Int, Double>> {
        if (c.size < window * 2 + 1) return emptyList()
        val out = mutableListOf<Pair<Int, Double>>()
        for (i in window until c.size - window) {
            val l = c[i].low
            if ((i - window..i + window).all { idx -> idx == i || c[idx].low >= l }) out += i to l
        }
        return out
    }

    private fun linearSlope(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        val xMean = points.map { it.first }.average()
        val yMean = points.map { it.second }.average()
        val num = points.sumOf { (it.first - xMean) * (it.second - yMean) }
        val den = points.sumOf { (it.first - xMean).pow(2) }
        return if (den == 0.0) 0.0 else num / den
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}

object TradingKnowledge {
    data class PatternInfo(val name: String, val group: String, val description: String)

    val patterns = listOf(
        PatternInfo("Bullish engulfing", "Candlestick", "A bullish candle fully engulfs the prior bearish real body. Stronger near support with confirmation."),
        PatternInfo("Bearish engulfing", "Candlestick", "A bearish candle fully engulfs the prior bullish real body. Stronger near resistance with confirmation."),
        PatternInfo("Doji", "Candlestick", "Open and close are very close. It signals indecision, not a trade by itself."),
        PatternInfo("Hammer", "Candlestick", "Small body with a long lower wick. It can show rejection of lower prices after weakness."),
        PatternInfo("Shooting star", "Candlestick", "Small body with a long upper wick. It can show rejection of higher prices after strength."),
        PatternInfo("Morning star", "Candlestick", "Three-candle bullish reversal structure: bearish impulse, indecision, then strong bullish recovery."),
        PatternInfo("Evening star", "Candlestick", "Three-candle bearish reversal structure: bullish impulse, indecision, then strong bearish recovery."),
        PatternInfo("Three white soldiers", "Candlestick", "Three consecutive strong bullish candles. Momentum confirmation matters because late entries can be extended."),
        PatternInfo("Three black crows", "Candlestick", "Three consecutive strong bearish candles. Momentum confirmation matters because late entries can be extended."),
        PatternInfo("Head and shoulders", "Chart", "Three peaks with the middle peak highest. A neckline break is usually the key confirmation."),
        PatternInfo("Inverse head and shoulders", "Chart", "Three troughs with the middle trough lowest. A neckline break is usually the key confirmation."),
        PatternInfo("Double top", "Chart", "Two similar swing highs separated by a pullback. Confirmation normally requires a neckline/support break."),
        PatternInfo("Double bottom", "Chart", "Two similar swing lows separated by a bounce. Confirmation normally requires a neckline/resistance break."),
        PatternInfo("Ascending triangle", "Chart", "Relatively flat resistance with rising lows. A breakout can be bullish, but false breaks are common."),
        PatternInfo("Descending triangle", "Chart", "Relatively flat support with falling highs. A breakdown can be bearish, but false breaks are common."),
        PatternInfo("Symmetrical triangle", "Chart", "Falling highs and rising lows. Direction is confirmed by the breakout, not by the shape alone."),
        PatternInfo("Rising wedge", "Chart", "Both boundaries rise while the range contracts. Often bearish, especially after an extended rise."),
        PatternInfo("Falling wedge", "Chart", "Both boundaries fall while the range contracts. Often bullish, especially after an extended decline."),
        PatternInfo("Bull flag", "Chart", "Sharp bullish impulse followed by controlled pullback/consolidation. Breakout continuation requires confirmation."),
        PatternInfo("Bear flag", "Chart", "Sharp bearish impulse followed by controlled bounce/consolidation. Breakdown continuation requires confirmation."),
        PatternInfo("Pennant", "Chart", "Small converging consolidation following a strong impulse. Usually treated as a continuation candidate."),
        PatternInfo("Cup and handle", "Chart", "Rounded base followed by a smaller pullback. Breakout above the rim is the common confirmation."),
        PatternInfo("Support / resistance", "Structure", "Repeated reaction zones. Treat them as areas, not exact single-price lines."),
        PatternInfo("HH + HL", "Structure", "Higher highs and higher lows describe bullish market structure."),
        PatternInfo("LH + LL", "Structure", "Lower highs and lower lows describe bearish market structure."),
        PatternInfo("Breakout + retest", "Structure", "Price breaks a level and later tests it from the other side. Retests can fail, so invalidation matters."),
        PatternInfo("Liquidity sweep", "Structure", "Price briefly trades beyond an obvious swing level and reverses. Context and follow-through are essential."),
        PatternInfo("RSI divergence", "Indicator", "Price and RSI move in opposite swing directions. Divergence is an alert, not automatic reversal confirmation."),
        PatternInfo("MACD crossover", "Indicator", "MACD crossing its signal line can show momentum change. It is lagging and works better with trend/context."),
        PatternInfo("EMA crossover", "Indicator", "A faster EMA crosses a slower EMA. It can help define trend but often whipsaws in ranges."),
        PatternInfo("Bollinger squeeze", "Indicator", "Bands contract as volatility falls. Expansion often follows, but direction must be confirmed."),
        PatternInfo("VWAP", "Indicator", "Volume-weighted average price. Commonly used as an intraday fair-value reference when reliable volume exists."),
        PatternInfo("ATR", "Risk", "Average True Range estimates recent volatility and is useful for volatility-aware stops and sizing."),
        PatternInfo("ADX", "Indicator", "ADX measures trend strength, not direction. Values around 25+ are often treated as stronger trend conditions."),
        PatternInfo("Fibonacci retracement", "Tool", "Common retracement levels include 38.2%, 50%, and 61.8%. They are reference zones, not guaranteed reversal prices.")
    )
}
