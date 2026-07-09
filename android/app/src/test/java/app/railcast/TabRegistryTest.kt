package app.railcast

import org.junit.Assert.assertEquals
import org.junit.Test

// Shell contract: five tabs, unique routes/labels, Home first. [backlog 3.1]
class TabRegistryTest {

    @Test
    fun fiveTabsInPrototypeOrder() {
        assertEquals(
            listOf("home", "track", "station", "plan", "alerts"),
            RailcastTab.entries.map { it.route },
        )
    }

    @Test
    fun routesAndLabelsAreUnique() {
        assertEquals(RailcastTab.entries.size, RailcastTab.entries.map { it.route }.distinct().size)
        assertEquals(RailcastTab.entries.size, RailcastTab.entries.map { it.labelRes }.distinct().size)
        assertEquals(RailcastTab.entries.size, RailcastTab.entries.map { it.icon }.distinct().size)
    }

    @Test
    fun homeIsTheStartDestination() {
        assertEquals(RailcastTab.Home, startTab)
    }
}
