package app.railcast.core.net

import kotlinx.serialization.Serializable

// Kotlin models mirror docs/api-contracts.md field-for-field (android/CLAUDE.md).
// This file covers §7 auth and §1 Track (the 3.3 vertical slice); the other
// screens' models arrive with their feature items (4.x).

// ─── §7 Auth ────────────────────────────────────────────────────────────────
@Serializable
data class DeviceAuthRequest(val platform: String = "android", val appVersion: String)

@Serializable
data class DeviceAuthResponse(val deviceToken: String)

// ─── §1 Track ─────────────────────────────────────────────────────────────
@Serializable
data class StationRef(val code: String, val name: String)

@Serializable
data class NextStation(
    val code: String,
    val name: String,
    val etaScheduled: String,
    val etaActual: String? = null,
)

@Serializable
data class TrainStatus(
    val state: String, // not_started|running|arrived|cancelled|diverted|rescheduled
    val summary: String,
    val delayMin: Int? = null,
    val lastStation: StationRef? = null,
    val nextStation: NextStation? = null,
    val lastUpdate: String,
)

@Serializable
data class RunDateChoice(val runDate: String, val label: String, val active: Boolean)

@Serializable
data class RouteStopTimes(val arr: String? = null, val dep: String? = null)

@Serializable
data class RouteStop(
    val code: String,
    val name: String,
    val km: Double,
    val day: Int,
    val platform: String? = null,
    val scheduled: RouteStopTimes,
    val actual: RouteStopTimes,
    val delayMin: Int? = null,
    val state: String, // passed|departed|next|upcoming|destination
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class Position(
    val kind: String, // "interpolated"
    val lat: Double,
    val lng: Double,
    val betweenCodes: List<String>,
    val progress: Double,
)

@Serializable
data class CoachOrder(val type: String, val number: String, val position: Int)

@Serializable
data class Reversal(val atStationCode: String, val atStationName: String)

@Serializable
data class CoachGuide(
    val referenceStation: String,
    val order: List<CoachOrder>,
    val reversals: List<Reversal>,
)

@Serializable
data class Prediction(val typicalDelayMin: Int, val atStationCode: String, val basisRuns: Int)

@Serializable
data class TrainScreen(
    val trainNo: String,
    val name: String,
    val runDateResolved: String,
    val runDateChoices: List<RunDateChoice>,
    val status: TrainStatus,
    val route: List<RouteStop>,
    val position: Position? = null,
    val coach: CoachGuide? = null,
    val prediction: Prediction? = null,
)
