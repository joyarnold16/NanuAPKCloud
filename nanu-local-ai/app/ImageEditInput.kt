package com.example.llama

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class SelectedImage(val path: String, val width: Int, val height: Int)

/** Copies only the selected image into private storage, with orientation applied and metadata removed. */
class ImageEditInput(private val context: Context) {
    private val directory get() = File(context.filesDir, "image_inputs").also { it.mkdirs() }

    fun import(uri: Uri): SelectedImage {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0) { "This image has invalid dimensions." }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, 2048.0 / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
        }
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not save selected image." } }
            return SelectedImage(file.absolutePath, bitmap.width, bitmap.height)
        } catch (e: Exception) { file.delete(); throw e }
        finally { bitmap.recycle() }
    }

    fun restore(path: String?): SelectedImage? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val file = requireInput(path)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
            SelectedImage(file.absolutePath, bounds.outWidth, bounds.outHeight)
        }.getOrNull()
    }

    private fun requireInput(path: String): File {
        val file = File(path).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.isFile && file.extension == "png") { "Selected photo is unavailable. Add it again." }
        return file
    }

    /** Exact model-sized input; a changed aspect uses a center crop, never a stretch. */
    fun prepare(path: String, width: Int, height: Int): File {
        val file = requireInput(path)
        require(width in 64..768 && height in 64..768 && width % 8 == 0 && height % 8 == 0)
        val source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = File(File(context.cacheDir, "image_tasks").also { it.mkdirs() }, "${UUID.randomUUID()}.png")
        try {
            val canvas = Canvas(target)
            canvas.drawColor(Color.WHITE)
            val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
            val w = source.width * scale
            val h = source.height * scale
            canvas.drawBitmap(source, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
            output.outputStream().use { check(target.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return output
        } catch (e: Exception) { output.delete(); throw e }
        finally { source.recycle(); target.recycle() }
    }

    companion object {
        fun dimensions(width: Int, height: Int, quality: Boolean): Pair<Int, Int> {
            require(width > 0 && height > 0)
            val side = if (quality) 512 else 384
            val scale = side.toDouble() / max(width, height)
            fun aligned(value: Int) = ((value * scale / 8).roundToInt() * 8).coerceIn(64, side)
            return aligned(width) to aligned(height)
        }
    }
}

/** Arguments verified against the pinned sd-cli's --init-img / --strength options. */
object ImageEditArguments {
    fun savedOptions(request: org.json.JSONObject): String = org.json.JSONObject().apply {
        for (key in listOf("inputImage", "strength", "quality", "width", "height", "steps", "negative", "aspect")) {
            if (request.has(key) && !request.isNull(key)) put(key, request.get(key))
        }
    }.toString()
    fun forInput(path: String?, strength: Double): List<String> {
        if (path == null) return emptyList()
        require(path.isNotBlank()) { "Choose a source image." }
        require(strength.isFinite() && strength in 0.1..0.9) { "Change strength must be between 10% and 90%." }
        return listOf("--init-img", path, "--strength", String.format(java.util.Locale.US, "%.2f", strength))
    }
}
