package com.example.llama

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile


data class NanuAttachment(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val extractedText: String?
) {
    val extension: String
        get() = displayName.substringAfterLast('.', "").lowercase(Locale.US)

    val isImage: Boolean
        get() = mimeType.startsWith("image/") || extension in setOf("png", "jpg", "jpeg", "webp", "bmp")

    fun contextForPrompt(maxChars: Int = 12000): String {
        val text = extractedText?.trim().orEmpty()
        if (text.isBlank()) {
            return "Attached file: $displayName (${mimeType.ifBlank { "unknown type" }}). The file is attached locally, but no readable text could be extracted from it."
        }
        val clipped = text.take(maxChars)
        val suffix = if (text.length > clipped.length) "\n[Document text truncated by Nanu for local context.]" else ""
        return "Attached file: $displayName\n\n$clipped$suffix"
    }
}

class AttachmentManager(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "attachments").also { if (!it.exists()) it.mkdirs() }

    init {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    fun import(uri: Uri): NanuAttachment {
        val resolver = context.contentResolver
        var displayName = "attachment-${System.currentTimeMillis()}"
        var reportedSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) reportedSize = cursor.getLong(sizeIndex)
            }
        }

        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
        val target = File(directory, "${System.currentTimeMillis()}-$safeName")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        } ?: error("Unable to open selected file")

        val mime = resolver.getType(uri).orEmpty()
        val extracted = runCatching { extractText(target, displayName, mime) }.getOrNull()
        return NanuAttachment(
            displayName = displayName,
            mimeType = mime,
            sizeBytes = if (reportedSize >= 0L) reportedSize else target.length(),
            localPath = target.absolutePath,
            extractedText = extracted?.take(MAX_EXTRACTED_CHARS)
        )
    }

    private fun extractText(file: File, displayName: String, mime: String): String? {
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        return when {
            mime.startsWith("text/") || ext in setOf("txt", "csv", "json", "md", "log", "xml", "html", "htm") ->
                file.readText(Charsets.UTF_8)
            ext == "pdf" || mime == "application/pdf" -> extractPdf(file)
            ext == "docx" -> extractDocx(file)
            ext == "xlsx" -> extractXlsx(file)
            ext == "pptx" -> extractPptx(file)
            else -> null
        }
    }

    private fun extractPdf(file: File): String = PDDocument.load(file).use { document ->
        PDFTextStripper().getText(document)
    }

    private fun extractDocx(file: File): String = ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return@use ""
        val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        decodeXml(
            xml.replace(Regex("</w:p>"), "\n")
                .replace(Regex("<w:tab[^>]*/>"), "\t")
                .replace(Regex("<[^>]+>"), "")
        )
    }

    private fun extractPptx(file: File): String = ZipFile(file).use { zip ->
        val slides = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.name }
            .toList()
        buildString {
            slides.forEachIndexed { index, entry ->
                if (index > 0) append("\n\n")
                append("Slide ${index + 1}:\n")
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val parts = Regex("<a:t[^>]*>(.*?)</a:t>", setOf(RegexOption.DOT_MATCHES_ALL))
                    .findAll(xml)
                    .map { decodeXml(it.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
                append(parts.joinToString("\n"))
            }
        }
    }

    private fun extractXlsx(file: File): String = ZipFile(file).use { zip ->
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            Regex("<t(?:\\s[^>]*)?>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL))
                .findAll(xml)
                .map { decodeXml(it.groupValues[1]) }
                .toList()
        }.orEmpty()

        val sheetEntries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { it.name }
            .take(4)
            .toList()

        buildString {
            sheetEntries.forEachIndexed { sheetIndex, entry ->
                if (sheetIndex > 0) append("\n\n")
                append("Sheet ${sheetIndex + 1}:\n")
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("<row[^>]*>(.*?)</row>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml).take(500).forEach { rowMatch ->
                    val row = rowMatch.groupValues[1]
                    val values = Regex("<c([^>]*)>(.*?)</c>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(row).map { cell ->
                        val attrs = cell.groupValues[1]
                        val body = cell.groupValues[2]
                        val inline = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL)).find(body)?.groupValues?.get(1)
                        if (inline != null) {
                            decodeXml(inline)
                        } else {
                            val raw = Regex("<v>(.*?)</v>", setOf(RegexOption.DOT_MATCHES_ALL)).find(body)?.groupValues?.get(1).orEmpty()
                            if (attrs.contains("t=\"s\"")) raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw else raw
                        }
                    }.toList()
                    append(values.joinToString("\t")).append('\n')
                }
            }
        }
    }

    private fun decodeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    companion object {
        private const val MAX_EXTRACTED_CHARS = 80_000
    }
}
