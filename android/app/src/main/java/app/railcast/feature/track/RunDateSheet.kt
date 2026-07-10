package app.railcast.feature.track

import app.railcast.core.net.TrainScreen

/**
 * Run-date choice logic (FR-2.3, invariant 3). The user is NEVER shown a raw
 * date field for a live train — only "Started today / yesterday" options the
 * BFF already probed. This is the one place that decision is computed, kept
 * pure so it's unit-tested (android/CLAUDE.md: "run-date sheet logic").
 */
enum class RunLabel { TODAY, YESTERDAY }

/** One sheet row: a probed run, its today/yesterday label, and whether it's the
 *  run currently on screen. `runDate` is passed back to the API, never typed. */
data class RunDateOption(val runDate: String, val label: RunLabel, val selected: Boolean)

object RunDateSheet {

    fun options(screen: TrainScreen): List<RunDateOption> =
        screen.runDateChoices.map {
            RunDateOption(
                runDate = it.runDate,
                label = labelOf(it.label),
                selected = it.runDate == screen.runDateResolved,
            )
        }

    /** The run to show by default: the server-detected active one, else the
     *  resolved run (server already auto-picked with run=auto). */
    fun defaultRun(screen: TrainScreen): String =
        screen.runDateChoices.firstOrNull { it.active }?.runDate ?: screen.runDateResolved

    /** Offer the sheet only when there's a genuine choice to make. */
    fun hasChoice(screen: TrainScreen): Boolean = screen.runDateChoices.size > 1

    private fun labelOf(raw: String): RunLabel =
        if (raw.equals("yesterday", ignoreCase = true)) RunLabel.YESTERDAY else RunLabel.TODAY
}
