package app.railcast

import app.railcast.directory.DirectoryIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectoryIndexTest {

    // Column order deliberately differs from the record field order to prove
    // the reader binds by header name, not by a hard-coded offset (FORMAT.md).
    private val json = """
        {
          "version": 2,
          "generatedAt": "1970-01-01T00:00:00.000Z",
          "source": { "name": "datameet/railways", "url": "x", "license": "CC0-1.0" },
          "stationColumns": ["name","code","lat","lng","city","state"],
          "trainColumns": ["name","number","fromCode","toCode"],
          "localeColumns": ["hi","bn"],
          "stations": [
            ["Rani Kamlapati","RKMP",23.22,77.44,"Bhopal","Madhya Pradesh"],
            ["Pune Jn","PUNE",null,null,"Pune",""]
          ],
          "trains": [
            ["Intercity Exp","22188","ADTL","RKMP"]
          ]
        }
    """.trimIndent()

    @Test fun `binds columns by header name regardless of order`() {
        val idx = DirectoryIndex.parse(json)
        assertEquals(2, idx.version)
        val rkmp = idx.stations.first { it.code == "RKMP" }
        assertEquals("Rani Kamlapati", rkmp.name)
        assertEquals("Bhopal", rkmp.city)
        assertEquals(23.22, rkmp.lat!!, 1e-9)
        assertEquals(77.44, rkmp.lng!!, 1e-9)
    }

    @Test fun `null coordinates decode to null`() {
        val idx = DirectoryIndex.parse(json)
        val pune = idx.stations.first { it.code == "PUNE" }
        assertNull(pune.lat)
        assertNull(pune.lng)
    }

    @Test fun `train rows decode with endpoints`() {
        val t = DirectoryIndex.parse(json).trains.single()
        assertEquals("22188", t.number)
        assertEquals("Intercity Exp", t.name)
        assertEquals("ADTL", t.fromCode)
        assertEquals("RKMP", t.toCode)
    }

    @Test fun `missing version defaults to 1`() {
        val idx = DirectoryIndex.parse(
            """{"stationColumns":["code","name","city","state","lat","lng"],""" +
                """"trainColumns":["number","name","fromCode","toCode"],"stations":[],"trains":[]}""",
        )
        assertEquals(1, idx.version)
    }
}
