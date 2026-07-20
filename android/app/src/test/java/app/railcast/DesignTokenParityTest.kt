package app.railcast

import androidx.compose.ui.graphics.Color
import app.railcast.core.design.RailcastColors
import app.railcast.core.design.RailcastDarkColors
import app.railcast.core.design.RailcastLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Kotlin palette and the XML palette must not drift.
 *
 * RemoteViews surfaces (Glance widget, live notification) cannot read the
 * Compose CompositionLocal, so every token exists twice. Without a gate, a
 * colour corrected in Colors.kt and forgotten in XML leaves the ambient layer
 * rendering the OLD value — and the last correction to these tokens was a WCAG
 * failure, so drift here means shipping known-inaccessible colours on the
 * surface the product's whole direction depends on.
 *
 * Pure JVM: reads the resource files off disk and compares them to the Kotlin
 * source of truth. Colors.kt is authoritative; XML mirrors it.
 */
class DesignTokenParityTest {

    private fun parse(file: File): Map<String, String> {
        assertTrue("missing token file: ${file.absolutePath}", file.exists())
        val re = Regex("""<color name="([^"]+)">#([0-9A-Fa-f]{8})</color>""")
        return re.findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    /** Compose Color -> the AARRGGBB string an Android resource would carry. */
    private fun hex(c: Color): String {
        fun ch(v: Float) = ((v * 255f) + 0.5f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
        return (ch(c.alpha) + ch(c.red) + ch(c.green) + ch(c.blue)).uppercase()
    }

    private fun expected(c: RailcastColors): Map<String, String> = mapOf(
        "rc_bg" to hex(c.bg),
        "rc_surface" to hex(c.surface),
        "rc_surface2" to hex(c.surface2),
        "rc_ink" to hex(c.ink),
        "rc_ink2" to hex(c.ink2),
        "rc_ink3" to hex(c.ink3),
        "rc_line" to hex(c.line),
        "rc_brand" to hex(c.brand),
        "rc_brand2" to hex(c.brand2),
        "rc_green" to hex(c.green),
        "rc_amber" to hex(c.amber),
        "rc_red" to hex(c.red),
        "rc_board" to hex(c.board),
        "rc_board_green" to hex(c.boardGreen),
        "rc_board_amber" to hex(c.boardAmber),
        "rc_board_red" to hex(c.boardRed),
        "rc_board_ink" to hex(c.boardInk),
        "rc_estimate" to hex(c.estimate),
        "rc_focus" to hex(c.focus),
    )

    private fun assertParity(resPath: String, colors: RailcastColors, label: String) {
        val actual = parse(File(resPath))
        val expected = expected(colors)
        for ((name, want) in expected) {
            assertEquals("$label token $name drifted from Colors.kt", want, actual[name])
        }
        // Catches a token added to XML that no Kotlin field backs.
        assertEquals(
            "$label has XML tokens with no Kotlin source: ${actual.keys - expected.keys}",
            emptySet<String>(),
            actual.keys - expected.keys,
        )
    }

    @Test fun light_xml_matches_kotlin() =
        assertParity("src/main/res/values/design_tokens.xml", RailcastLightColors, "light")

    @Test fun dark_xml_matches_kotlin() =
        assertParity("src/main/res/values-night/design_tokens.xml", RailcastDarkColors, "dark")
}
