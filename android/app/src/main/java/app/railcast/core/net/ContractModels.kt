package app.railcast.core.net

import kotlinx.serialization.Serializable

// Kotlin models mirror docs/api-contracts.md field-for-field (android/CLAUDE.md).
// This file covers §7 auth and §1 Track (the 3.3 vertical slice); the other
// screens' models arrive with their feature items (4.x).

// ─── §7 Auth ────────────────────────────────────────────────────────────────
@Serializable
// No default on `platform`: encodeDefaults=false silently DROPS defaulted
// fields from the wire body, and the server rejects a mint without it.
data class DeviceAuthRequest(val platform: String, val appVersion: String)

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

// ─── §2 PNR ───────────────────────────────────────────────────────────────
// The full PNR travels ONLY in the request path (TLS); responses carry the
// masked form. Never model or store the raw PNR here (FR-4.3, invariant 2).
@Serializable
data class PnrTrainRef(val no: String, val name: String)

@Serializable
data class PnrJourney(
    val date: String,
    val from: StationRef,
    val to: StationRef,
    val boardingPoint: StationRef,
    val cls: String,
    val quota: String,
    val arrivalEta: String? = null,
)

@Serializable
data class ChartStatus(val prepared: Boolean)

@Serializable
data class PnrPassenger(
    val idx: Int,
    val bookingStatus: String,
    val currentStatus: String,
    val coach: String? = null,
    val berth: Int? = null,
    val berthType: String? = null,
)

@Serializable
data class PnrFare(val total: Double)

@Serializable
data class PnrScreen(
    val pnrMasked: String, // "••••2882" — the only PNR form the client renders/stores
    val train: PnrTrainRef,
    val journey: PnrJourney,
    val chart: ChartStatus,
    val passengers: List<PnrPassenger>,
    val fare: PnrFare? = null,
    val live: TrainStatus? = null, // joined when the train is currently running
)

// ─── §3 Station ─────────────────────────────────────────────────────────────
@Serializable
data class StationTrainTime(val scheduled: String, val actual: String? = null, val delayMin: Int? = null)

@Serializable
data class StationTrain(
    val no: String,
    val name: String,
    val source: StationRef,
    val dest: StationRef,
    val platform: String? = null,
    val arrival: StationTrainTime? = null, // null = originates here
    val departure: StationTrainTime? = null, // null = terminates here
    val status: String, // "ontime" | "late" | "cancelled"
    val classes: List<String>,
)

@Serializable
data class StationScreen(
    val station: StationRef,
    val windowHrs: Int, // 2 | 4 | 8
    val trains: List<StationTrain>,
)

// ─── §4 Plan (progressive per-row hydration) ────────────────────────────────
@Serializable
data class PlanPunctuality(val pct: Int, val basisRuns: Int)

@Serializable
data class PlanRow(
    val no: String,
    val name: String,
    val dep: String,
    val arr: String,
    val durationMin: Int,
    val classes: List<String>,
    val runsOn: List<Boolean>, // [Sun..Sat]
    val punctuality: PlanPunctuality? = null,
    val availability: AvailabilityCell = AvailabilityCell.Pending,
    val fare: FareCell = FareCell.Pending,
)

@Serializable
data class PlanScreen(
    val from: StationRef,
    val to: StationRef,
    val date: String,
    val quota: String,
    val trains: List<PlanRow>,
)

/** Response of GET /screen/plan/row/:trainNo — hydrates one row. */
@Serializable
data class PlanRowHydration(val availability: RowAvailability, val fare: RowFare)

// ─── §5 Watch (create only in P1; list/delete land with 4.8 Alerts) ─────────
@Serializable
data class WatchEntity(
    val kind: String, // "pnr" | "train"
    val pnr: String? = null, // raw PNR — request path/body only, never persisted client-side
    val trainNo: String? = null,
    val runDate: String? = null,
)

@Serializable
data class WatchParams(
    val delayThresholdMin: Int? = null,
    val stationCode: String? = null,
    val leadMin: Int? = null,
    val tatkalBand: String? = null, // "ac" | "nonac" — type=tatkal (required)
)

@Serializable
data class WatchRequest(val type: String, val entity: WatchEntity, val params: WatchParams? = null)

@Serializable
data class WatchCreated(val watchId: String, val expiresAt: String)

// POST /device/push-token — registers this install for watch push fan-out.
@Serializable
data class PushTokenRequest(val fcmToken: String)

/** Contract `Ok<{}>`: an acknowledgement carrying no fields. */
@Serializable
class EmptyData
