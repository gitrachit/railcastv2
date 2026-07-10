package app.railcast.core.analytics

/**
 * Privacy-respecting analytics (FR-11.3). By construction events carry ONLY
 * numeric params — `Map<String, Long>` — so no free-form string (and therefore
 * no PNR or other identifier) can ever enter the pipeline (invariant 2). The
 * closed event set instruments the §2 metrics: time-to-answer, alert latency,
 * first-session success. Consent gating lives in ConsentGatedAnalytics.
 */
enum class AnalyticsScreen { HOME, TRACK, STATION, PLAN, PNR }

sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, Long>
}

/** Time from opening a screen to its first answer being shown (§2). */
data class TimeToAnswer(val screen: AnalyticsScreen, val ms: Long) : AnalyticsEvent {
    override val name get() = "time_to_answer"
    override val params get() = mapOf("screen" to screen.ordinal.toLong(), "ms" to ms)
}

/** Push send→display latency, categorised by alert-type ordinal (no entity id). */
data class AlertLatency(val typeOrdinal: Int, val ms: Long) : AnalyticsEvent {
    override val name get() = "alert_latency"
    override val params get() = mapOf("type" to typeOrdinal.toLong(), "ms" to ms)
}

/** The user reached value on first run, tagged by chosen intent ordinal. */
data class FirstSessionSuccess(val intentOrdinal: Int) : AnalyticsEvent {
    override val name get() = "first_session_success"
    override val params get() = mapOf("intent" to intentOrdinal.toLong())
}

interface Analytics {
    fun log(event: AnalyticsEvent)
}

/** Default sink until a real backend is wired (mirrors the server NoopSender). */
object NoopAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
}

/** Honours opt-out (FR-11.3): drops every event while consent is off. */
class ConsentGatedAnalytics(
    private val delegate: Analytics,
    private val consentGiven: () -> Boolean,
) : Analytics {
    override fun log(event: AnalyticsEvent) {
        if (consentGiven()) delegate.log(event)
    }
}
