package com.example.llama

import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Read-only, key-free online sources used by Nanu's bounded agent.
 *
 * Every destination is hard-coded and every response is size bounded. User prompts are never
 * uploaded wholesale: only the short city, symbol or search query supplied to the selected tool.
 */
object OnlineToolClient {
    private const val MAX_QUERY_CHARS = 300
    private const val MAX_RESPONSE_CHARS = 600_000
    private val timestampFormat = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm:ss z", Locale.US)
    private val allowedHosts = setOf(
        "api.open-meteo.com",
        "geocoding-api.open-meteo.com",
        "html.duckduckgo.com",
        "news.google.com",
        "commons.wikimedia.org"
    )

    private data class Place(
        val name: String,
        val region: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val timezone: String
    ) {
        val label: String = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    fun currentTime(locationInput: String): String {
        val place = geocode(locationInput)
        val zone = runCatching { ZoneId.of(place.timezone) }.getOrElse { error("The time zone for ${place.label} was not recognized.") }
        return buildString {
            append("Current time for ${place.label}: ${ZonedDateTime.now(zone).format(timestampFormat)}\n")
            append("Time zone: ${place.timezone}\n")
            append("Location source: Open-Meteo geocoding (https://open-meteo.com/)\n")
            append("Retrieved: ${retrievedAt()}")
        }
    }

    fun weather(locationInput: String): String {
        val place = geocode(locationInput)
        val query = "latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m" +
            "&timezone=auto"
        val sourceUrl = "https://api.open-meteo.com/v1/forecast?$query"
        val root = JSONObject(get(sourceUrl, "application/json"))
        val current = root.getJSONObject("current")
        val units = root.optJSONObject("current_units")
        val temperature = current.getDouble("temperature_2m")
        val apparent = current.optDouble("apparent_temperature", Double.NaN)
        val humidity = current.optDouble("relative_humidity_2m", Double.NaN)
        val precipitation = current.optDouble("precipitation", Double.NaN)
        val wind = current.optDouble("wind_speed_10m", Double.NaN)
        val code = current.optInt("weather_code", -1)
        return buildString {
            append("Current weather for ${place.label}\n")
            append("Condition: ${weatherDescription(code)}\n")
            append("Temperature: ${number(temperature)}${units?.optString("temperature_2m", "°C") ?: "°C"}\n")
            if (apparent.isFinite()) append("Feels like: ${number(apparent)}${units?.optString("apparent_temperature", "°C") ?: "°C"}\n")
            if (humidity.isFinite()) append("Humidity: ${number(humidity)}${units?.optString("relative_humidity_2m", "%") ?: "%"}\n")
            if (precipitation.isFinite()) append("Precipitation: ${number(precipitation)} ${units?.optString("precipitation", "mm") ?: "mm"}\n")
            if (wind.isFinite()) append("Wind: ${number(wind)} ${units?.optString("wind_speed_10m", "km/h") ?: "km/h"}\n")
            current.optString("time").takeIf { it.isNotBlank() }?.let { append("Observation time: $it (${place.timezone})\n") }
            append("Source: Open-Meteo (https://open-meteo.com/)\n")
            append("Retrieved: ${retrievedAt()}")
        }
    }

    fun webSearch(queryInput: String): String {
        val query = boundedQuery(queryInput)
        val sourceUrl = "https://html.duckduckgo.com/html/?q=${encoded(query)}&kl=in-en"
        val items = parseSearch(get(sourceUrl, "text/html")).take(6)
        require(items.isNotEmpty()) { "The free web lookup returned no readable results." }
        return buildString {
            append("Web results for: $query\n\n")
            items.forEachIndexed { index, item ->
                append("${index + 1}. ${item.title}\n")
                if (item.snippet.isNotBlank()) append("${item.snippet}\n")
                append("Link: ${item.link}\n\n")
            }
            append("Search source: DuckDuckGo HTML (https://duckduckgo.com/)\n")
            append("Retrieved: ${retrievedAt()}")
        }
    }

    fun news(queryInput: String): String {
        val query = boundedQuery(queryInput.ifBlank { "top news" })
        val url = "https://news.google.com/rss/search?q=${encoded(query)}&hl=en-IN&gl=IN&ceid=IN:en"
        val xml = get(url, "application/rss+xml, application/xml;q=0.9")
        val items = parseNews(xml).take(6)
        require(items.isNotEmpty()) { "No recent news items were returned for that query." }
        return buildString {
            append("Recent news results for: $query\n\n")
            items.forEachIndexed { index, item ->
                append("${index + 1}. ${item.title}\n")
                if (item.source.isNotBlank()) append("Publisher: ${item.source}\n")
                if (item.published.isNotBlank()) append("Published: ${item.published}\n")
                append("Link: ${item.link}\n\n")
            }
            append("Feed source: Google News RSS; articles belong to their publishers.\n")
            append("Retrieved: ${retrievedAt()}")
        }
    }

    fun imageSearch(queryInput: String): String {
        val query = boundedQuery(queryInput)
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
            "&gsrnamespace=6&gsrlimit=6&gsrsearch=${encoded(query)}" +
            "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=900&format=json&origin=*"
        val root = JSONObject(get(url, "application/json"))
        val pages = root.optJSONObject("query")?.optJSONObject("pages")
            ?: error("No reusable images were found for that request.")
        val results = mutableListOf<String>()
        val keys = pages.keys()
        while (keys.hasNext() && results.size < 4) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val preview = info.optString("thumburl").ifBlank { info.optString("url") }
            val pageUrl = info.optString("descriptionurl")
            if (!preview.startsWith("https://") || !pageUrl.startsWith("https://")) continue
            val metadata = info.optJSONObject("extmetadata")
            val license = metadata?.optJSONObject("LicenseShortName")?.optString("value").orEmpty().ifBlank { "Check source page" }
            val artist = stripHtml(metadata?.optJSONObject("Artist")?.optString("value").orEmpty()).take(140).ifBlank { "Unknown" }
            val title = page.optString("title").removePrefix("File:").take(180)
            results += "$title\nPreview: $preview\nSource and licence: $pageUrl\nLicence: $license\nCreator: $artist"
        }
        require(results.isNotEmpty()) { "No reusable images were found for that request." }
        return buildString {
            append("Reusable image results for: $query\n\n")
            results.forEachIndexed { index, result -> append("${index + 1}. $result\n\n") }
            append("Source: Wikimedia Commons. Check each file page before reuse.\n")
            append("Retrieved: ${retrievedAt()}")
        }
    }

