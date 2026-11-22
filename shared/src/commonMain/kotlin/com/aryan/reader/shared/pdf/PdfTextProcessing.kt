package com.aryan.reader.shared.pdf

data class PdfProcessedText(
    val cleanText: String,
    val indexMap: List<Int>
) {
    /**
     * Maps a [start, endExclusive) range in [cleanText] back to raw source offsets
     * (end exclusive). Returns null when no clean character falls inside the range.
     */
    fun rawRange(start: Int, endExclusive: Int): Pair<Int, Int>? {
        if (indexMap.isEmpty()) return null
        val first = start.coerceIn(0, indexMap.lastIndex)
        val last = (endExclusive - 1).coerceIn(first, indexMap.lastIndex)
        return indexMap[first] to indexMap[last] + 1
    }
}

data class PdfOcrWord(
    val text: String,
    val bounds: PdfPageBounds?
)

object PdfTextProcessing {
    /**
     * Normalizes raw Pdfium text for TTS.
     *
     * A line break is either structural (end of paragraph, heading, list item - kept as `\n` so
     * the chunker pauses there) or a soft wrap (replaced by a space). Sentence-final punctuation
     * and blank lines are always structural; [hardBreakOffsets] adds font-derived boundaries such
     * as headings that carry no punctuation. Every line break keeps a separator, so two sentences
     * are never glued together (`"End.\nNext"` becomes `"End.\nNext"` or `"End. Next"`, never
     * `"End.Next"`).
     */
    fun preprocessForTts(
        rawText: String,
        hardBreakOffsets: Set<Int> = emptySet()
    ): PdfProcessedText {
        if (rawText.isBlank()) return PdfProcessedText("", emptyList())

        val cleanText = StringBuilder(rawText.length)
        val indexMap = mutableListOf<Int>()
        var lineStart = 0
        rawText.forEachIndexed { index, char ->
            when (char) {
                '\n' -> {
                    if (isNewlineInsideHyphenatedWord(rawText, index)) {
                        removeTrailingHyphenFragment(cleanText, indexMap)
                    } else {
                        val line = rawText.substring(lineStart, index)
                        if (index in hardBreakOffsets || line.isBlank() || endsSentence(line)) {
                            appendHardBreak(cleanText, indexMap, index)
                        } else {
                            appendSoftBreak(cleanText, indexMap, index)
                        }
                    }
                    lineStart = index + 1
                }
                '\r' -> Unit
                else -> {
                    cleanText.append(char)
                    indexMap += index
                }
            }
        }
        return trimProcessed(cleanText, indexMap)
    }

    /**
     * True when [text] ends a sentence: optional trailing whitespace and closing quotes or
     * brackets after `.`, `?`, `!` or an ellipsis.
     */
    fun endsSentence(text: String): Boolean {
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        while (end > 0 && text[end - 1] in TRAILING_CLOSERS) end--
        if (end == 0) return false
        val last = text[end - 1]
        return last == '.' || last == '?' || last == '!' || last == '\u2026'
    }

    private fun appendHardBreak(
        cleanText: StringBuilder,
        indexMap: MutableList<Int>,
        newlineIndex: Int
    ) {
        while (cleanText.isNotEmpty() && cleanText.last() == ' ') {
            cleanText.deleteAt(cleanText.length - 1)
            if (indexMap.isNotEmpty()) indexMap.removeAt(indexMap.size - 1)
        }
        if (cleanText.isEmpty() || cleanText.last() == '\n') return
        cleanText.append('\n')
        indexMap += newlineIndex
    }

    private fun appendSoftBreak(
        cleanText: StringBuilder,
        indexMap: MutableList<Int>,
        newlineIndex: Int
    ) {
        if (cleanText.isEmpty() || cleanText.last().isWhitespace()) return
        cleanText.append(' ')
        indexMap += newlineIndex
    }

