package app.railcast.feature.ambient

import android.content.Context
import app.railcast.core.design.Confidence
import app.railcast.core.net.TrainScreen

/**
 * The seam between journey data and the ambient surfaces.
 *
 * HomeViewModel is deliberately Android-free so it unit-tests on the JVM;
 * handing it a Context to write a widget snapshot would undo that. It calls
 * this interface instead, and the real implementation holds the Context.
 */
fun interface AmbientSink {
    fun publish(journeys: List<AmbientJourney>)

    companion object {
        /** Used in tests and wherever the ambient layer is irrelevant. */
        val Noop = AmbientSink { }
    }
}

/**
 * Publishes to every ambient surface: the home-screen widget and the lockscreen
 * live notification. Both render from the same resolved state, so they cannot
 * show different answers for the same journey.
 */
class WidgetAmbientSink(private val context: Context) : AmbientSink {
    override fun publish(journeys: List<AmbientJourney>) {
        // The user's mutes are applied BEFORE resolution, so a muted journey
        // cannot win the urgency sort and reappear on either surface.
        val visible = AmbientSuppression.filter(context, journeys)
        AmbientSuppression.prune(context, journeys.map { it.trainNo }.toSet())

        AmbientRepository.writeSnapshot(context, visible)
        LiveJourneyNotification.render(
            context,
            Ambient.resolve(visible),
            AmbientRepository.ageSeconds(context),
        )
    }
}

/**
 * Maps a live train screen to the ambient model.
 *
 * The confidence decision is the important one: the arrival at the user's stop
 * is a *projection* while the train is still running, so it is ESTIMATED and
 * picks up the `~`. Marking it CERTAIN here would defeat the whole confidence
 * system at the one surface where copy is the only honesty channel.
 */
fun TrainScreen.toAmbientJourney(): AmbientJourney {
    val cancelled = status.state == "cancelled"
    val next = route.firstOrNull { it.state == "next" }
    return AmbientJourney(
        trainNo = trainNo,
        trainName = name,
        statusWord = status.summary,
        stationName = next?.name,
        eta = next?.actual?.arr ?: next?.scheduled?.arr,
        platform = next?.platform,
        // Still ahead of the train, so this is projected, not observed.
        confidence = if (cancelled) Confidence.UNKNOWN else Confidence.ESTIMATED,
        cancelled = cancelled,
        minutesUntilRelevant = status.delayMin,
    )
}
