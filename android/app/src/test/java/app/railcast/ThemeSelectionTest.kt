package app.railcast

import app.railcast.core.design.RailcastDarkColors
import app.railcast.core.design.RailcastLightColors
import app.railcast.core.design.RailcastSunlightColors
import app.railcast.core.design.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Theme precedence (FR-5.3). Sunlight is an accessibility choice, so it wins. */
class ThemeSelectionTest {

    @Test fun defaults_to_light() =
        assertEquals(RailcastLightColors, paletteFor(dark = false, sunlight = false))

    @Test fun system_dark_gives_dark() =
        assertEquals(RailcastDarkColors, paletteFor(dark = true, sunlight = false))

    @Test fun sunlight_wins_over_light() =
        assertEquals(RailcastSunlightColors, paletteFor(dark = false, sunlight = true))

    /**
     * The regression that matters: most users have system dark mode on, so if
     * dark took precedence the sunlight theme would never appear for them.
     */
    @Test fun sunlight_wins_over_system_dark() =
        assertEquals(RailcastSunlightColors, paletteFor(dark = true, sunlight = true))
}
