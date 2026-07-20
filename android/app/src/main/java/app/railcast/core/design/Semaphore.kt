package app.railcast.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color

/**
 * Semaphore — design-system Stage 0 (ADDITIVE ONLY).
 *
 * This file introduces the redesign's new tokens WITHOUT modifying any existing
 * type (Colors.kt, Theme.kt, Type.kt, Dimens.kt are untouched), so it cannot
 * change current behaviour or break the build. Wiring these into `RailcastColors`
 * and `RailcastTheme` happens in Stage 1, reviewed on its own.
 *
 * Rules preserved as physics: brand != signal; colour never alone (FR-10.2);
 * fact-bearing pairs >= 4.5:1 (>= 7:1 in Sunlight) — enforced by
 * SemaphoreContrastTest.
 */

/**
 * Sunlight palette (FR-5.3) — near-white ground, near-black ink, signal colours
 * deepened, all fact pairs >= 7:1. Same shape as Light/Dark so Theme can select
 * it via the existing CompositionLocal in Stage 1.
 */
val RailcastSunlightColors = RailcastColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF2F2F2),
    ink = Color(0xFF000000),
    ink2 = Color(0xFF2A2A2A),
    ink3 = Color(0xFF555555),
    line = Color(0xFFBBBBBB),
    brand = Color(0xFF1D33A0),
    brandSoft = Color(0xFFD8DEF6),
    // Deepened so the composited chip pair (text on the opaque tint) clears the
    // 7:1 Sunlight bar, not merely text-on-white.
    green = Color(0xFF095731),
    greenSoft = Color(0xFFD9EEE2),
    amber = Color(0xFF6E4100),
    amberSoft = Color(0xFFF3E7D5),
    red = Color(0xFF852424),
    redSoft = Color(0xFFF2DEDE),
    board = Color(0xFFFFFFFF),
    boardGreen = Color(0xFF095731),
    boardAmber = Color(0xFF6E4100),
    boardInk = Color(0xFF333333),
    isDark = false,
)

/**
 * New Semaphore role tokens not yet fields on RailcastColors. Kept standalone in
 * Stage 0; folded into the data class in Stage 1 so callers pick a theme variant.
 */
object SemaphoreTokens {
    // Board cancellation — today's board sub-palette has no red (FR-2.4 on Board).
    val boardRedLight = Color(0xFFFF7A7A)
    val boardRedDark = Color(0xFFFF7A7A)
    val boardRedSun = Color(0xFF852424)

    // "estimate" — every interpolated/predicted value renders reduced-opacity + dashed (P8).
    val estimateLight = Color(0x9954696F) // ink2 @ 60%
    val estimateDark = Color(0x999DB4BB)
    val estimateSun = Color(0xFF2A2A2A)

    // Keyboard / switch-access focus ring (FR-10.3).
    val focusLight = Color(0xFF2743C4)
    val focusDark = Color(0xFF8098FF)
    val focusSun = Color(0xFF000000)

    // Brand gradient top for primary buttons (brand2).
    val brand2Light = Color(0xFF4E6BF2)
    val brand2Dark = Color(0xFFA6B5FF)
    val brand2Sun = Color(0xFF1D33A0)
}

/**
 * Confidence is a first-class visual dimension: no live value renders without one.
 * The renderer maps each level to opacity/edge/motion/copy-prefix (Stage 1 component work).
 */
enum class Confidence { CERTAIN, ESTIMATED, STALE, UNKNOWN }

/**
 * Motion ladder. `PollController` is the only owner of time; UI animates on state
 * change only — never a LaunchedEffect timer. Durations in ms; easings for Compose.
 */
object Motion {
    const val instantMs = 90    // chip select, toggle knob
    const val quickMs = 180     // crossfades, chip morphs, list reorder start
    const val settleMs = 280    // sheet detents, card expand, shared-element continuation
    const val rollMs = 420      // the flap-roll; severity colour ease
    const val breatheMs = 2400  // the estimated-position marker (the one ambient motion)

    val standardDecel: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val emphasizedDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
