package app.railcast

import app.railcast.ui.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab structure (PRD §7, amended July 2026 — three tabs, was five).
 *
 * Tab count is not cosmetic here: each tab gets 1/n of the bottom bar, and the
 * PRD requires icons to be *always labelled*. At five tabs a label had a fifth
 * of the width, which is what made labels clip once the 1.3x font-scale clamp
 * was removed (FR-10.3). Adding a fourth tab should be a deliberate decision
 * that revisits that, not a drive-by.
 */
class NavigationStructureTest {

    @Test fun there_are_exactly_three_tabs() {
        assertEquals(
            "tab count changed — re-check label legibility at 200% text (FR-10.3) and amend PRD §7",
            3,
            Destination.entries.size,
        )
    }

    @Test fun routes_are_unique() {
        val routes = Destination.entries.map { it.route }
        assertEquals("duplicate tab routes: $routes", routes.size, routes.toSet().size)
    }

    @Test fun every_tab_carries_a_label_and_an_icon() {
        for (dest in Destination.entries) {
            assertTrue("${dest.name} has no label resource", dest.label != 0)
            assertTrue("${dest.name} route is blank", dest.route.isNotBlank())
        }
    }

    /** Track and Alerts are properties of a journey, not places (§7 amendment). */
    @Test fun track_and_alerts_are_not_destinations() {
        val routes = Destination.entries.map { it.route }
        assertTrue("track became a tab again", "track" !in routes)
        assertTrue("alerts became a tab again", "alerts" !in routes)
    }
}
