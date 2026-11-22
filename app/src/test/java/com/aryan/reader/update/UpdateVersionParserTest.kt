package com.aryan.reader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionParserTest {
    @Test
    fun `parses personal release tag`() {
        assertEquals(123, UpdateVersionParser.buildNumberFromTag("personal-b123"))
    }

    @Test
    fun `rejects unrelated tags`() {
        assertNull(UpdateVersionParser.buildNumberFromTag("fdroid-oss-build"))
        assertNull(UpdateVersionParser.buildNumberFromTag("v1.0.59"))
        assertNull(UpdateVersionParser.buildNumberFromTag("personal-bnan"))
    }

    @Test
    fun `only newer build numbers count as updates`() {
        assertTrue(UpdateVersionParser.isNewer(124, 123))
        assertFalse(UpdateVersionParser.isNewer(123, 123))
        assertFalse(UpdateVersionParser.isNewer(122, 123))
    }
}
