package app.railcast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.railcast.core.design.BoardHero
import app.railcast.core.design.Confidence
import app.railcast.core.design.ConfidenceValue
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.StatusChip
import app.railcast.core.design.StatusLevel
import org.junit.Rule
import org.junit.Test

/**
 * WCAG 2.2 **1.4.4 Resize text** and **1.4.10 Reflow**, on a real device.
 *
 * The app used to clamp OS font scale to 1.3x because layouts clipped past it.
 * The clamp is gone and [app.railcast.core.design.Reflow] grants a line budget
 * instead — but the *budget* is what the JVM tests cover. Whether real text at
 * 200% actually fits, wraps and stays readable can only be observed with a
 * renderer.
 *
 * These are the checks CI cannot make. Run them before claiming AA:
 *
 *     ./gradlew :app:connectedDebugAndroidTest
 *
 * NOT YET EXECUTED — this project has no emulator in CI and these were written
 * without a device to hand. Treat a green run as the first real evidence.
 */
class TextScalingTest {

    @get:Rule val compose = createComposeRule()

    /** WCAG's bar, and the reason the clamp had to go. */
    private val wcagScale = 2.0f

    /** The narrowest width 1.4.10 requires content to reflow into. */
    private val reflowWidth = 320.dp

    private fun setScaled(scale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, scale),
            ) {
                RailcastTheme {
                    Column(Modifier.fillMaxSize().width(reflowWidth)) { content() }
                }
            }
        }
    }

    @Test fun status_chip_survives_200_percent() {
        setScaled(wcagScale) {
            StatusChip(icon = "⚠", label = "12 min late", level = StatusLevel.WARN)
        }
        // Displayed, not merely present: a clipped node still exists in the tree.
        compose.onNodeWithText("12 min late").assertIsDisplayed()
    }

    @Test fun the_board_hero_answer_survives_200_percent() {
        setScaled(wcagScale) {
            BoardHero(
                title = "Rajdhani · 12951",
                answer = "12 min late",
                answerIcon = "⚠",
                level = StatusLevel.WARN,
                freshness = "updated 8s ago",
                stale = false,
            )
        }
        compose.onNodeWithText("12 min late", substring = true).assertIsDisplayed()
    }

    /**
     * The consequence line is the answer that ends the anxiety loop, so it is
     * the single worst thing to lose to clipping.
     */
    @Test fun the_consequence_line_survives_200_percent() {
        setScaled(wcagScale) {
            ConfidenceValue(value = "Bhopal 16:49", confidence = Confidence.ESTIMATED)
        }
        compose.onNodeWithText("~Bhopal 16:49", substring = true).assertIsDisplayed()
    }

    /** Sanity: the same content at default scale, to isolate scale-specific breakage. */
    @Test fun the_consequence_line_renders_at_default_scale() {
        setScaled(1.0f) {
            ConfidenceValue(value = "Bhopal 16:49", confidence = Confidence.ESTIMATED)
        }
        compose.onNodeWithText("~Bhopal 16:49", substring = true).assertIsDisplayed()
    }
}
