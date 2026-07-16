package app.railcast.feature.alerts

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.railcast.R
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.RailcastTheme

/**
 * Full-screen arrival alarm (FR-7.3). Launched via a full-screen intent so it
 * shows over the lock screen and turns the screen on. High-priority, bypasses
 * quiet hours by design (NotificationPolicy).
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val titleRes = intent.getIntExtra(EXTRA_TITLE_RES, R.string.alert_arrival_title)
        val bodyRes = intent.getIntExtra(EXTRA_BODY_RES, R.string.alert_arrival_body)
        val args = intent.getStringArrayListExtra(EXTRA_ARGS) ?: arrayListOf()

        setContent {
            RailcastTheme {
                AlarmContent(
                    title = stringResource(titleRes),
                    body = stringResource(bodyRes, *args.toTypedArray()),
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        const val EXTRA_TITLE_RES = "title_res"
        const val EXTRA_BODY_RES = "body_res"
        const val EXTRA_ARGS = "args"
    }
}

@Composable
private fun AlarmContent(title: String, body: String, onDismiss: () -> Unit) {
    val colors = RailcastTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(RailcastIcons.Alarm, contentDescription = null, tint = colors.brand, modifier = Modifier.size(72.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.ink, modifier = Modifier.padding(top = 16.dp))
        Text(body, fontSize = 16.sp, color = colors.ink2, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 40.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brand),
        ) {
            Text(stringResource(R.string.alert_alarm_dismiss), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
