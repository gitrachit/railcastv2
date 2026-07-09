package app.railcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.railcast.core.design.RailcastTheme
import app.railcast.ui.RailcastApp

// Single-activity Compose host (android/CLAUDE.md). Navigation, screens, and
// the design system all live in Composables under RailcastTheme.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RailcastTheme {
                RailcastApp()
            }
        }
    }
}
