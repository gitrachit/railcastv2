package app.railcast

import app.railcast.core.net.RunDateChoice
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.TrainStatus
import app.railcast.feature.track.RunDateSheet
import app.railcast.feature.track.RunLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun rdScreen(
    resolved: String,
    choices: List<RunDateChoice>,
) = TrainScreen(
    trainNo = "12780",
    name = "Goa Express",
    runDateResolved = resolved,
    runDateChoices = choices,
    status = TrainStatus(state = "running", summary = "Running", lastUpdate = "t"),
    route = emptyList(),
)

class RunDateSheetTest {

    private val choices = listOf(
        RunDateChoice("2026-07-10", "today", active = false),
        RunDateChoice("2026-07-09", "yesterday", active = true),
    )

    @Test fun `options mark the resolved run as selected and map labels`() {
        val opts = RunDateSheet.options(rdScreen("2026-07-09", choices))
        assertEquals(2, opts.size)
        assertEquals(RunLabel.TODAY, opts[0].label)
        assertEquals(RunLabel.YESTERDAY, opts[1].label)
        assertFalse(opts[0].selected)
        assertTrue(opts[1].selected) // resolved = yesterday's date
    }

    @Test fun `options never expose anything but probed run dates`() {
        // Each option's runDate comes straight from the server choices — no free date.
        val opts = RunDateSheet.options(rdScreen("2026-07-10", choices))
        assertEquals(choices.map { it.runDate }.toSet(), opts.map { it.runDate }.toSet())
    }

    @Test fun `defaultRun picks the server-detected active run`() {
        assertEquals("2026-07-09", RunDateSheet.defaultRun(rdScreen("2026-07-10", choices)))
    }

    @Test fun `defaultRun falls back to the resolved run when none is active`() {
        val inactive = choices.map { it.copy(active = false) }
        assertEquals("2026-07-10", RunDateSheet.defaultRun(rdScreen("2026-07-10", inactive)))
    }

    @Test fun `hasChoice is false with a single run`() {
        assertFalse(RunDateSheet.hasChoice(rdScreen("2026-07-10", choices.take(1))))
        assertTrue(RunDateSheet.hasChoice(rdScreen("2026-07-10", choices)))
    }

    @Test fun `label mapping is case-insensitive and defaults to today`() {
        val odd = listOf(RunDateChoice("2026-07-10", "TODAY", true), RunDateChoice("2026-07-09", "Yesterday", false))
        val opts = RunDateSheet.options(rdScreen("2026-07-10", odd))
        assertEquals(RunLabel.TODAY, opts[0].label)
        assertEquals(RunLabel.YESTERDAY, opts[1].label)
    }
}
