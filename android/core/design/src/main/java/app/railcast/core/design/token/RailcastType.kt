package app.railcast.core.design.token

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale ported from docs/prototype/Railcast-v3.html.
 * Sizes are sp so OS text scaling reflows naturally. [FR-10.3]
 */
object RailcastType {

    /** Numbers, times, codes — tabular so live digits don't jitter. */
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.01).em,
    )

    /** `.rc-hello h1` — screen greeting / page title. */
    val Hero = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
    )

    /** `.rc-board-status-main` — the big board status line. */
    val BoardStatus = Mono.copy(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )

    /** `.rc-statusbig` — card-level live status. */
    val StatusBig = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02).em,
    )

    /** `.rc-trainname` / card titles. */
    val CardTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
    )

    /** Default body copy. */
    val Body = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    )

    /** `.rc-livecard-sub` and other secondary rows. */
    val Meta = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )

    /** `.rc-eyebrow` — uppercase section labels. */
    val Eyebrow = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.09.em,
    )

    /** `.rc-chip` / badges. */
    val ChipLabel = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}
