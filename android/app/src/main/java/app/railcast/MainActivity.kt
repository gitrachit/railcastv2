package app.railcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.railcast.core.design.RailcastTheme

// Single-activity shell: RailcastTheme + five-tab bottom nav [backlog 3.1].
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RailcastTheme {
                RailcastApp()
            }
        }
    }
}
