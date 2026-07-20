package app.railcast.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.railcast.R
import app.railcast.core.design.RailcastIcons
import app.railcast.core.i18n.AppLanguage
import app.railcast.feature.alerts.AlertsScreen
import app.railcast.feature.alerts.AlertsViewModel
import app.railcast.feature.home.HomeScreen
import app.railcast.feature.home.HomeViewModel
import app.railcast.feature.pnr.PnrScreen
import app.railcast.feature.pnr.PnrViewModel
import app.railcast.feature.track.TrackViewModel
import app.railcast.feature.plan.PlanScreen
import app.railcast.feature.plan.PlanViewModel
import app.railcast.feature.station.StationScreen
import app.railcast.feature.station.StationViewModel
import app.railcast.feature.track.TrackScreen

/**
 * The three tabs (PRD §7, amended July 2026 — was five). Nothing important
 * deeper than two taps; icons are always labelled. Inlined vector glyphs
 * (RailcastIcons) tint with selection and keep the APK lean — no icon font
 * (NFR-1).
 *
 * Track and Alerts stopped being destinations: tracking is what a journey *is*,
 * not a place you go to do it, and an alert is a property of a journey rather
 * than a sibling of it. Station and Plan merged because both are "find me
 * something I don't have yet".
 */
/** Reached from within Journeys, not from the bottom bar, so these are plain
 *  nested routes rather than [Destination]s. */
private const val PNR_ROUTE = "pnr"
private const val TRACK_ROUTE = "track"

enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    JOURNEYS("journeys", R.string.nav_journeys, RailcastIcons.Train),
    FIND("find", R.string.nav_find, RailcastIcons.Search),
    YOU("you", R.string.nav_you, RailcastIcons.Bell),
}

@Composable
fun RailcastApp(
    home: HomeViewModel,
    track: TrackViewModel,
    pnr: PnrViewModel,
    station: StationViewModel,
    plan: PlanViewModel,
    alerts: AlertsViewModel,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    sunlight: Boolean,
    onSunlightChange: (Boolean) -> Unit,
    isOffline: Boolean = false,
    startRoute: String? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            RailcastBottomBar(
                selected = { dest -> currentRoute?.hierarchy?.any { it.route == dest.route } == true },
                onSelect = { dest ->
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        // Offline strip above every screen; content keeps its cached data (FR-9.1).
        OfflineBanner(visible = isOffline)
        NavHost(
            navController = navController,
            // Onboarding drops the user on the tab matching their chosen intent
            // (PRD §7); otherwise Home. Unknown routes fall back to Home.
            startDestination = Destination.entries.firstOrNull { it.route == startRoute }?.route
                ?: Destination.JOURNEYS.route,
            modifier = Modifier.fillMaxSize().weight(1f),
        ) {
            // Cancelled train / cancelled board row → alternatives, which now
            // live inside Find rather than being their own tab (FR-2.4, FR-5.1).
            val toAlternatives: () -> Unit = {
                navController.navigate(Destination.FIND.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            composable(Destination.JOURNEYS.route) {
                HomeScreen(
                    home,
                    onCheckPnr = { navController.navigate(PNR_ROUTE) { launchSingleTop = true } },
                    // A journey card IS the way into tracking now that Track is
                    // not a tab — without this the flow would have no entrance.
                    onOpenTrain = { trainNo ->
                        track.open(trainNo)
                        navController.navigate(TRACK_ROUTE) { launchSingleTop = true }
                    },
                )
            }
            composable(PNR_ROUTE) { PnrScreen(pnr, alerts, onBack = { navController.popBackStack() }) }
            composable(TRACK_ROUTE) {
                TrackScreen(track = track, alerts = alerts, onAlternatives = toAlternatives)
            }
            composable(Destination.FIND.route) {
                FindScreen(station = station, plan = plan, onAlternatives = toAlternatives)
            }
            composable(Destination.YOU.route) {
                AlertsScreen(
                    alerts = alerts,
                    language = language,
                    onLanguageChange = onLanguageChange,
                    sunlight = sunlight,
                    onSunlightChange = onSunlightChange,
                )
            }
        }
      }
    }
}
