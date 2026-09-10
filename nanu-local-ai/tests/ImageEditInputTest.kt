package com.example.llama

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEditInputTest {
    private fun fixture(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File {
        val file = File.createTempFile("photo", if (format == Bitmap.CompressFormat.JPEG) ".jpg" else ".png", RuntimeEnvironment.getApplication().cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { assertTrue(bitmap.compress(format, 95, it)) }
        bitmap.recycle()
        return file
    }
    @Test fun importKeepsOriginalAndCreatesIndependentPng() {
        val context = RuntimeEnvironment.getApplication()
        val source = fixture(80, 40)
        val before = source.readBytes()
        val inputs = ImageEditInput(context)
        val selected = inputs.import(Uri.fromFile(source))
        assertEquals(80, selected.width)
        assertEquals(40, selected.height)
        assertNotEquals(source.absolutePath, selected.path)
        assertArrayEquals(before, source.readBytes())
        val dimensions = ImageEditInput.dimensions(selected.width, selected.height, false)
        assertEquals(384 to 192, dimensions)
        val prepared = inputs.prepare(selected.path, 384, 192)
        BitmapFactory.decodeFile(prepared.path).let { bitmap ->
            assertEquals(384, bitmap.width); assertEquals(192, bitmap.height); bitmap.recycle()
        }
        assertTrue(File(selected.path).exists())
        assertNotEquals(selected.path, prepared.path)
        assertEquals(selected, inputs.restore(selected.path))
    }
    @Test fun appliesCameraOrientation() {
        val source = fixture(80, 40, Bitmap.CompressFormat.JPEG)
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val selected = ImageEditInput(RuntimeEnvironment.getApplication()).import(Uri.fromFile(source))
        assertEquals(40, selected.width)
        assertEquals(80, selected.height)
    }
    @Test fun largeImportIsBoundedAndInvalidInputsAreRejected() {
        val context = RuntimeEnvironment.getApplication()
        val inputs = ImageEditInput(context)
        val selected = inputs.import(Uri.fromFile(fixture(2400, 1200)))
        assertEquals(2048, selected.width)
        assertEquals(1024, selected.height)
        val bad = File(context.cacheDir, "not-an-image.jpg").apply { writeText("invalid image") }
        try { inputs.import(Uri.fromFile(bad)); fail("Invalid image must be rejected") } catch (_: Exception) { }
        assertNull(inputs.restore(bad.path))
        try { inputs.prepare(bad.path, 384, 384); fail("Only imported image paths may reach the engine") } catch (_: IllegalArgumentException) { }
    }
    @Test fun sourceShapeAndSelectedCropHaveValidEngineDimensions() {
        assertEquals(288 to 512, ImageEditInput.dimensions(900, 1600, true))
        val inputs = ImageEditInput(RuntimeEnvironment.getApplication())
        val selected = inputs.import(Uri.fromFile(fixture(80, 40)))
        val cropped = inputs.prepare(selected.path, 288, 512)
        BitmapFactory.decodeFile(cropped.path).let { bitmap ->
            assertEquals(288, bitmap.width); assertEquals(512, bitmap.height); bitmap.recycle()
        }
    }
    @Test fun editArgumentsUsePinnedFlagsAndLocaleIndependentStrength() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(listOf("--init-img", "/private/photo name.png", "--strength", "0.45"), ImageEditArguments.forInput("/private/photo name.png", 0.45))
            assertTrue(ImageEditArguments.forInput(null, 0.45).isEmpty())
            for (value in listOf(Double.NaN, -1.0, 1.0)) {
                try { ImageEditArguments.forInput("photo.png", value); fail("Invalid strength must be rejected") } catch (_: IllegalArgumentException) { }
            }
        } finally { Locale.setDefault(old) }
    }
}
