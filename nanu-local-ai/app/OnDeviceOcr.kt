package com.example.llama

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class OcrResult(val text: String, val method: String)

/** Bundled, network-free Latin and Devanagari OCR for images and scanned PDFs. */
class OnDeviceOcr(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun recognizeImage(file: File): OcrResult {
        require(file.isFile) { "Image file is unavailable" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Android could not decode this image" }
        val longest = max(options.outWidth, options.outHeight)
        var sample = 1
        while (longest / sample > MAX_IMAGE_EDGE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Android could not decode this image")
        return bitmap.useAndRecycle { original ->
            val prepared = resizeIfNeeded(original)
            if (prepared === original) recognize(original) else prepared.useAndRecycle(::recognize)
        }
    }

    fun recognizePdf(file: File, maxPages: Int = MAX_PDF_PAGES): OcrResult {
        require(file.isFile) { "PDF file is unavailable" }
        val pageTexts = mutableListOf<String>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount.coerceAtMost(maxPages)
                for (index in 0 until count) {
                    renderer.openPage(index).use { page ->
                        val scale = (MAX_PDF_EDGE.toFloat() / max(page.width, page.height)).coerceAtMost(2.0f)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val result = bitmap.useAndRecycle { recognize(it) }
                        if (result.text.isNotBlank()) pageTexts += "Page ${index + 1}:\n${result.text}"
                    }
                }
            }
        }
        return OcrResult(pageTexts.joinToString("\n\n"), "on-device OCR (${pageTexts.size} scanned page${if (pageTexts.size == 1) "" else "s"})")
    }

    private fun recognize(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        return try {
            val latinText = runCatching { Tasks.await(latin.process(image)).text }.getOrDefault("")
            val devanagariText = runCatching { Tasks.await(devanagari.process(image)).text }.getOrDefault("")
            chooseBest(latinText, devanagariText)
        } finally {
            latin.close()
            devanagari.close()
        }
    }

    private fun resizeIfNeeded(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= MAX_IMAGE_EDGE) return source
        val scale = MAX_IMAGE_EDGE.toFloat() / longest
        val matrix = Matrix().apply { setScale(scale, scale) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private inline fun <T> Bitmap.useAndRecycle(block: (Bitmap) -> T): T {
        return try {
            block(this)
        } finally {
            if (!isRecycled) recycle()
        }
    }

    companion object {
        private const val MAX_IMAGE_EDGE = 2048
        private const val MAX_PDF_EDGE = 1800
        private const val MAX_PDF_PAGES = 16

        internal fun chooseBest(latin: String, devanagari: String): OcrResult {
            fun score(value: String): Int = value.count { it.isLetterOrDigit() } +
                value.count { it.code in 0x0900..0x097F } * 2
            val normalizedLatin = normalize(latin)
            val normalizedDevanagari = normalize(devanagari)
            return if (score(normalizedDevanagari) > score(normalizedLatin)) {
                OcrResult(normalizedDevanagari, "on-device Devanagari OCR")
            } else {
                OcrResult(normalizedLatin, "on-device text OCR")
            }
        }

        private fun normalize(value: String): String = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
