package app.railcast

import app.railcast.core.net.CoachGuide
import app.railcast.core.net.CoachOrder
import app.railcast.feature.track.CoachLayout
import app.railcast.feature.track.PlatformZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun guide(vararg coaches: Pair<String, String>) = CoachGuide(
    referenceStation = "Bhopal",
    // Supplied out of order to prove `ordered` sorts by position.
    order = coaches.mapIndexed { i, (num, type) -> CoachOrder(type = type, number = num, position = coaches.size - i) },
    reversals = emptyList(),
)

class CoachLayoutTest {

    @Test fun `ordered sorts coaches by platform position`() {
        val g = guide("A1" to "3A", "B2" to "SL", "C3" to "GEN")
        assertEquals(listOf("C3", "B2", "A1"), CoachLayout.ordered(g).map { it.number })
    }

    @Test fun `zone buckets a six-coach rake into thirds`() {
        assertEquals(PlatformZone.FRONT, CoachLayout.zone(1, 6))
        assertEquals(PlatformZone.FRONT, CoachLayout.zone(2, 6))
        assertEquals(PlatformZone.MIDDLE, CoachLayout.zone(3, 6))
        assertEquals(PlatformZone.MIDDLE, CoachLayout.zone(4, 6))
        assertEquals(PlatformZone.REAR, CoachLayout.zone(5, 6))
        assertEquals(PlatformZone.REAR, CoachLayout.zone(6, 6))
    }

    @Test fun `single coach sits in the middle, not the rear`() {
        assertEquals(PlatformZone.MIDDLE, CoachLayout.zone(1, 1))
    }

    @Test fun `zone is defensive about empty rakes`() {
        assertEquals(PlatformZone.MIDDLE, CoachLayout.zone(1, 0))
    }

    @Test fun `zoneOf finds a coach by number, case-insensitively`() {
        val g = guide("A1" to "3A", "B2" to "SL", "C3" to "GEN") // ordered: C3, B2, A1
        assertEquals(PlatformZone.FRONT, CoachLayout.zoneOf(g, "c3"))
        assertEquals(PlatformZone.REAR, CoachLayout.zoneOf(g, "A1"))
        assertNull(CoachLayout.zoneOf(g, "Z9"))
    }

    @Test fun `genCoaches picks out only unreserved coaches`() {
        val g = guide("A1" to "3A", "G1" to "GEN", "G2" to "gen", "S1" to "SL")
        assertEquals(setOf("G1", "G2"), CoachLayout.genCoaches(g))
    }
}