    private fun trimProcessed(
        cleanText: StringBuilder,
        indexMap: MutableList<Int>
    ): PdfProcessedText {
        var start = 0
        var end = cleanText.length
        while (start < end && cleanText[start].isWhitespace()) start++
        while (end > start && cleanText[end - 1].isWhitespace()) end--
        if (start == 0 && end == cleanText.length) {
            return PdfProcessedText(cleanText.toString(), indexMap)
        }
        val text = cleanText.substring(start, end)
        val trimmedMap = indexMap.subList(start, end).toList()
        return PdfProcessedText(text, trimmedMap)
    }

    /**
     * Removes hyphens that typographically split a word across a line break
     * ("understand-\ning" -> "understanding") while leaving every other character,
     * including regular line breaks, untouched. Heuristic: the break hyphen must sit
     * between two letters, and the word continuation must start right after the line
     * break. Hyphenated compounds broken at an existing hyphen ("mother-in-\nlaw")
     * are indistinguishable from soft wraps without a dictionary and get joined too.
     */
    fun joinHyphenatedLineBreaks(text: String): String = joinHyphenatedLineBreaksMapped(text).cleanText

    /** [joinHyphenatedLineBreaks] with a clean-to-source index map for offset remapping. */
    fun joinHyphenatedLineBreaksMapped(text: String): PdfProcessedText {
        val result = StringBuilder(text.length)
        val indexMap = mutableListOf<Int>()
        text.forEachIndexed { index, char ->
            if (char == '\n' && isNewlineInsideHyphenatedWord(text, index)) {
                removeTrailingHyphenFragment(result, indexMap)
            } else {
                result.append(char)
                indexMap += index
            }
        }
        return PdfProcessedText(result.toString(), indexMap)
    }

    private fun isNewlineInsideHyphenatedWord(text: String, newlineIndex: Int): Boolean {
        val after = text.getOrNull(newlineIndex + 1) ?: return false
        if (!after.isLetter()) return false

        var cursor = newlineIndex - 1
        while (cursor >= 0 && text[cursor] != '\n' && text[cursor].isWhitespace()) cursor--
        if (cursor < 0 || !isBreakHyphen(text[cursor])) return false

        val before = text.getOrNull(cursor - 1) ?: return false
        return before.isLetter()
    }

    private fun removeTrailingHyphenFragment(cleanText: StringBuilder, indexMap: MutableList<Int>) {
        var removed = 0
        while (cleanText.isNotEmpty() &&
            (cleanText.last().isWhitespace() || isBreakHyphen(cleanText.last()))
        ) {
            cleanText.deleteAt(cleanText.length - 1)
            removed++
        }
        repeat(removed) {
            if (indexMap.isNotEmpty()) indexMap.removeAt(indexMap.size - 1)
        }
    }

    private fun isBreakHyphen(char: Char): Boolean =
        char == '-' || char == '\u2010' || char == '\u00AD'

    fun mergeScreenBoundsIntoLines(bounds: List<PdfPageBounds>): List<PdfPageBounds> {
        if (bounds.isEmpty()) return emptyList()
        val merged = mutableListOf<PdfPageBounds>()
        var current: PdfPageBounds? = null
        // Sorted by ascending top because this coordinate space is y-down.
        bounds.sortedWith(compareBy<PdfPageBounds> { it.top }.thenBy { it.left }).forEach { next ->
            val line = current
            if (line == null) {
                current = next
            } else {
                // Same majority-overlap test as mergePdfBoundsIntoLines. The previous test here was
                // plain `any positive overlap`, which is looser still and merged two text lines even
                // more readily than the PDF variant - which is why the live selection could draw a
                // different shape from the committed highlight.
                val overlapHeight = minOf(line.bottom, next.bottom) - maxOf(line.top, next.top)
                val minHeight = minOf(line.bottom - line.top, next.bottom - next.top)
                if (overlapHeight > 0f && overlapHeight >= minHeight * PDF_SAME_LINE_MIN_OVERLAP) {
                    current = PdfPageBounds(
                        left = minOf(line.left, next.left),
                        top = minOf(line.top, next.top),
                        right = maxOf(line.right, next.right),
                        bottom = maxOf(line.bottom, next.bottom)
                    )
                } else {
                    merged += line
                    current = next
                }
            }
        }
        current?.let(merged::add)
        return merged
    }

