package com.example.llama

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Lightweight read-only reference prices for general current-information questions. */
data class MarketSnapshot(
    val symbol: String,
    val price: Double,
    val change24h: Double? = null,
    val source: String
)

object MarketSnapshotClient {
    private const val MAX_RESPONSE_CHARS = 100_000
    private val allowedHosts = setOf("api.coingecko.com", "api.frankfurter.dev")

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
        val url = "https://api.frankfurter.dev/v2/rate/${from.lowercase(Locale.US)}/${to.lowercase(Locale.US)}"
        val json = JSONObject(get(url))
        val price = json.getDouble("rate")
        return MarketSnapshot("$from/$to", price, null, "Frankfurter central-bank reference data")
    }

    private fun get(urlString: String): String {
        val requested = URL(urlString)
        require(requested.protocol == "https" && requested.host.lowercase(Locale.US) in allowedHosts) {
            "Market source is not allow-listed."
        }
        val connection = (requested.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NanuLocalAI/1.0")
        }
        try {
            val code = connection.responseCode
            val finalUrl = connection.url
            require(finalUrl.protocol == "https" && finalUrl.host.lowercase(Locale.US) in allowedHosts) {
                "Market source redirected outside Nanu's allow-list."
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4_096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    require(output.length + read <= MAX_RESPONSE_CHARS) { "Market source returned too much data." }
                    output.append(buffer, 0, read)
                }
                output.toString()
            }.orEmpty()
            if (code == 429) error("The free market source is temporarily rate-limited. Please try again shortly.")
            if (code in 300..399) error("Market source attempted a redirect, which Nanu blocks for privacy.")
            if (code !in 200..299) error("Market source returned HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
