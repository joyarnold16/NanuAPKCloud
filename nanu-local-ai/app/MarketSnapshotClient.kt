package com.example.llama

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Lightweight optional online market snapshots for the Trading Lab.
 * Technical calculations remain local. These endpoints are for informational snapshots only.
 */
data class MarketSnapshot(
    val symbol: String,
    val price: Double,
    val change24h: Double? = null,
    val source: String
)

object MarketSnapshotClient {

    fun crypto(symbolInput: String): MarketSnapshot {
        val normalized = symbolInput.uppercase(Locale.US)
            .replace("/USDT", "")
            .replace("/USD", "")
            .replace("USDT", "")
            .replace("USD", "")
            .trim()

        val id = when (normalized) {
            "BTC" -> "bitcoin"
            "ETH" -> "ethereum"
            "SOL" -> "solana"
            "BNB" -> "binancecoin"
            "XRP" -> "ripple"
            "DOGE" -> "dogecoin"
            "ADA" -> "cardano"
            "AVAX" -> "avalanche-2"
            "LINK" -> "chainlink"
            "DOT" -> "polkadot"
            else -> error("Live snapshot currently supports BTC, ETH, SOL, BNB, XRP, DOGE, ADA, AVAX, LINK and DOT.")
        }

        val url = "https://api.coingecko.com/api/v3/simple/price?ids=$id&vs_currencies=usd&include_24hr_change=true"
        val json = JSONObject(get(url))
        val row = json.getJSONObject(id)
        val price = row.getDouble("usd")
        val change = if (row.has("usd_24h_change") && !row.isNull("usd_24h_change")) row.getDouble("usd_24h_change") else null
        return MarketSnapshot("$normalized/USD", price, change, "CoinGecko")
    }

    fun forex(symbolInput: String): MarketSnapshot {
        val clean = symbolInput.uppercase(Locale.US).replace("/", "").replace(" ", "")
        require(clean.length == 6) { "Use a 6-letter FX pair such as EURUSD or GBPJPY." }
        val from = clean.substring(0, 3)
        val to = clean.substring(3, 6)
        val url = "https://api.frankfurter.app/latest?from=$from&to=$to"
        val json = JSONObject(get(url))
        val rates = json.getJSONObject("rates")
        val price = rates.getDouble(to)
        return MarketSnapshot("$from/$to", price, null, "Frankfurter / ECB reference data")
    }

    private fun get(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NanuLocalAI/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Market source returned HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
