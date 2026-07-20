package app.railcast

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import app.railcast.core.design.Confidence
import app.railcast.core.design.ConfidenceValue
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.StatusChip
import app.railcast.core.design.StatusLevel
import org.junit.Rule
import org.junit.Test

/**
 * WCAG 2.2 **4.1.2 Name, role, value** — what TalkBack actually announces.
 *
 * The confidence system carries epistemic state visually: a dashed underline
 * and a `~` prefix. A blind user gets neither, so the *word* has to be in the
 * semantics tree. The JVM tests assert `describe()` produces the right string;
 * only a real semantics tree proves it survives composition and reaches the
 * accessibility node.
 *
 * NOT YET EXECUTED — no emulator in CI, no device when these were written.
 *
 *     ./gradlew :app:connectedDebugAndroidTest
 */
class ScreenReaderTest {

    @get:Rule val compose = createComposeRule()

    /** An estimate must ANNOUNCE that it is one — the tilde is invisible to TalkBack. */
    @Test fun an_estimated_value_announces_that_it_is_estimated() {
        compose.setContent {
            RailcastTheme {
                ConfidenceValue(
                    value = "16:49",
                    confidence = Confidence.ESTIMATED,
                    label = "arrival at Bhopal",
                )
            }
        }
        compose.onNodeWithContentDescription("estimated arrival at Bhopal").assertExists()
    }

    @Test fun a_certain_value_does_not_claim_to_be_estimated() {
        compose.setContent {
            RailcastTheme {
                ConfidenceValue(
                    value = "16:49",
                    confidence = Confidence.CERTAIN,
                    label = "arrival at Bhopal",
                )
            }
        }
        compose.onNodeWithContentDescription("arrival at Bhopal").assertExists()
    }

    @Test fun an_unknown_value_says_it_is_unavailable() {
        compose.setContent {
            RailcastTheme {
                ConfidenceValue(value = "", confidence = Confidence.UNKNOWN, label = "platform")
            }
        }
        compose.onNodeWithContentDescription("platform not available").assertExists()
    }

    /**
     * The chip is one node, not three. Without `clearAndSetSemantics` TalkBack
     * reads "warning triangle, 12 min late" as separate fragments, which is
     * how a status becomes noise instead of information (FR-10.3).
     */
    @Test fun a_status_chip_reads_as_a_single_phrase() {
        compose.setContent {
            RailcastTheme {
                StatusChip(icon = "⚠", label = "12 min late", level = StatusLevel.WARN)
            }
        }
        compose.onNodeWithContentDescription("12 min late").assertExists()
    }

    /** Dumps the tree when a run fails, so the fix does not need a second run. */
    @Test fun print_semantics_tree_for_inspection() {
        compose.setContent {
            RailcastTheme {
                StatusChip(icon = "⚠", label = "12 min late", level = StatusLevel.WARN)
            }
        }
        compose.onRoot().printToLog("RailcastSemantics")
    }
}
