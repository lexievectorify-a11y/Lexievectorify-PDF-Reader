package com.aryan.reader

import com.aryan.reader.shared.AndroidShareArtifactManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AndroidFileProviderPathsContractTest {
    @Test
    fun `provider exposes only bounded share and update roots`() {
        val paths = readPaths()
            .getElementsByTagName("cache-path")
            .asElements()
            .associateBy { it.attribute("name") }

        assertEquals(setOf(AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY, "updates"), paths.keys)
        assertEquals(
            "${AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY}/",
            paths.getValue(AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY).attribute("path"),
        )
        assertEquals("updates/", paths.getValue("updates").attribute("path"))
        assertFalse(paths.values.any { it.attribute("path") == "." })
        assertTrue(paths.values.all { it.attribute("path")?.contains("..") != true })
    }

    @Test
    fun `unrelated cache files are outside the provider root`() {
        val cacheRoot = File("build/provider-contract-cache").canonicalFile
        val shareRoot = File(cacheRoot, AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY).canonicalFile
        val unrelated = File(cacheRoot, "unrelated-cache-file.pdf").canonicalFile

        assertFalse(unrelated.path.startsWith("${shareRoot.path}${File.separator}"))
    }

    private fun readPaths(): org.w3c.dom.Document {
        val resource = listOf(
            File("src/main/res/xml/provider_paths.xml"),
            File("app/src/main/res/xml/provider_paths.xml"),
        ).first { it.isFile }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(resource)
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> = buildList {
        for (index in 0 until length) {
            (item(index) as? Element)?.let(::add)
        }
    }

    private fun Element.attribute(name: String): String? = attributes?.getNamedItem(name)?.nodeValue
}
