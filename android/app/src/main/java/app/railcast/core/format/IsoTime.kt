package app.railcast.core.format

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tiny, dependency-free ISO-8601 display formatter. minSdk 24 has no java.time
 * without desugaring, and pulling that in for two helpers isn't worth the APK
 * weight (NFR-1) — so this parses by hand and is unit-tested instead.
 *
 * Two jobs:
 *  - [clock]: a wall-clock "HH:mm" for schedule times. Indian train times arrive
 *    with the +05:30 offset, so the time component already IS the IST clock.
 *  - [ageLabel]: a human "just now / 6 min ago" for freshness stamps, computed
 *    from the UTC instant so it's correct regardless of the device timezone.
 */
object IsoTime {
    private val CLOCK = Regex("""T(\d{2}:\d{2})""")
    private val FULL = Regex(
        """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})""",
    )

    /** "2026-07-18T02:50:00+05:30" → "02:50". Blank when absent/unparseable. */
    fun clock(iso: String?): String = iso?.let { CLOCK.find(it)?.groupValues?.get(1) } ?: ""

    /** "2026-07-18" → "Sat, 18 Jul", localized to [locale]. Falls back to the
     *  raw string if it isn't a plain date. Locale defaults to the app's chosen
     *  language (LocalizedContent sets the default), so weekday/month names
     *  translate without a per-name resource table. */
    fun friendlyDate(dateIso: String, locale: Locale = Locale.getDefault()): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateIso) ?: return dateIso
        SimpleDateFormat("EEE, d MMM", locale).format(parsed)
    }.getOrDefault(dateIso)

    /** Epoch millis (UTC) for an ISO instant with `Z` or a `±HH:MM` offset. */
    fun epochMillis(iso: String): Long? {
        val m = FULL.find(iso) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        val local = daysFromCivil(y.toInt(), mo.toInt(), d.toInt()) * 86_400L +
            h.toInt() * 3_600L + mi.toInt() * 60L + s.toInt()
        val off = when (val z = m.groupValues[7]) {
            "Z" -> 0L
            else -> {
                val sign = if (z[0] == '-') -1 else 1
                sign * (z.substring(1, 3).toLong() * 3_600L + z.substring(4, 6).toLong() * 60L)
            }
        }
        return (local - off) * 1_000L
    }

    /** A freshness bucket — the *wording* is the caller's (string resources,
     *  EN/HI), this only does the timezone-correct maths. */
    sealed interface Age {
        data object JustNow : Age
        data class Minutes(val n: Int) : Age
        data class Hours(val n: Int) : Age
        data class Days(val n: Int) : Age // "3 d ago" — unambiguous for stale offline cards
        data object Unknown : Age
    }

    fun age(iso: String?, nowMillis: Long): Age {
        if (iso == null) return Age.Unknown
        val t = epochMillis(iso) ?: return Age.Unknown
        val d = nowMillis - t
        return when {
            d < 45_000L -> Age.JustNow
            d < 3_600_000L -> Age.Minutes((d / 60_000L).toInt())
            d < 86_400_000L -> Age.Hours((d / 3_600_000L).toInt())
            else -> Age.Days((d / 86_400_000L).toInt())
        }
    }

    // Howard Hinnant's days-from-civil: civil date → days since 1970-01-01.
    private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146_097L + doe - 719_468L
    }
}
