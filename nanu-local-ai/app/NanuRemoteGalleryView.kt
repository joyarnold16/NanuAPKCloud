package com.example.llama

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class NanuRemoteGalleryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : GridLayout(context, attrs) {
    private var generation = 0

    init {
        columnCount = 2
        alignmentMode = ALIGN_BOUNDS
    }

    fun bind(text: String): Boolean {
        generation++
        val token = generation
        removeAllViews()
        val urls = PREVIEW.findAll(text).mapNotNull { match ->
            runCatching { URL(match.groupValues[1]) }.getOrNull()?.takeIf {
                it.protocol == "https" && it.host.lowercase(Locale.US) == ALLOWED_HOST
            }?.toString()
        }.distinct().take(4).toList()
        visibility = if (urls.isEmpty()) View.GONE else View.VISIBLE
        urls.forEachIndexed { index, url ->
            val image = ImageView(context).apply {
                contentDescription = "Reusable image result ${index + 1}"
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(ContextCompat.getColor(context, R.color.nanu_panel_2))
            }
            val params = LayoutParams(spec(index / 2), spec(index % 2, 1f)).apply {
                width = 0
                height = (150 * resources.displayMetrics.density).toInt()
                val margin = (4 * resources.displayMetrics.density).toInt()
                setMargins(margin, margin, margin, margin)
                setGravity(Gravity.FILL)
            }
            addView(image, params)
            val cached = cache.get(url)
            if (cached != null) image.setImageBitmap(cached) else executor.execute {
                val bitmap = runCatching { download(url) }.getOrNull()
                if (bitmap != null) cache.put(url, bitmap)
                post { if (generation == token && bitmap != null) image.setImageBitmap(bitmap) }
            }
        }
        return urls.isNotEmpty()
    }

    private fun download(value: String): Bitmap {
        val url = URL(value)
        require(url.protocol == "https" && url.host.lowercase(Locale.US) == ALLOWED_HOST)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "NanuLocalAI/1.0 Android")
        }
        try {
            require(connection.responseCode in 200..299) { "Image source returned HTTP ${connection.responseCode}" }
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= MAX_BYTES) { "Image preview is too large" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "Image preview is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Image preview could not be decoded")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ALLOWED_HOST = "upload.wikimedia.org"
        private const val MAX_BYTES = 5 * 1024 * 1024
        private val PREVIEW = Regex("(?im)^Preview:\\s*(https://[^\\s]+)")
        private val executor = Executors.newFixedThreadPool(2)
        private val cache = object : LruCache<String, Bitmap>(12) {}
    }
}
