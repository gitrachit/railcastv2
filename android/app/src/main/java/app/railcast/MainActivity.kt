package app.railcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.railcast.core.AppContainer
import app.railcast.core.design.RailcastTheme
import app.railcast.core.i18n.AppLanguage
import app.railcast.core.i18n.LanguageStore
import app.railcast.core.i18n.LocalizedContent
import app.railcast.core.poll.bindTo
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

        setContent {
            val context = LocalContext.current
            val store = remember { LanguageStore(context.applicationContext) }
            val language by store.language.collectAsState(initial = AppLanguage.DEFAULT)
            val scope = rememberCoroutineScope()

            // Restore the persisted device token so the first screen request is
            // already authenticated (contracts §7).
            LaunchedEffect(Unit) { container.session.restore() }

            LocalizedContent(language) {
                RailcastTheme {
                    RailcastApp(
                        language = language,
                        onLanguageChange = { scope.launch { store.setLanguage(it) } },
                    )
                }
            }
        }
    }
}
