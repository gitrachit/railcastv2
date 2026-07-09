package app.railcast

import app.railcast.core.design.RailcastDarkColors
import app.railcast.core.design.RailcastLightColors
import app.railcast.core.design.StatusLevel
import app.railcast.core.design.statusColor
import app.railcast.core.design.statusSoftColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Status is icon + word + colour, never colour alone (FR-10.2). This pins the
 * colour half of that contract: each signal level maps to the right palette
 * colour in both themes, and the four levels are visually distinct.
 */
class StatusChipTest {
    @Test
    fun levelsMapToSignalColours() {
        val c = RailcastLightColors
        assertEquals(c.green, statusColor(StatusLevel.GOOD, c))
        assertEquals(c.amber, statusColor(StatusLevel.WARN, c))
        assertEquals(c.red, statusColor(StatusLevel.BAD, c))
        assertEquals(c.ink2, statusColor(StatusLevel.NEUTRAL, c))
    }

    @Test
    fun softTintsMatchLevels() {
        val c = RailcastLightColors
        assertEquals(c.greenSoft, statusSoftColor(StatusLevel.GOOD, c))
        assertEquals(c.amberSoft, statusSoftColor(StatusLevel.WARN, c))
        assertEquals(c.redSoft, statusSoftColor(StatusLevel.BAD, c))
    }

    @Test
    fun signalColoursAreDistinctPerLevel() {
        val c = RailcastDarkColors
        val colours = StatusLevel.entries.map { statusColor(it, c) }.toSet()
        assertEquals(StatusLevel.entries.size, colours.size) // all four differ
        assertNotEquals(statusColor(StatusLevel.GOOD, c), statusColor(StatusLevel.BAD, c))
    }

    @Test
    fun themesUseDifferentGreens() {
        // Dark theme brightens the signal green for contrast on the dark board.
        assertNotEquals(
            statusColor(StatusLevel.GOOD, RailcastLightColors),
            statusColor(StatusLevel.GOOD, RailcastDarkColors),
        )
    }
}
