package app.railcast.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The board hero — the visual hook (a dark departure-board surface with the
 * answer in the largest type). Answer-first skeleton: big answer → freshness →
 * detail (PRD §7). The status word is signal-coloured on the board palette and
 * always accompanied by its icon.
 */
@Composable
fun BoardHero(
    title: String,
    answer: String,
    answerIcon: String,
    level: StatusLevel,
    freshness: String,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
) {
    val colors = RailcastTheme.colors
    val targetColor = when (level) {
        StatusLevel.GOOD -> colors.boardGreen
        StatusLevel.WARN -> colors.boardAmber
        StatusLevel.BAD -> colors.red
        StatusLevel.NEUTRAL -> colors.boardInk
    }
    // A late train easing green → amber reads as a change, not a jump (design
    // review, phase 1b). Cheap, state-driven — no running timer.
    val answerColor by animateColorAsState(targetColor, tween(220), label = "board-color")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(colors.board)
            .padding(20.dp),
    ) {
        Text(text = title, color = colors.boardInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        // Departure-board roll: a refreshed answer ("2 min late" → "5 min late")
        // rolls up into place as the old one rolls out — the split-flap feel of
        // a real board, evoked without per-digit machinery. Keyed on the text,
        // so an unchanged answer never animates on a poll tick (stays calm,
        // PRD §6). Cheap, state-driven — no running timer.
        AnimatedContent(
            targetState = "$answerIcon $answer",
            modifier = Modifier.clipToBounds(), // roll stays behind the line's edges
            transitionSpec = {
                (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(140)))
            },
            label = "board-answer",
        ) { line ->
            Text(
                text = line,
                color = answerColor,
                fontFamily = RailcastMono,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
            )
        }
        // Provenance stamp (design blueprint §5.3): a dot + label so freshness
        // is legible at a glance — green when live, muted when stale/offline.
        // Colour is never the only signal; the label still says "offline".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (stale) colors.boardInk else colors.boardGreen),
            )
            Text(text = freshness, color = colors.boardInk, fontSize = 11.sp)
        }
    }
}
