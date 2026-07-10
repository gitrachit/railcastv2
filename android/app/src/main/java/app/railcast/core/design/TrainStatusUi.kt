package app.railcast.core.design

/**
 * Maps a contract TrainStatus.state (+ delay) to the icon+word+colour trio the
 * board and chips render. Status is never colour alone (FR-10.2): every level
 * carries a glyph, and the word comes from the server's localized summary.
 */
data class StatusVisual(val level: StatusLevel, val icon: String)

fun trainStatusVisual(state: String, delayMin: Int?): StatusVisual = when (state) {
    "running" -> if ((delayMin ?: 0) > 15) StatusVisual(StatusLevel.WARN, "▶") else StatusVisual(StatusLevel.GOOD, "▶")
    "arrived" -> StatusVisual(StatusLevel.GOOD, "✓")
    "not_started" -> StatusVisual(StatusLevel.NEUTRAL, "🕓")
    "rescheduled" -> StatusVisual(StatusLevel.WARN, "↻")
    "cancelled" -> StatusVisual(StatusLevel.BAD, "✕")
    "diverted" -> StatusVisual(StatusLevel.BAD, "⤳")
    else -> StatusVisual(StatusLevel.NEUTRAL, "•")
}
