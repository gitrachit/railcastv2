package app.railcast.core.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The enforcement point for Law 2 of the design system: *confidence is a data
 * dimension, and no live value renders without one*.
 *
 * [confidence] has NO default. Every call site must state what it knows about
 * the number it is showing, so an interpolated position or a predicted delay
 * cannot be rendered as though it were observed fact (FR-11.1, FR-2.2). This
 * turns an honesty policy into a compile-time property.
 *
 * Confidence is carried on channels that cost no legibility:
 *  - copy      — the `~` prefix, the only channel that survives on Glance
 *                widgets and RemoteViews notifications
 *  - edge      — a dashed underline for ESTIMATED
 *  - semantics — the word "estimated" reaches TalkBack, since a blind user
 *                cannot see the dashed edge
 *
 * It is deliberately NOT carried on opacity. Fading estimates measured 2.53:1
 * in Light, and an ETA is the single most important number on the screen —
 * the values a user most needs are exactly the ones an opacity scheme
 * degrades most.
 */
@Composable
fun ConfidenceValue(
    value: String,
    confidence: Confidence,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    /** Read to screen readers instead of the raw glyphs, e.g. "arrival at Bhopal". */
    label: String? = null,
) {
    val colors = RailcastTheme.colors
    val text = confidencePrefix(confidence) + displayValue(value, confidence)

    Text(
        // Only the number runs take the tabular face — "78% chance" keeps its
        // word in the UI sans while the digits stay jitter-free on refresh.
        text = monoNumerals(text),
        modifier = modifier
            .then(if (confidence == Confidence.ESTIMATED) Modifier.dashedUnderline(colors.estimate) else Modifier)
            .clearAndSetSemantics { contentDescription = describe(value, confidence, label) },
        color = when (confidence) {
            // Full contrast in every state. STALE is signalled by the freshness
            // strip beside it, never by fading the number itself.
            Confidence.CERTAIN -> colors.ink
            Confidence.ESTIMATED -> colors.estimate
            Confidence.STALE -> colors.ink2
            Confidence.UNKNOWN -> colors.ink3
        },
        style = style,
    )
}

/**
 * UNKNOWN renders as an em-dash — never `0`, never blank. A missing platform
 * shown blank reads as "no platform"; shown as `0` reads as platform zero.
 * Both are lies the em-dash does not tell.
 */
internal fun displayValue(value: String, confidence: Confidence): String =
    if (confidence == Confidence.UNKNOWN) "—" else value

/** The `~` is the confidence channel that survives everywhere, including Glance. */
internal fun confidencePrefix(confidence: Confidence): String =
    if (confidence == Confidence.ESTIMATED) "~" else ""

/**
 * Screen readers get the epistemic state in words, because they cannot see the
 * dashed edge that conveys it to a sighted user (FR-10.3, WCAG 4.1.2).
 */
internal fun describe(value: String, confidence: Confidence, label: String?): String {
    val subject = label ?: value
    return when (confidence) {
        Confidence.CERTAIN -> subject
        Confidence.ESTIMATED -> "estimated $subject"
        Confidence.STALE -> "$subject, last known"
        Confidence.UNKNOWN -> "${label ?: "value"} not available"
    }
}

private fun Modifier.dashedUnderline(color: androidx.compose.ui.graphics.Color, gap: Dp = 2.dp): Modifier =
    drawBehind {
        val y = size.height + gap.toPx()
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = Stroke.HairlineWidth.coerceAtLeast(1f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f),
        )
    }

/** Freshness strip (FR-2.5). Pairs with STALE values; never replaces them. */
@Composable
fun FreshnessStamp(text: String, isStale: Boolean, modifier: Modifier = Modifier) {
    val colors = RailcastTheme.colors
    Row(modifier = modifier.padding(top = Spacing.xs)) {
        Text(
            text = text,
            color = if (isStale) colors.amber else colors.ink3,
            style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
