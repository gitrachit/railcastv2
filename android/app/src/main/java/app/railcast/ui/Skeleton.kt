package app.railcast.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.railcast.core.design.Radius
import app.railcast.core.design.RailcastTheme

/**
 * One loading placeholder for every screen (design review, phase 1). Replaces
 * the per-screen "Loading…" boxes that each rolled their own surface + radius.
 * A single, calm alpha pulse — no gradient sweep — so it stays cheap on
 * low-end devices (NFR-1) and never a blank surface (FR-9.1). `label` is the
 * spoken name so TalkBack still announces what's loading (FR-10.3).
 */
@Composable
fun Skeleton(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    corner: Dp = Radius.lg,
) {
    val colors = RailcastTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(corner))
            .alpha(pulse)
            .background(colors.surface2)
            .semantics { contentDescription = label },
    )
}
