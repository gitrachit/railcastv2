package app.railcast.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Humanist system sans (first-class Indic-script support comes free from the
// platform font). Tabular numerals live on RailcastMono so refreshing live
// numbers never jitters (PRD §7).
val RailcastMono = FontFamily.Monospace

val RailcastTypography = Typography(
    // Answer-first: the big answer on every data screen.
    displaySmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.03).em),
    // Screen titles (Track / Station / Plan) — was hardcoded 28sp per screen.
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.5f.sp),
)

/** Tabular-numeral style for live figures (times, delays, fares). */
val TabularNumberStyle = TextStyle(
    fontFamily = RailcastMono,
    textDirection = TextDirection.Ltr, // numbers stay LTR even in RTL locales
)
