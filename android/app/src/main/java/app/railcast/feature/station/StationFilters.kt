package app.railcast.feature.station

import app.railcast.core.design.StatusLevel
import app.railcast.core.net.StationTrain

/** Board filter state (FR-5.1). Pure so the filtering is unit-tested. */
data class StationFilterState(
    val dest: String = "", // destination name/code substring
    val cls: String? = null, // exact class, e.g. "2A"
    val onTimeOnly: Boolean = false,
)

object StationFilters {

    fun apply(trains: List<StationTrain>, f: StationFilterState): List<StationTrain> {
        val dest = f.dest.trim().lowercase()
        return trains.filter { t ->
            (dest.isEmpty() || t.dest.name.lowercase().contains(dest) || t.dest.code.lowercase().contains(dest)) &&
                (f.cls == null || t.classes.any { it.equals(f.cls, ignoreCase = true) }) &&
                (!f.onTimeOnly || t.status == "ontime")
        }
    }

    /** Distinct classes present on the board, for the filter chips (sorted). */
    fun classesOf(trains: List<StationTrain>): List<String> =
        trains.flatMap { it.classes }.distinct().sorted()
}

/** Board status → icon+word+colour (FR-10.2). A "late" train escalates to red
 *  past 15 min like the Track board. */
fun stationStatusVisual(status: String, delayMin: Int?): Pair<StatusLevel, String> = when (status) {
    "ontime" -> StatusLevel.GOOD to "✓"
    "late" -> (if ((delayMin ?: 0) > 15) StatusLevel.BAD else StatusLevel.WARN) to "▲"
    "cancelled" -> StatusLevel.BAD to "✕"
    else -> StatusLevel.NEUTRAL to "•"
}
