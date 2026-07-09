package app.railcast

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces FR-10.1: every translatable string in the EN baseline has a Hindi
 * counterpart and vice-versa — full parity, every string, no drift. This runs
 * in :app:testDebugUnitTest so a missing or extra translation fails CI, which
 * is stronger than a lint warning for cross-file parity.
 */
class StringsParityTest {
    private val resDir = File("src/main/res")

    private fun translatableKeys(valuesDir: String): Set<String> {
        val file = File(resDir, "$valuesDir/strings.xml")
        assertTrue("missing $file", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            val translatable = el.attributes.getNamedItem("translatable")?.nodeValue
            if (translatable == "false") continue // brand strings (app_name) are exempt
            keys += el.attributes.getNamedItem("name").nodeValue
        }
        return keys
    }

    @Test
    fun englishAndHindiHaveIdenticalKeySets() {
        val en = translatableKeys("values")
        val hi = translatableKeys("values-hi")

        assertEquals("Hindi is missing translations", emptySet<String>(), en - hi)
        assertEquals("Hindi has strings English lacks", emptySet<String>(), hi - en)
        assertTrue("expected a non-trivial string set", en.size >= 10)
    }

    @Test
    fun hindiValuesAreNotAccidentallyLeftInEnglish() {
        val file = File(resDir, "values-hi/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        var devanagari = 0
        for (i in 0 until nodes.length) {
            if (nodes.item(i).textContent.any { it in 'ऀ'..'ॿ' }) devanagari++
        }
        // Most HI strings should actually contain Devanagari (guards a copy-paste slip).
        assertTrue("Hindi strings look untranslated", devanagari >= 8)
    }
}
