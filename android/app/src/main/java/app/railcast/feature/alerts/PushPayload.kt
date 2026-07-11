package app.railcast.feature.alerts

/** The five alert types the user opts into (FR-7.4); mirrors the watch/push
 *  `type` union in api-contracts §5. */
enum class AlertType { CHART, DELAY, PLATFORM, CANCEL, ARRIVAL, TATKAL }

/** The one place mute-this-journey keys are built, so the mute chips on Track/
 *  PNR and the incoming-push entityKey can never drift apart (FR-7.4). The PNR
 *  form uses the MASKED value — no raw PNR ever reaches prefs storage. */
object MuteKeys {
    fun train(trainNo: String) = "train:$trainNo"
    fun pnr(pnrMasked: String) = "pnr:$pnrMasked"
}

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
        override val entityKey get() = MuteKeys.pnr(pnrMasked)
    }

    data class Delay(val trainNo: String, val delayMin: Int, val nextStation: String) : PushPayload {
        override val type get() = AlertType.DELAY
        override val entityKey get() = MuteKeys.train(trainNo)
    }

    data class PlatformChange(val trainNo: String, val stationCode: String, val platform: String) : PushPayload {
        override val type get() = AlertType.PLATFORM
        override val entityKey get() = MuteKeys.train(trainNo)
    }

    data class Disruption(val cancelled: Boolean, val trainNo: String, val runDate: String) : PushPayload {
        override val type get() = AlertType.CANCEL // cancelled OR diverted → the "cancel" opt-in
        override val entityKey get() = MuteKeys.train(trainNo)
    }

    data class ArrivalAlarm(
        val trainNo: String,
        val stationCode: String,
        val etaActual: String,
        val leadMin: Int,
    ) : PushPayload {
        override val type get() = AlertType.ARRIVAL
        override val entityKey get() = MuteKeys.train(trainNo)
    }

    data class TatkalOpen(val trainNo: String, val runDate: String, val band: String) : PushPayload {
        override val type get() = AlertType.TATKAL
        override val entityKey get() = MuteKeys.train(trainNo)
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
                "TATKAL_OPEN" -> TatkalOpen(
                    trainNo = data["trainNo"] ?: return null,
                    runDate = data["runDate"].orEmpty(),
                    band = data["band"].orEmpty(),
                )
                else -> null
            }
        }
    }
}
