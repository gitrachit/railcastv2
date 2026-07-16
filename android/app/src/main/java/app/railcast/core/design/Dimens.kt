package app.railcast.core.design

import androidx.compose.ui.unit.dp

/**
 * The two geometry scales (design review, phase 1). Before this, screens used
 * ad-hoc spacing (12/16/20/24/32) and five different corner radii
 * (12/14/16/20/999) — cards read as if from different kits. One scale each,
 * adopted incrementally as screens are touched.
 */
object Spacing {
    val xs = 4.dp   // icon ↔ label
    val sm = 8.dp   // inside chips
    val md = 12.dp  // list gaps
    val lg = 16.dp  // card padding
    val xl = 24.dp  // screen gutter
    val xxl = 32.dp // section breaks
}

/** Corner radii. The stray 14dp is retired — it folds into sm/md. */
object Radius {
    val sm = 12.dp   // inputs, rows
    val md = 16.dp   // cards
    val lg = 20.dp   // board hero, large placeholders
    val full = 999.dp // chips, pills
}
