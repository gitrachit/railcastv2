package app.railcast.core.design.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Railcast color tokens, ported 1:1 from docs/prototype/Railcast-v3.html
 * (`.rc[data-theme="light"]` / `.rc[data-theme="dark"]` CSS variables).
 *
 * Status colors always ride with an icon + word — never color alone. [FR-10.2]
 */
@Immutable
data class RailcastPalette(
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
)

val LightPalette = RailcastPalette(
    bg = Color(0xFFE7ECEF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F4F6),
    ink = Color(0xFF0F2A33),
    ink2 = Color(0xFF54696F),
    ink3 = Color(0xFF8FA1A8),
    line = Color(0xFFDCE3E7),
    brand = Color(0xFF2743C4),
    brandSoft = Color(0xFFEAEEFB),
    green = Color(0xFF178A52),
    greenSoft = Color(0x24178A52), // rgba(23,138,82,.14)
    amber = Color(0xFFC9770B),
    amberSoft = Color(0x24C9770B), // rgba(201,119,11,.14)
    red = Color(0xFFC33F3F),
    redSoft = Color(0xFFFBECEC),
    board = Color(0xFF0F2A33),
    boardGreen = Color(0xFF3DE08A),
    boardAmber = Color(0xFFFFC24D),
    boardInk = Color(0xFF7FA6AE),
)

val DarkPalette = RailcastPalette(
    bg = Color(0xFF081115),
    surface = Color(0xFF0F2129),
    surface2 = Color(0xFF15303A),
    ink = Color(0xFFEAF3F4),
    ink2 = Color(0xFF9DB4BB),
    ink3 = Color(0xFF6C868E),
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
)
