package app.railcast

import app.railcast.core.net.AvailabilityCell
import app.railcast.core.net.FareCell
import app.railcast.core.net.NetworkModule
import app.railcast.core.net.PlanScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the `RowAvailability | "pending"` wire union (docs §4) decodes both
 *  the string sentinel and the ready object, and round-trips. */
class PlanCellsTest {
    private val json = NetworkModule.json

    private val wire = """
        {
          "from": {"code":"A","name":"Aaa"}, "to": {"code":"B","name":"Bbb"},
          "date":"2026-07-10","quota":"GN",
          "trains":[
            {"no":"111","name":"X Exp","dep":"10:00","arr":"12:00","durationMin":120,
             "classes":["2A"],"runsOn":[true,true,true,true,true,true,true],
             "availability":"pending","fare":"pending"},
            {"no":"222","name":"Y Exp","dep":"11:00","arr":"14:00","durationMin":180,
             "classes":["SL"],"runsOn":[false,true,false,true,false,true,false],
             "availability":{"status":"available","text":"AVL 20","canBook":true},
             "fare":{"total":500,"breakdown":{"base":400,"reservation":40,"superfast":30,"tatkal":0,"gst":20,"dynamic":10,"other":0}}}
          ]
        }
    """.trimIndent()

    @Test fun `pending cells decode to Pending`() {
        val screen = json.decodeFromString(PlanScreen.serializer(), wire)
        assertTrue(screen.trains[0].availability is AvailabilityCell.Pending)
        assertTrue(screen.trains[0].fare is FareCell.Pending)
    }

    @Test fun `ready cells decode to the object`() {
        val screen = json.decodeFromString(PlanScreen.serializer(), wire)
        val avail = screen.trains[1].availability as AvailabilityCell.Ready
        assertEquals("AVL 20", avail.value.text)
        val fare = screen.trains[1].fare as FareCell.Ready
        assertEquals(500.0, fare.value.total, 0.0)
        assertEquals(400.0, fare.value.breakdown.base, 0.0)
    }

    @Test fun `round-trips through encode then decode`() {
        val screen = json.decodeFromString(PlanScreen.serializer(), wire)
        val again = json.decodeFromString(PlanScreen.serializer(), json.encodeToString(PlanScreen.serializer(), screen))
        assertTrue(again.trains[0].availability is AvailabilityCell.Pending)
        assertEquals("AVL 20", (again.trains[1].availability as AvailabilityCell.Ready).value.text)
    }
}
