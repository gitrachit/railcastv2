package app.railcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.railcast.core.AppContainer
import app.railcast.core.design.RailcastTheme
import app.railcast.core.i18n.AppLanguage
import app.railcast.core.i18n.LanguageStore
import app.railcast.core.i18n.LocalizedContent
import app.railcast.core.analytics.FirstSessionSuccess
import app.railcast.core.poll.bindTo
import app.railcast.feature.onboarding.OnboardingScreen
import app.railcast.feature.onboarding.OnboardingStore
import app.railcast.ui.RailcastApp
import kotlinx.coroutines.launch

// Single-activity Compose host (android/CLAUDE.md). The chosen language is
// applied above the whole tree so a switch re-composes instantly (FR-10.1).
class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Composition root, created once per activity. Polling follows the
        // activity lifecycle: it runs only in the foreground (PRD §6.4, NFR-3).
        container = AppContainer(this)
        container.poller.bindTo(lifecycle)
        container.notifications.ensureChannels() // alerts + alarm channels (4.8)

        setContent {
            val context = LocalContext.current
            val store = remember { LanguageStore(context.applicationContext) }
            val onboarding = remember { OnboardingStore(context.applicationContext) }
            val language by store.language.collectAsState(initial = AppLanguage.DEFAULT)
            val onboardingDone by onboarding.completed.collectAsState(initial = null)
            val online by container.connectivity.isOnline.collectAsState(initial = true)
            val scope = rememberCoroutineScope()

            // Where onboarding drops the user this launch; null → default Home.
            var startRoute by remember { mutableStateOf<String?>(null) }

            // Restore the persisted device token — or MINT one on first run —
            // so screen requests are authenticated (contracts §7). Without the
            // mint, a fresh install would get UNAUTHORIZED on every fetch.
            LaunchedEffect(Unit) { container.session.ensureToken(container.api) }

            LocalizedContent(language) {
                RailcastTheme {
                    when (onboardingDone) {
                        // null = still reading the flag; render nothing to avoid a
                        // flash of the wrong screen on cold start.
                        null -> Unit
                        false -> OnboardingScreen(
                            language = language,
                            onLanguageChange = { scope.launch { store.setLanguage(it) } },
                            onDone = { intent ->
                                startRoute = intent.route
                                // Anonymised: only the intent ordinal, no PII (FR-11.3).
                                container.analytics.log(FirstSessionSuccess(intent.ordinal))
                                scope.launch { onboarding.complete(intent) }
                            },
                        )
                        true -> RailcastApp(
                            home = container.home,
                            track = container.track,
                            pnr = container.pnr,
                            station = container.station,
                            plan = container.plan,
                            alerts = container.alerts,
                            language = language,
                            onLanguageChange = { scope.launch { store.setLanguage(it) } },
                            isOffline = !online,
                            startRoute = startRoute,
                        )
                    }
                }
            }
        }
    }
}