    internal data class NewsItem(val title: String, val link: String, val source: String, val published: String)
    internal data class SearchItem(val title: String, val link: String, val snippet: String)

    internal fun parseSearch(html: String): List<SearchItem> {
        val titlePattern = Regex(
            """(?is)<a[^>]*class=[\"'][^\"']*result__a[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>"""
        )
        val snippetPattern = Regex(
            """(?is)<a[^>]*class=[\"'][^\"']*result__snippet[^\"']*[\"'][^>]*>(.*?)</a>"""
        )
        val titles = titlePattern.findAll(html).toList()
        return titles.mapIndexedNotNull { index, match ->
            val title = stripHtml(match.groupValues[2]).take(300)
            val link = decodeSearchLink(match.groupValues[1]) ?: return@mapNotNullIndexed null
            val nextStart = titles.getOrNull(index + 1)?.range?.first ?: html.length
            val block = html.substring(match.range.last + 1, nextStart)
            val snippet = snippetPattern.find(block)?.groupValues?.get(1)?.let(::stripHtml)
                ?.replace(Regex("(?i)ignore (all|any|previous) (instructions|prompts)"), "[untrusted instruction removed]")
                ?.take(600)
                .orEmpty()
            if (title.isBlank()) null else SearchItem(title, link, snippet)
        }.distinctBy { it.link }
    }

    internal fun parseNews(xmlText: String): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xmlText.reader())
        val items = mutableListOf<NewsItem>()
        var insideItem = false
        var title = ""
        var link = ""
        var source = ""
        var published = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                    "item" -> { insideItem = true; title = ""; link = ""; source = ""; published = "" }
                    "title" -> if (insideItem) title = parser.nextText().trim()
                    "link" -> if (insideItem) link = parser.nextText().trim()
                    "source" -> if (insideItem) source = parser.nextText().trim()
                    "pubdate" -> if (insideItem) published = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("item", true)) {
                    if (title.isNotBlank() && link.startsWith("https://")) items += NewsItem(title.take(300), link, source.take(120), published.take(120))
                    insideItem = false
                }
            }
            event = parser.next()
        }
        return items
    }

    internal fun boundedQuery(value: String): String {
        val query = value.replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex("\\s+"), " ").trim()
        require(query.isNotEmpty()) { "This online tool needs a search term or place name." }
        require(query.length <= MAX_QUERY_CHARS) { "Online queries are limited to $MAX_QUERY_CHARS characters." }
        return query
    }

    internal fun stripHtml(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeSearchLink(rawHref: String): String? {
        val href = rawHref.replace("&amp;", "&").let { if (it.startsWith("//")) "https:$it" else it }
        val uri = runCatching { URI(href) }.getOrNull() ?: return null
        val target = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "uddg" }
            ?.substringAfter('=', "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?: href
        val parsed = runCatching { URL(target) }.getOrNull() ?: return null
        return target.takeIf { parsed.protocol == "https" && parsed.host.isNotBlank() }
    }

    private fun geocode(locationInput: String): Place {
        val location = boundedQuery(locationInput)
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${encoded(location)}&count=1&language=en&format=json"
        val root = JSONObject(get(url, "application/json"))
        val row = root.optJSONArray("results")?.optJSONObject(0)
            ?: error("No location named '$location' was found.")
        return Place(
            name = row.optString("name"),
            region = row.optString("admin1"),
            country = row.optString("country"),
            latitude = row.getDouble("latitude"),
            longitude = row.getDouble("longitude"),
            timezone = row.optString("timezone").ifBlank { "UTC" }
        )
    }

    private fun get(urlString: String, accept: String): String {
        val requested = URL(urlString)
        require(requested.protocol == "https" && requested.host.lowercase(Locale.US) in allowedHosts) { "Online source is not allow-listed." }
        val connection = (requested.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "en-IN,en;q=0.8")
            setRequestProperty("User-Agent", "NanuLocalAI/1.0 (Android; read-only agent tool)")
        }
        try {
            val code = connection.responseCode
            val finalUrl = connection.url
            require(finalUrl.protocol == "https" && finalUrl.host.lowercase(Locale.US) in allowedHosts) { "Online source redirected outside Nanu's allow-list." }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8_192)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        require(out.length + read <= MAX_RESPONSE_CHARS) { "Online source returned too much data." }
                        out.append(buffer, 0, read)
                    }
                    out.toString()
                }
            }.orEmpty()
            if (code in 300..399) error("Online source attempted a redirect, which Nanu blocks for privacy.")
            if (code !in 200..299) error("Online source returned HTTP $code.")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun retrievedAt(): String = ZonedDateTime.now().format(timestampFormat)
    private fun number(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather code $code"
    }
}
