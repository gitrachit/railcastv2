package app.railcast

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.railcast.core.design.RailcastTheme

// The five top-level destinations (docs/prototype nav). Order = bar order.
enum class RailcastTab(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    Home("home", Icons.Filled.Home, R.string.nav_home),
    Track("track", Icons.Filled.Explore, R.string.nav_track),
    Station("station", Icons.Filled.Place, R.string.nav_station),
    Plan("plan", Icons.Filled.CalendarMonth, R.string.nav_plan),
    Alerts("alerts", Icons.Filled.Notifications, R.string.nav_alerts),
}

val startTab = RailcastTab.Home

@Composable
fun RailcastApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = RailcastTheme.colors.bg,
        bottomBar = {
            NavigationBar(containerColor = RailcastTheme.colors.surface) {
                RailcastTab.entries.forEach { tab ->
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RailcastTheme.colors.brand,
                            selectedTextColor = RailcastTheme.colors.brand,
                            indicatorColor = RailcastTheme.colors.brandSoft,
                            unselectedIconColor = RailcastTheme.colors.ink3,
                            unselectedTextColor = RailcastTheme.colors.ink3,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startTab.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            RailcastTab.entries.forEach { tab ->
                composable(tab.route) { PlaceholderScreen(tab.labelRes) }
            }
        }
    }
}

// Stand-in until the feature screens land (backlog M4.x).
@Composable
private fun PlaceholderScreen(@StringRes titleRes: Int) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = RailcastTheme.colors.ink,
        )
    }
}
