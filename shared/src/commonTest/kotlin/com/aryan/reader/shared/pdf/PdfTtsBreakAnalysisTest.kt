package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfTtsBreakAnalysisTest {
    @Test
    fun `larger font marks a heading line as a hard break`() {
        val raw = "Introduction\nBody text continues here and goes on.\nMore body."
        val sizes = FloatArray(raw.length) { index -> if (index < 12) 20f else 10f }

        val breaks = PdfTtsBreakAnalysis.hardBreakOffsets(raw, sizes, null)

        assertTrue(12 in breaks)
    }

    @Test
    fun `bold font marks an unpunctuated heading as a hard break`() {
        val raw = "Bold Term\nDefinition body sentence follows here."
        val weights = IntArray(raw.length) { index -> if (index < 9) 700 else 400 }

        val breaks = PdfTtsBreakAnalysis.hardBreakOffsets(raw, null, weights)

        assertTrue(9 in breaks)
    }

    @Test
    fun `soft wrapped body lines are not hard breaks`() {
        val raw = "This line is only a soft wrap\nand continues here."
        val sizes = FloatArray(raw.length) { 10f }

        val breaks = PdfTtsBreakAnalysis.hardBreakOffsets(raw, sizes, null)

        assertFalse(raw.indexOf('\n') in breaks)
    }

    @Test
    fun `without font data short all caps lines and blank lines break`() {
        val allCaps = PdfTtsBreakAnalysis.hardBreakOffsets("CHAPTER ONE\nSome text.")
        assertTrue(11 in allCaps)

        val blank = PdfTtsBreakAnalysis.hardBreakOffsets("One\n\nTwo")
        assertTrue(4 in blank)
        assertFalse(3 in blank)
    }

    @Test
    fun `font data keeps a mixed case body line soft wrapped`() {
        val raw = "This is a regular sentence\nthat continues here."
        val sizes = FloatArray(raw.length) { 10f }

        assertTrue(PdfTtsBreakAnalysis.hardBreakOffsets(raw, sizes, null).isEmpty())
    }

    @Test
    fun `short arrays and missing entries fall back safely`() {
        val raw = "Introduction\nBody."

        assertEquals(emptySet(), PdfTtsBreakAnalysis.hardBreakOffsets(raw, FloatArray(4), null))
    }
}