    fun mergePdfBoundsIntoLines(bounds: List<PdfPageBounds>): List<PdfPageBounds> {
        if (bounds.isEmpty()) return emptyList()
        val normalized = bounds.map { boundsForRect ->
            PdfPageBounds(
                left = minOf(boundsForRect.left, boundsForRect.right),
                top = minOf(boundsForRect.top, boundsForRect.bottom),
                right = maxOf(boundsForRect.left, boundsForRect.right),
                bottom = maxOf(boundsForRect.top, boundsForRect.bottom)
            )
        }
        val merged = mutableListOf<PdfPageBounds>()
        var current: PdfPageBounds? = null
        // Sorted by descending bottom because this coordinate space is y-up.
        normalized.sortedWith(compareBy<PdfPageBounds> { -it.bottom }.thenBy { it.left }).forEach { next ->
            val line = current
            if (line == null) {
                current = next
            } else {
                // Two rects belong to the same text line when they share most of their vertical
                // band. The previous test was `overlap > 0 && overlap >= 10% of the shorter box`,
                // which also accepted the ~1.3pt overlap that tight leading produces between two
                // adjacent lines: the pair merged into one box spanning both lines, and because a
                // decoration stroke is placed relative to the box (underline at 86% of height) the
                // single stroke landed under the *second* line only, leaving the first line with no
                // stroke at all. Requiring a majority overlap keeps adjacent lines apart.
                val overlapHeight = minOf(line.bottom, next.bottom) - maxOf(line.top, next.top)
                val minHeight = minOf(line.bottom - line.top, next.bottom - next.top)
                if (overlapHeight > 0f && overlapHeight >= minHeight * PDF_SAME_LINE_MIN_OVERLAP) {
                    current = PdfPageBounds(
                        left = minOf(line.left, next.left),
                        top = minOf(line.top, next.top),
                        right = maxOf(line.right, next.right),
                        bottom = maxOf(line.bottom, next.bottom)
                    )
                } else {
                    merged += line.toPdfCoordinateBounds()
                    current = next
                }
            }
        }
        current?.let { merged += it.toPdfCoordinateBounds() }
        return merged
    }

    fun findOcrWordSequence(words: List<PdfOcrWord>, textChunk: String): List<PdfPageBounds> {
        if (textChunk.isBlank() || words.isEmpty()) return emptyList()
        val targetWords = textChunk.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (targetWords.isEmpty() || targetWords.size > words.size) return emptyList()

        for (start in 0..words.size - targetWords.size) {
            val matchedBounds = mutableListOf<PdfPageBounds>()
            var matches = true
            for (offset in targetWords.indices) {
                val word = words[start + offset]
                if (!wordsMatch(word.text, targetWords[offset])) {
                    matches = false
                    break
                }
                word.bounds?.let(matchedBounds::add)
            }
            if (matches) return matchedBounds
        }
        return emptyList()
    }

    private fun wordsMatch(ocrWord: String, targetWord: String): Boolean {
        return ocrWord.equals(targetWord, ignoreCase = true) ||
            ocrWord.replace(TrailingOcrPunctuation, "").equals(targetWord, ignoreCase = true) ||
            targetWord.replace(TrailingTargetPunctuation, "").equals(ocrWord, ignoreCase = true)
    }

    private fun PdfPageBounds.toPdfCoordinateBounds(): PdfPageBounds =
        PdfPageBounds(left = left, top = bottom, right = right, bottom = top)

    private val TrailingOcrPunctuation = Regex("[.,;:!?\"')$]")
    private val TrailingTargetPunctuation = Regex("[.,;:!?\"'(]$")
    private const val TRAILING_CLOSERS = "\"')]}\u201D\u2019\u00BB\u203A"
}
