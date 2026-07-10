package app.railcast.feature.alerts

/** The five alert types the user opts into (FR-7.4); mirrors the watch/push
 *  `type` union in api-contracts §5. */
enum class AlertType { CHART, DELAY, PLATFORM, CANCEL, ARRIVAL }

/**
 * FCM `data` push payloads (api-contracts §5). They arrive as a flat
 * string→string map (FCM data messages), which the client parses and renders
 * into a localized notification. Modelled field-for-field.
 */
sealed interface PushPayload {
    val type: AlertType
    val entityKey: String // for mute-this-journey matching

    data class ChartPrepared(
        val pnrMasked: String,
        val trainNo: String,
        val trainName: String,
        val allConfirmed: Boolean,
        val coachSummary: String,
    ) : PushPayload {
        override val type get() = AlertType.CHART
        override val entityKey get() = "pnr:$pnrMasked"
    }

    data class Delay(val trainNo: String, val delayMin: Int, val nextStation: String) : PushPayload {
        override val type get() = AlertType.DELAY
        override val entityKey get() = "train:$trainNo"
    }

    data class PlatformChange(val trainNo: String, val stationCode: String, val platform: String) : PushPayload {
        override val type get() = AlertType.PLATFORM
        override val entityKey get() = "train:$trainNo"
    }

    data class Disruption(val cancelled: Boolean, val trainNo: String, val runDate: String) : PushPayload {
        override val type get() = AlertType.CANCEL // cancelled OR diverted → the "cancel" opt-in
        override val entityKey get() = "train:$trainNo"
    }

    data class ArrivalAlarm(
        val trainNo: String,
        val stationCode: String,
        val etaActual: String,
        val leadMin: Int,
    ) : PushPayload {
        override val type get() = AlertType.ARRIVAL
        override val entityKey get() = "train:$trainNo"
    }

    companion object {
        /** Parse an FCM data map by its `kind` discriminator; null if unknown/malformed. */
        fun parse(data: Map<String, String>): PushPayload? {
            return when (data["kind"]) {
                "CHART_PREPARED" -> ChartPrepared(
                    pnrMasked = data["pnrMasked"] ?: return null,
                    trainNo = data["trainNo"] ?: return null,
                    trainName = data["trainName"].orEmpty(),
                    allConfirmed = data["allConfirmed"]?.toBoolean() ?: false,
                    coachSummary = data["coachSummary"].orEmpty(),
                )
                "DELAY" -> Delay(
                    trainNo = data["trainNo"] ?: return null,
                    delayMin = data["delayMin"]?.toIntOrNull() ?: return null,
                    nextStation = data["nextStation"].orEmpty(),
                )
                "PLATFORM_CHANGE" -> PlatformChange(
                    trainNo = data["trainNo"] ?: return null,
                    stationCode = data["stationCode"].orEmpty(),
                    platform = data["platform"] ?: return null,
                )
                "CANCELLED", "DIVERTED" -> Disruption(
                    cancelled = data["kind"] == "CANCELLED",
                    trainNo = data["trainNo"] ?: return null,
                    runDate = data["runDate"].orEmpty(),
                )
                "ARRIVAL_ALARM" -> ArrivalAlarm(
                    trainNo = data["trainNo"] ?: return null,
                    stationCode = data["stationCode"] ?: return null,
                    etaActual = data["etaActual"].orEmpty(),
                    leadMin = data["leadMin"]?.toIntOrNull() ?: 0,
                )
                else -> null
            }
        }
    }
}
