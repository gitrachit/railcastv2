package app.railcast.core.design

import androidx.compose.ui.graphics.Color

/**
 * Railcast palette, ported token-for-token from docs/prototype/Railcast-v3.html.
 * Calm base + single brand accent + railway-signal status colours (green/amber/
 * red), each with a soft tint. The "board" tokens are the dark departure-board
 * surface used by the board hero. Status is NEVER colour alone (FR-10.2) — the
 * StatusChip always pairs these with an icon and a word.
 */
data class RailcastColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val brand: Color,
    val brandSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val red: Color,
    val redSoft: Color,
    val board: Color,
    val boardGreen: Color,
    val boardAmber: Color,
    val boardInk: Color,
    val boardRed: Color,
    /**
     * Ink for ESTIMATED values. Deliberately FULL contrast, not a faded ink:
     * an estimate is still fact-bearing text (the ETA is the answer), so
     * confidence rides on the `~` prefix, the dashed underline and the breathe
     * — never on opacity. Fading it measured 2.53:1 in Light.
     */
    val estimate: Color,
    /** Keyboard / switch-access focus ring (WCAG 2.4.13, >= 3:1). */
    val focus: Color,
    /** Gradient top for primary buttons. Affordance only, never a signal. */
    val brand2: Color,
    val isDark: Boolean,
)

val RailcastLightColors = RailcastColors(
    bg = Color(0xFFE7ECEF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F4F6),
    ink = Color(0xFF0F2A33),
    ink2 = Color(0xFF54696F),
    ink3 = Color(0xFF5B6E75),
    line = Color(0xFFDCE3E7),
    brand = Color(0xFF2743C4),
    brandSoft = Color(0xFFEAEEFB),
    // Signal colours are darkened from the prototype so that StatusChip's
    // *composited* pair (text over the 14% tint over surface OR bg) clears
    // 4.5:1. Measuring text against a plain surface overstated these by ~1 stop.
    green = Color(0xFF126C40),
    greenSoft = Color(0x24178A52), // rgba(23,138,82,.14) — tint hue unchanged
    amber = Color(0xFF8B5208),
    amberSoft = Color(0x24C9770B), // rgba(201,119,11,.14) — tint hue unchanged
    red = Color(0xFFC03C3C),
    redSoft = Color(0xFFFBECEC),
    board = Color(0xFF0F2A33),
    boardGreen = Color(0xFF3DE08A),
    boardAmber = Color(0xFFFFC24D),
    boardInk = Color(0xFF7FA6AE),
    boardRed = Color(0xFFFF7A7A),
    estimate = Color(0xFF54696F), // = ink2, full contrast (5.79:1)
    focus = Color(0xFF2743C4),
    brand2 = Color(0xFF4E6BF2),
    isDark = false,
)

val RailcastDarkColors = RailcastColors(
    bg = Color(0xFF081115),
    surface = Color(0xFF0F2129),
    surface2 = Color(0xFF15303A),
    ink = Color(0xFFEAF3F4),
    ink2 = Color(0xFF9DB4BB),
    ink3 = Color(0xFF708A92), // lifted to clear 4.5:1 on surface (was 4.28:1)
    line = Color(0xFF1E3B45),
    brand = Color(0xFF8098FF),
    brandSoft = Color(0xFF182747),
    green = Color(0xFF3DBB77),
    greenSoft = Color(0x293DBB77), // rgba(61,187,119,.16)
    amber = Color(0xFFE7A542),
    amberSoft = Color(0x29E7A542), // rgba(231,165,66,.16)
    red = Color(0xFFE36868),
    redSoft = Color(0xFF3A1E1E),
    board = Color(0xFF06151A),
    boardGreen = Color(0xFF3DE08A),
    boardAmber = Color(0xFFFFC24D),
    boardInk = Color(0xFF6E969E),
    boardRed = Color(0xFFFF7A7A),
    estimate = Color(0xFF9DB4BB), // = ink2, full contrast (7.62:1)
    focus = Color(0xFF8098FF),
    brand2 = Color(0xFFA6B5FF),
    isDark = true,
)
