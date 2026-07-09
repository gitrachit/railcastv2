package app.railcast

import app.railcast.core.design.component.StatusTone
import app.railcast.core.design.component.colors
import app.railcast.core.design.token.DarkPalette
import app.railcast.core.design.token.LightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// Status is icon + word + color; the color leg must at least be unambiguous
// per tone in both themes. [FR-10.2]
class StatusToneTest {

    @Test
    fun everyToneResolvesToDistinctColorsInLightPalette() {
        val resolved = StatusTone.entries.map { it.colors(LightPalette) }
        assertEquals(StatusTone.entries.size, resolved.distinct().size)
    }

    @Test
    fun everyToneResolvesToDistinctColorsInDarkPalette() {
        val resolved = StatusTone.entries.map { it.colors(DarkPalette) }
        assertEquals(StatusTone.entries.size, resolved.distinct().size)
    }

    @Test
    fun contentAndContainerDifferSoTextStaysLegible() {
        for (palette in listOf(LightPalette, DarkPalette)) {
            for (tone in StatusTone.entries) {
                val c = tone.colors(palette)
                assertNotEquals("$tone container == content", c.container, c.content)
            }
        }
    }

    @Test
    fun tonesMapToTheirSemanticPaletteSlots() {
        val c = StatusTone.Positive.colors(LightPalette)
        assertEquals(LightPalette.green, c.content)
        assertEquals(LightPalette.greenSoft, c.container)
        val w = StatusTone.Warning.colors(LightPalette)
        assertEquals(LightPalette.amber, w.content)
        val e = StatusTone.Critical.colors(LightPalette)
        assertEquals(LightPalette.red, e.content)
    }
}
