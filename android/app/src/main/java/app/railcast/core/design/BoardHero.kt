package app.railcast.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
) {
    val colors = RailcastTheme.colors
    val answerColor = when (level) {
        StatusLevel.GOOD -> colors.boardGreen
        StatusLevel.WARN -> colors.boardAmber
        StatusLevel.BAD -> colors.red
        StatusLevel.NEUTRAL -> colors.boardInk
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.board)
            .padding(20.dp),
    ) {
        Text(text = title, color = colors.boardInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "$answerIcon $answer",
            color = answerColor,
            fontFamily = RailcastMono,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
        )
        Text(text = freshness, color = colors.boardInk, fontSize = 11.sp)
    }
}
