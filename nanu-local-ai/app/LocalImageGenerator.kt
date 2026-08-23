package com.example.llama

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


data class ImageGenerationResult(
    val file: File,
    val elapsedSeconds: Long,
    val gallerySaved: Boolean
)

class LocalImageGenerator(private val context: Context) {
    @Volatile private var activeProcess: Process? = null

    fun cancel() {
        val process = activeProcess ?: return
        runCatching { process.destroy() }
        runCatching {
            if (!process.waitFor(750, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        activeProcess = null
    }

    suspend fun generate(
        prompt: String,
        negativePrompt: String = "blurry, low quality, distorted, malformed",
        quality: Boolean = false,
        onProgress: suspend (String) -> Unit
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val manager = ImageModelManager(context)
        val model = ImageModelCatalog.starter
        val modelFile = manager.destinationFile(model)
        require(modelFile.exists() && manager.looksLikeGguf(modelFile)) { "Image model is not downloaded" }

        val native = File(context.applicationInfo.nativeLibraryDir, "libsd.so")
        require(native.exists()) { "Local image engine is missing from this build" }

        val outputDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "generated"
        ).also { if (!it.exists()) it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val output = File(outputDir, "nanu-$stamp.png")

        val size = if (quality) 512 else 384
        val steps = if (quality) 14 else 8
        val timeoutMs = if (quality) 60L * 60L * 1000L else 30L * 60L * 1000L
        val command = mutableListOf(
            native.absolutePath,
            "-m", modelFile.absolutePath,
            "-p", prompt,
            "-o", output.absolutePath,
            "--steps", steps.toString(),
            "-W", size.toString(),
            "-H", size.toString(),
            "--vae-tiling"
        )
        if (negativePrompt.isNotBlank()) command += listOf("-n", negativePrompt)

        val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
        processBuilder.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
        processBuilder.environment()["TMPDIR"] = context.cacheDir.absolutePath

        onProgress("Loading local image model…")
        val process = processBuilder.start()
        activeProcess = process
        val started = SystemClock.elapsedRealtime()
        var lastLine = "Starting image engine…"
        var lastUiUpdate = 0L

        try {
            process.inputStream.bufferedReader().use { reader ->
                while (process.isAlive) {
                    while (reader.ready()) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) lastLine = line.takeLast(160)
                    }
                    val elapsed = SystemClock.elapsedRealtime() - started
                    if (elapsed - lastUiUpdate >= 1500L) {
                        lastUiUpdate = elapsed
                        val seconds = elapsed / 1000L
                        onProgress("Generating locally • ${seconds}s\n$lastLine")
                    }
                    if (elapsed >= timeoutMs) {
                        cancel()
                        error("Image generation timed out after ${timeoutMs / 60000L} minutes")
                    }
                    delay(250L)
                }
                while (reader.ready()) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) lastLine = line.takeLast(160)
                }
            }

            val exit = process.waitFor()
            if (exit != 0 || !output.exists() || output.length() == 0L) {
                error("Image engine exited with code $exit. $lastLine")
            }
            val elapsedSeconds = ((SystemClock.elapsedRealtime() - started) / 1000L).coerceAtLeast(1L)
            val gallerySaved = saveToGallery(output)
            ImageGenerationResult(output, elapsedSeconds, gallerySaved)
        } finally {
            activeProcess = null
        }
    }

    private fun saveToGallery(source: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nanu")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }
}
