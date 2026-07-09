package app.railcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.railcast.core.design.RailcastTheme
import app.railcast.core.i18n.AppLanguage
import app.railcast.core.i18n.LanguageStore
import app.railcast.core.i18n.LocalizedContent
import app.railcast.ui.RailcastApp
import kotlinx.coroutines.launch

// Single-activity Compose host (android/CLAUDE.md). The chosen language is
// applied above the whole tree so a switch re-composes instantly (FR-10.1).
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val store = remember { LanguageStore(context.applicationContext) }
            val language by store.language.collectAsState(initial = AppLanguage.DEFAULT)
            val scope = rememberCoroutineScope()

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
