package app.railcast

import app.railcast.directory.DirectoryIndex
import app.railcast.directory.Station
import app.railcast.directory.Train
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import app.railcast.directory.DirectorySearch
import org.junit.Test

class DirectorySearchTest {

    private val index = DirectoryIndex(
        version = 1,
        stations = listOf(
            Station("NDLS", "New Delhi", "Delhi", "Delhi", 28.64, 77.22),
            Station("NZM", "Hazrat Nizamuddin", "Delhi", "Delhi", 28.58, 77.25),
            Station("BPL", "Bhopal Jn", "Bhopal", "Madhya Pradesh", 23.26, 77.40),
            Station("RKMP", "Rani Kamlapati", "Bhopal", "Madhya Pradesh", 23.22, 77.44),
        ),
        trains = listOf(
            Train("12780", "Goa Express", "NZM", "VSG"),
            Train("12951", "Mumbai Rajdhani", "MMCT", "NDLS"),
            Train("22188", "Intercity Exp", "ADTL", "RKMP"),
        ),
    )

    private fun search(q: String) = DirectorySearch.search(index, q)

    @Test fun `empty query returns nothing`() {
        assertTrue(DirectorySearch.search(index, "   ").isEmpty())
    }

    @Test fun `digit query matches train numbers, not stations`() {
        val hits = search("12780")
        assertEquals("12780", (hits.first().entry as Train).number)
        assertTrue(hits.all { it.entry is Train })
    }

    @Test fun `partial number prefix ranks the train`() {
        val hits = search("227")
        assertTrue(hits.isEmpty()) // no train starts with 227
        val hits2 = search("221")
        assertEquals("22188", (hits2.first().entry as Train).number)
    }

    @Test fun `exact code beats a mere substring match`() {
        val hits = search("nzm")
        assertEquals("NZM", (hits.first().entry as Station).code)
    }

    @Test fun `name prefix search resolves to the station`() {
        val hits = search("bhopal")
        assertEquals("BPL", (hits.first().entry as Station).code)
    }

    @Test fun `train name search works by word`() {
        val hits = search("rajdhani")
        assertEquals("12951", (hits.first().entry as Train).number)
    }

    @Test fun `typo tolerance finds the intended train`() {
        // "rajdhany" is one substitution (i→y) away from "rajdhani"
        val hits = search("rajdhany")
        assertTrue(hits.any { (it.entry as? Train)?.number == "12951" })
    }

    @Test fun `prefix outranks fuzzy`() {
        val hits = search("goa")
        assertEquals("12780", (hits.first().entry as Train).number)
    }

    @Test fun `results are capped at the limit`() {
        val big = DirectoryIndex(
            1,
            (1..50).map { Station("S$it", "Delhi Station $it", "Delhi", "Delhi", null, null) },
            emptyList(),
        )
        assertEquals(5, DirectorySearch.search(big, "delhi", limit = 5).size)
    }

    @Test fun `ranking is deterministic across runs`() {
        assertEquals(search("delhi").map { it.entry.query }, search("delhi").map { it.entry.query })
    }
}
