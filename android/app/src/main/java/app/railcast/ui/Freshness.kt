package app.railcast.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.format.IsoTime

/**
 * Human freshness for any live surface (blueprint §5.3): "just now" · "6 min
 * ago" · "3 h ago", with an offline suffix when the data is stale/cached.
 * Shared by the board hero call sites (Track, Home) so the wording can never
 * drift, and the age maths is unit-tested in [IsoTime]. Fixes the on-device
 * bug where the raw server timestamp was shown verbatim.
 */
@Composable
fun freshnessLabel(freshness: String?, stale: Boolean): String {
    val base = when (val a = IsoTime.age(freshness, System.currentTimeMillis())) {
        IsoTime.Age.JustNow -> stringResource(R.string.freshness_just_now)
        is IsoTime.Age.Minutes -> stringResource(R.string.freshness_min_ago, a.n)
        is IsoTime.Age.Hours -> stringResource(R.string.freshness_hr_ago, a.n)
        is IsoTime.Age.Clock -> a.hhmm
        IsoTime.Age.Unknown -> stringResource(R.string.freshness_demo)
    }
    return if (stale) stringResource(R.string.freshness_offline, base) else base
}
