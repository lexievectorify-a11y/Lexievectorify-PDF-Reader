package com.aryan.reader.shared.pdf

/**
 * Decides which raw Pdfium line breaks are structural (a paragraph or heading ends there) and
 * should stay as a `\n` in the text handed to TTS.
 *
 * Pdfium returns one line per visual line, so a paragraph wrapped over three lines looks exactly
 * like three lines of a list. Sentence-final punctuation is a reliable boundary on its own; font
 * data (when the native bridge is available) separates headings - which often carry no terminal
 * punctuation at all - from body text. Without font data we fall back to shape: short all-caps
 * lines and bullet/numbered lines.
 */
object PdfTtsBreakAnalysis {
    private const val HEADING_SIZE_RATIO = 1.12f
    private const val HEADING_WEIGHT_THRESHOLD = 600
    private const val HEADING_MAX_LENGTH = 80
    private const val HEADING_MAX_WORDS = 12
    private const val FALLBACK_MAX_LENGTH = 60
    private const val MIN_BODY_SAMPLES = 8

    private val NumberedLineRegex = Regex("""^(\d{1,3}[.)]|[A-Za-z][.)])\s+""")
    private val BulletPrefixes = listOf("- ", "* ", "\u2022", "\u25AA", "\u25E6", "\u2013 ", "\u2014 ")

    /**
     * Raw indices of the `\n` characters that end a structural line. [fontSizes] and
     * [fontWeights] are indexed by raw character offset, as returned by
     * `NativePdfiumBridge.getPageFontSizes` / `getPageFontWeights`; either may be null or shorter
     * than [rawText].
     */
    fun hardBreakOffsets(
        rawText: String,
        fontSizes: FloatArray? = null,
        fontWeights: IntArray? = null
    ): Set<Int> {
        if (rawText.isEmpty()) return emptySet()
        val bodySize = bodyFontSize(rawText, fontSizes)
        val breaks = mutableSetOf<Int>()
        var lineStart = 0
        var index = 0
        while (index <= rawText.length) {
            val atEnd = index == rawText.length
            if (atEnd || rawText[index] == '\n') {
                if (!atEnd) {
                    val line = rawText.substring(lineStart, index)
                    if (isHardBreakLine(line, lineStart, bodySize, fontSizes, fontWeights)) {
                        breaks += index
                    }
                }
                lineStart = index + 1
            }
            index++
        }
        return breaks
    }

    private fun isHardBreakLine(
        line: String,
        lineStart: Int,
        bodySize: Float?,
        fontSizes: FloatArray?,
        fontWeights: IntArray?
    ): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true
        if (PdfTextProcessing.endsSentence(trimmed)) return true
        if (!isHeadingShaped(trimmed)) return false

        val fontHeading = isFontHeading(line, lineStart, trimmed, bodySize, fontSizes, fontWeights)
        if (fontHeading == true) return true
        if (fontHeading == false) return false
        return isFallbackHeading(trimmed)
    }

    /** null when the line has no usable font data, so the caller can apply the shape fallback. */
    private fun isFontHeading(
        line: String,
        lineStart: Int,
        trimmed: String,
        bodySize: Float?,
        fontSizes: FloatArray?,
        fontWeights: IntArray?
    ): Boolean? {
        if (fontSizes == null && fontWeights == null) return null
        var maxSize = 0f
        var maxWeight = 0
        var saw = false
        line.forEachIndexed { offset, char ->
            if (char.isWhitespace()) return@forEachIndexed
            val index = lineStart + offset
            val size = fontSizes?.getOrNull(index) ?: 0f
            val weight = fontWeights?.getOrNull(index) ?: 0
            if (size > 0f) {
                maxSize = maxOf(maxSize, size)
                saw = true
            }
            if (weight > 0) {
                maxWeight = maxOf(maxWeight, weight)
                saw = true
            }
        }
        if (!saw) return null
        if (trimmed.isEmpty()) return false
        return (bodySize != null && maxSize > bodySize * HEADING_SIZE_RATIO) ||
            maxWeight > HEADING_WEIGHT_THRESHOLD
    }

    private fun isHeadingShaped(trimmed: String): Boolean {
        if (trimmed.length > HEADING_MAX_LENGTH) return false
        if (trimmed.none { it.isLetter() }) return false
        if (trimmed.endsWith(",") || trimmed.endsWith(";")) return false
        return trimmed.split(Regex("\\s+")).size <= HEADING_MAX_WORDS
    }

    private fun isFallbackHeading(trimmed: String): Boolean {
        if (trimmed.length !in 3..FALLBACK_MAX_LENGTH) return false
        if (trimmed.none { it.isLetter() }) return false
        if (isBulletLine(trimmed)) return true
        return isAllCapsHeading(trimmed)
    }

    private fun isBulletLine(trimmed: String): Boolean =
        BulletPrefixes.any { trimmed.startsWith(it) } || NumberedLineRegex.containsMatchIn(trimmed)

    private fun isAllCapsHeading(trimmed: String): Boolean =
        !trimmed.endsWith(".") &&
            trimmed.any { it.isLetter() } &&
            trimmed.all { it.isUpperCase() || !it.isLetter() }

    /** Median font size of non-whitespace characters; null when there is too little data. */
    private fun bodyFontSize(rawText: String, fontSizes: FloatArray?): Float? {
        if (fontSizes == null) return null
        val samples = ArrayList<Float>(fontSizes.size)
        val count = minOf(rawText.length, fontSizes.size)
        for (index in 0 until count) {
            if (rawText[index].isWhitespace()) continue
            val size = fontSizes[index]
            if (size > 0f) samples += size
        }
        if (samples.size < MIN_BODY_SAMPLES) return null
        samples.sort()
        return samples[samples.size / 2]
    }
}
