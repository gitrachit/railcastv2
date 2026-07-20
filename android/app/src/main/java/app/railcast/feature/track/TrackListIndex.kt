package app.railcast.feature.track

import app.railcast.core.net.TrainScreen

/**
 * Where the coach section sits in the Track list, for the action bar's "jump to
 * my coach" scroll.
 *
 * This used to be an inline `var i = 2` with a comment asking the reader to
 * keep it in sync with the item order. It silently broke the moment the header
 * and the answer block moved out of the list for the pinned-answer layout
 * (wireframe W5) — the jump would land two items past the coach guide, which
 * is the sort of bug nobody files and everybody notices.
 *
 * Pure and tested, so the coupling is checked by CI rather than by whoever
 * remembers the comment.
 */
object TrackListIndex {

    /**
     * Optional items rendered above the coach section, in list order. The
     * header and the answer block are deliberately absent: they are pinned
     * above the scrolling list and occupy no index in it.
     */
    fun coachIndex(screen: TrainScreen, hasRunDateChoice: Boolean): Int {
        var index = 0
        if (screen.status.state == "diverted" || screen.status.state == "rescheduled") index++
        if (hasRunDateChoice) index++
        if (screen.position != null) index++
        return index
    }
}
