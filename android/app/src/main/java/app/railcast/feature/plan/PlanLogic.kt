package app.railcast.feature.plan

import app.railcast.core.net.AvailabilityCell
import app.railcast.core.net.FareCell
import app.railcast.core.net.PlanRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Booking quotas that drive availability + fare queries (FR-6.4). */
enum class PlanQuota(val code: String) {
    GENERAL("GN"),
    TATKAL("TQ"),
    LADIES("LD"),
    SENIOR("SS");

    val isTatkal get() = this == TATKAL

    companion object {
        fun fromCode(code: String?): PlanQuota = entries.firstOrNull { it.code == code } ?: GENERAL
    }
}

enum class PlanSort { DEPARTURE, PRICE, SEATS }

/** UTC date stepping for the journey date — no java.time (minSdk 24 safe). */
object PlanDates {
    private fun formatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    fun today(): String = formatter().format(java.util.Date())

    fun addDays(iso: String, days: Int): String {
        val parsed = runCatching { formatter().parse(iso) }.getOrNull() ?: return iso
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, days)
        }
        return formatter().format(cal.time)
    }
}

/**
 * Row sorting (FR-6.2). Rows never block on the slowest call, so a row may still
 * be hydrating — those sort to the end for price/seats rather than jumping around.
 */
object PlanSorting {
    fun sort(rows: List<PlanRow>, sort: PlanSort): List<PlanRow> = when (sort) {
        PlanSort.DEPARTURE -> rows.sortedBy { it.dep }
        PlanSort.PRICE -> rows.sortedBy { fareTotal(it) ?: Double.MAX_VALUE }
        PlanSort.SEATS -> rows.sortedBy { seatRank(it) }
    }

    private fun fareTotal(row: PlanRow): Double? = (row.fare as? FareCell.Ready)?.value?.total

    private fun seatRank(row: PlanRow): Int =
        when ((row.availability as? AvailabilityCell.Ready)?.value?.status) {
            "available" -> 0
            "rac" -> 1
            "waitlist" -> 2
            "not_available" -> 3
            else -> 4 // still pending → last
        }
}
