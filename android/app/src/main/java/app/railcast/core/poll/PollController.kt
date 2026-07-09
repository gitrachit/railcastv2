package app.railcast.core.poll

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * THE single owner of every foreground refresh loop (PRD §6.4, android/CLAUDE.md).
 * No screen runs its own timer — each registers a key + cadence + fetch, and
 * this controller:
 *   - refreshes immediately on register/resume,
 *   - backs off (2×, 4×, … capped) while the payload is unchanged, to save
 *     battery/data (NFR-3) — resetting to the base cadence the moment it changes,
 *   - stops all loops on background and resumes them (with an immediate refresh)
 *     on foreground.
 *
 * Pure Kotlin + coroutines — no Android deps — so it's driven by a virtual clock
 * in tests. Lifecycle wiring lives in PollLifecycleBridge.
 */
class PollController(private val scope: CoroutineScope) {

    /** One refresh. Returns a content signature to compare across polls (so an
     *  unchanged payload backs off); null means the refresh failed (retry at
     *  base cadence, no back-off). */
    fun interface PollFetch {
        suspend fun refresh(): String?
    }

    private class Loop(val cadenceMs: Long, val fetch: PollFetch) {
        var job: Job? = null
        var lastSignature: String? = null
    }

    private val loops = LinkedHashMap<String, Loop>()
    private var foreground = true

    /** Register (or replace) a refresh loop. Starts immediately if foregrounded. */
    fun register(key: String, cadenceMs: Long, fetch: PollFetch) {
        loops[key]?.job?.cancel()
        val loop = Loop(cadenceMs, fetch)
        loops[key] = loop
        if (foreground) startLoop(key, loop)
    }

    fun unregister(key: String) {
        loops.remove(key)?.job?.cancel()
    }

    /** App resumed: restart every loop with an immediate refresh (PRD §6.4). */
    fun onForeground() {
        foreground = true
        for ((key, loop) in loops) startLoop(key, loop)
    }

    /** App backgrounded: stop all polling (background = push only, NFR-3). */
    fun onBackground() {
        foreground = false
        for (loop in loops.values) {
            loop.job?.cancel()
            loop.job = null
        }
    }

    val activeLoopCount: Int get() = loops.values.count { it.job?.isActive == true }

    private fun startLoop(key: String, loop: Loop) {
        loop.job?.cancel()
        loop.job = scope.launch {
            var identicalStreak = 0
            while (isActive) {
                val signature = loop.fetch.refresh()
                identicalStreak =
                    if (signature != null && signature == loop.lastSignature) identicalStreak + 1 else 0
                if (signature != null) loop.lastSignature = signature

                val multiplier = 1L shl identicalStreak.coerceAtMost(MAX_BACKOFF_SHIFT)
                delay(loop.cadenceMs * multiplier)
            }
        }
    }

    private companion object {
        const val MAX_BACKOFF_SHIFT = 3 // cap at 8× the base cadence
    }
}
