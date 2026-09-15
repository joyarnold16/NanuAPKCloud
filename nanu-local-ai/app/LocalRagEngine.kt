package com.example.llama

import java.util.Locale
import kotlin.math.ln

data class RagDocument(val id: String, val name: String, val text: String)

data class RagHit(
    val sourceId: String,
    val sourceName: String,
    val section: Int,
    val text: String,
    val score: Double
) {
    val citation: String get() = "[Source: $sourceName §$section]"
}

data class RagResult(val hits: List<RagHit>) {
    val context: String = hits.joinToString("\n\n") { "${it.citation}\n${it.text}\n[End source]" }
    val citations: List<String> = hits.map { it.citation }.distinct()
}

/**
 * Dependency-free, on-device sparse retrieval for Nanu projects.
 *
 * This is deliberately deterministic: documents never leave the device, no embedding API is
 * required, and source text is returned in bounded chunks with stable citations. The local LLM
 * receives only the best matching chunks instead of the first pages of every selected file.
 */
object LocalRagEngine {
    private const val CHUNK_CHARS = 1_200
    private const val OVERLAP_CHARS = 180
    private val tokenPattern = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}_'-]*")
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "how", "i",
        "in", "is", "it", "of", "on", "or", "that", "the", "this", "to", "was", "what",
        "when", "where", "which", "who", "why", "with", "you", "your"
    )

    fun retrieve(
        query: String,
        documents: List<RagDocument>,
        maxChunks: Int = 6,
        charBudget: Int = 12_000,
        maxChunksPerSource: Int = 2
    ): RagResult {
        require(query.trim().isNotEmpty()) { "Enter a question before searching project memory." }
        require(maxChunks in 1..12) { "RAG chunk limit must be between 1 and 12." }
        require(charBudget in 1_000..30_000) { "RAG context budget must be between 1,000 and 30,000 characters." }
        require(maxChunksPerSource in 1..6) { "Per-source RAG limit must be between 1 and 6." }

        val chunks = documents.asSequence()
            .filter { it.text.isNotBlank() }
            .flatMap { document -> chunk(document).asSequence() }
            .toList()
        if (chunks.isEmpty()) return RagResult(emptyList())

        val rawQueryTokens = tokens(query)
        val queryTokens = rawQueryTokens.filterNot(stopWords::contains).ifEmpty { rawQueryTokens }.distinct()
        if (queryTokens.isEmpty()) return RagResult(emptyList())

        val tokenSets = chunks.map { tokens(it.text).toSet() }
        val documentFrequency = queryTokens.associateWith { term -> tokenSets.count { term in it } }
        val averageLength = chunks.map { tokens(it.text).size.coerceAtLeast(1) }.average().coerceAtLeast(1.0)
        val normalizedQuery = normalize(query)

        val ranked = chunks.mapNotNull { chunk ->
            val chunkTokens = tokens(chunk.text)
            if (chunkTokens.isEmpty()) return@mapNotNull null
            val counts = chunkTokens.groupingBy { it }.eachCount()
            var score = 0.0
            var matched = 0
            for (term in queryTokens) {
                val frequency = counts[term] ?: continue
                matched++
                val df = documentFrequency[term] ?: 0
                val idf = ln(1.0 + (chunks.size - df + 0.5) / (df + 0.5))
                val lengthNorm = 1.2 * (1.0 - 0.75 + 0.75 * chunkTokens.size / averageLength)
                score += idf * (frequency * 2.2) / (frequency + lengthNorm)
            }
            if (matched == 0) return@mapNotNull null

            val normalizedChunk = normalize(chunk.text)
            if (normalizedQuery.length >= 5 && normalizedChunk.contains(normalizedQuery)) score += 4.0
            score += 2.0 * matched / queryTokens.size
            val nameTokens = tokens(chunk.sourceName).toSet()
            score += queryTokens.count(nameTokens::contains) * 0.8
            chunk.copy(score = score)
        }.sortedWith(compareByDescending<RagHit> { it.score }.thenBy { it.sourceName }.thenBy { it.section })

        val candidateLimit = minOf(maxChunks, charBudget / 200)
        val candidates = mutableListOf<RagHit>()
        val perSource = mutableMapOf<String, Int>()

        // Prefer one strong excerpt from every matching source before taking second excerpts.
        for (hit in ranked) {
            if (candidates.size >= candidateLimit) break
            if ((perSource[hit.sourceId] ?: 0) != 0) continue
            candidates += hit
            perSource[hit.sourceId] = 1
        }
        for (hit in ranked) {
            if (candidates.size >= candidateLimit) break
            if (hit in candidates) continue
            if ((perSource[hit.sourceId] ?: 0) >= maxChunksPerSource) continue
            candidates += hit
            perSource[hit.sourceId] = (perSource[hit.sourceId] ?: 0) + 1
        }

        val selected = mutableListOf<RagHit>()
        var used = 0
        candidates.forEachIndexed { index, hit ->
            val remaining = charBudget - used
            val remainingSlots = candidates.size - index
            val allowance = remaining / remainingSlots
            val bounded = if (hit.text.length <= allowance) {
                hit
            } else {
                hit.copy(text = hit.text.take((allowance - 1).coerceAtLeast(1)).trimEnd() + "…")
            }
            selected += bounded
            used += bounded.text.length
        }
        return RagResult(selected)
    }

    private fun chunk(document: RagDocument): List<RagHit> {
        val text = document.text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return emptyList()
        val sourceName = document.name
            .replace(Regex("[\\r\\n\\[\\]]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
            .ifBlank { "Untitled" }
        val result = mutableListOf<RagHit>()
        var start = 0
        var section = 1
        while (start < text.length) {
            val hardEnd = (start + CHUNK_CHARS).coerceAtMost(text.length)
            var end = hardEnd
            if (hardEnd < text.length) {
                val minimumBreak = start + CHUNK_CHARS * 3 / 5
                val newline = text.lastIndexOf('\n', hardEnd).takeIf { it >= minimumBreak }
                val sentence = text.lastIndexOf(". ", hardEnd).takeIf { it >= minimumBreak }?.plus(1)
                end = listOfNotNull(newline, sentence).maxOrNull() ?: hardEnd
            }
            val value = text.substring(start, end).replace(Regex("[ \\t]+"), " ").trim()
            if (value.isNotEmpty()) result += RagHit(document.id, sourceName, section++, value, 0.0)
            if (end >= text.length) break
            val next = (end - OVERLAP_CHARS).coerceAtLeast(start + 1)
            start = text.indexOfAny(charArrayOf(' ', '\n'), next).takeIf { it in next until end }?.plus(1) ?: next
        }
        return result
    }

    private fun tokens(value: String): List<String> = tokenPattern.findAll(value.lowercase(Locale.ROOT))
        .map { it.value.trim('_', '\'', '-') }
        .filter { it.length >= 2 }
        .toList()

    private fun normalize(value: String): String = tokens(value).joinToString(" ")
}
