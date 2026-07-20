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
 * The five tabs (PRD §7). Nothing important deeper than two taps; icons are
 * always labelled. Inlined vector glyphs (RailcastIcons) tint with selection
 * and keep the APK lean — no icon font (NFR-1).
 */
/** PNR is reached from Home, not a bottom tab (PRD's five tabs), so it's a
 *  plain nested route rather than a [Destination]. */
private const val PNR_ROUTE = "pnr"

enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, RailcastIcons.Home),
    TRACK("track", R.string.nav_track, RailcastIcons.Train),
    STATION("station", R.string.nav_station, RailcastIcons.Place),
    PLAN("plan", R.string.nav_plan, RailcastIcons.Calendar),
    ALERTS("alerts", R.string.nav_alerts, RailcastIcons.Bell),
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
                ?: Destination.HOME.route,
            modifier = Modifier.fillMaxSize().weight(1f),
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(home, onCheckPnr = { navController.navigate(PNR_ROUTE) { launchSingleTop = true } })
            }
            composable(PNR_ROUTE) { PnrScreen(pnr, alerts, onBack = { navController.popBackStack() }) }
            composable(Destination.TRACK.route) {
                TrackScreen(
                    track = track,
                    alerts = alerts,
                    // Cancelled → alternatives via the Plan pipeline (FR-2.4).
                    onAlternatives = {
                        navController.navigate(Destination.PLAN.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Destination.STATION.route) {
                StationScreen(
                    station = station,
                    // Cancelled row → alternatives via the Plan pipeline (FR-5.1).
                    onAlternatives = {
                        navController.navigate(Destination.PLAN.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Destination.PLAN.route) { PlanScreen(plan) }
            composable(Destination.ALERTS.route) {
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
