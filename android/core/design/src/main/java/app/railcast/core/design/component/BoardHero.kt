package app.railcast.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.token.RailcastType

/**
 * The departure-board hero card (`.rc-board` in docs/prototype/Railcast-v3.html):
 * dark board surface, train number + live badge, big amber status line,
 * departed → next flip row, and a footer with the "estimated" note and a
 * freshness stamp. [FR-2.2, FR-2.5]
 *
 * Stateless: every string comes from the caller so this module holds no
 * user-facing text. [FR-10.1]
 */
@Composable
fun BoardHero(
    trainNumber: String,
    nameLine: String,
    statusLine: String,
    departedText: String,
    nextText: String,
    liveBadge: String,
    isLive: Boolean,
    estimatedNote: String,
    freshnessText: String,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
) {
    val colors = RailcastTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLive) 1f else 0.82f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.board)
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = trainNumber,
                style = RailcastType.Mono.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = liveBadge,
                style = RailcastType.Mono.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.12.em,
                ),
                color = if (isLive) colors.boardGreen else colors.boardInk,
            )
        }
        Text(
            text = nameLine,
            style = RailcastType.Mono.copy(fontSize = 11.sp, letterSpacing = 0.04.em),
            color = colors.boardInk,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = statusLine,
            style = RailcastType.BoardStatus,
            color = colors.boardAmber,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = departedText,
                style = RailcastType.Mono.copy(fontSize = 12.sp),
                color = Color(0xFFCFE0E3),
            )
            Text(
                text = "›",
                style = RailcastType.Mono.copy(fontSize = 12.sp),
                color = colors.boardGreen,
            )
            Text(
                text = nextText,
                style = RailcastType.Mono.copy(fontSize = 12.sp),
                color = Color(0xFFCFE0E3),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp),
            color = Color.White.copy(alpha = 0.08f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The interpolated position is always labelled estimated. [FR-2.2]
            Text(
                text = estimatedNote,
                style = RailcastType.Mono.copy(fontSize = 10.5.sp),
                color = colors.boardInk,
            )
            Text(
                text = freshnessText,
                style = RailcastType.Mono.copy(fontSize = 10.5.sp),
                color = colors.boardGreen,
                modifier = if (onRefresh != null) {
                    Modifier
                        .clickable(onClick = onRefresh)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp) // [FR-10.3]
                        .wrapContentSize(Alignment.Center)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Preview(name = "BoardHero light", showBackground = true)
@Composable
private fun BoardHeroPreview() {
    RailcastTheme(darkTheme = false) {
        BoardHero(
            trainNumber = "22188",
            nameLine = "INTERCITY EXP · ADTL → RKMP",
            statusLine = "RUNNING · 17 MIN LATE",
            departedText = "DEP ITARSI 20:47",
            nextText = "NDPM ~21:12",
            liveBadge = "● LIVE",
            isLive = true,
            estimatedNote = "◔ estimated position",
            freshnessText = "↻ updated 12s ago",
        )
    }
}

@Preview(name = "BoardHero cached", showBackground = true, backgroundColor = 0xFF081115)
@Composable
private fun BoardHeroCachedPreview() {
    RailcastTheme(darkTheme = true) {
        BoardHero(
            trainNumber = "22188",
            nameLine = "INTERCITY EXP · ADTL → RKMP",
            statusLine = "RUNNING · 17 MIN LATE",
            departedText = "DEP ITARSI 20:47",
            nextText = "NDPM ~21:12",
            liveBadge = "◌ CACHED",
            isLive = false,
            estimatedNote = "◔ estimated position",
            freshnessText = "6:40 PM",
        )
    }
}
