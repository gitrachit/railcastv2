package app.railcast.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * Text reflow (FR-10.3, WCAG 2.2 1.4.4 / 1.4.10).
 *
 * The app used to clamp OS font scale to 1.3x app-wide, because single-line
 * fact rows clipped past that. Capping the scale is the wrong trade: it denies
 * large text to the users who need it most — persona P1 is explicitly elderly
 * and vernacular-first. The clamp is gone; layouts grow instead.
 *
 * A line budget that grows with the font scale is the mechanism. A row like
 * "16:25 - 09:45 · 18h 25m" stays on one line at default size (where wrapping
 * it would look broken, and where a one-char-per-line column was a real past
 * regression), and is allowed two or three lines once text is large enough
 * that one line cannot hold it.
 *
 * Growing the budget beats simply removing `maxLines` because these are
 * *fact rows*: unbounded wrapping lets one long string push the rest of a card
 * off-screen. Bounded growth degrades predictably.
 */
object Reflow {

    /** Font scale past which a single-line label may take a second line. */
    const val WRAP_THRESHOLD = 1.3f

    /** Font scale past which it may take a third. */
    const val WRAP_THRESHOLD_LARGE = 1.8f

    /**
     * The line budget for a label whose natural budget is [base].
     * Monotonic in [fontScale] — more room to scale is never less room to wrap.
     */
    fun maxLines(base: Int, fontScale: Float): Int = when {
        fontScale >= WRAP_THRESHOLD_LARGE -> base + 2
        fontScale >= WRAP_THRESHOLD -> base + 1
        else -> base
    }
}

/** [Reflow.maxLines] against the current density. */
@Composable
@ReadOnlyComposable
fun reflowMaxLines(base: Int = 1): Int =
    Reflow.maxLines(base, LocalDensity.current.fontScale)
