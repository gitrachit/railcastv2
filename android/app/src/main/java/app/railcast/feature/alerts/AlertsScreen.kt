package app.railcast.feature.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.design.RailcastTheme
import app.railcast.core.i18n.AppLanguage
import app.railcast.core.i18n.LanguagePicker

/**
 * Alerts (backlog 4.8, FR-7.4): per-type opt-in, quiet hours, and OEM battery
 * guidance — plus the runtime language switch. Minimal, meaningful posture:
 * everything on, quiet hours off, until the user changes it.
 */
@Composable
fun AlertsScreen(
    alerts: AlertsViewModel,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    val prefs by alerts.prefs.collectAsState(initial = AlertPrefs())

    // POST_NOTIFICATIONS (API 33+), asked in context: this screen IS the
    // notification settings, so the reason is on-screen (PRD §7, FR-7.4).
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.nav_alerts), style = MaterialTheme.typography.displaySmall, color = colors.ink)

        SectionTitle(stringResource(R.string.alerts_prefs_title))
        for (type in AlertType.entries) {
            ToggleRow(
                label = stringResource(alertTypeLabel(type)),
                checked = prefs.isOptedIn(type),
                onChange = { alerts.setOptIn(type, it) },
            )
        }

        SectionTitle(stringResource(R.string.alert_quiet_title))
        ToggleRow(
            label = stringResource(R.string.alert_quiet_enable),
            checked = prefs.quietHours.enabled,
            onChange = { alerts.setQuietHours(prefs.quietHours.copy(enabled = it)) },
        )
        if (prefs.quietHours.enabled) {
            TimeStepper(
                label = stringResource(R.string.alert_quiet_from),
                minutes = prefs.quietHours.startMin,
                onStep = { alerts.setQuietHours(prefs.quietHours.copy(startMin = stepMinutes(prefs.quietHours.startMin, it))) },
            )
            TimeStepper(
                label = stringResource(R.string.alert_quiet_to),
                minutes = prefs.quietHours.endMin,
                onStep = { alerts.setQuietHours(prefs.quietHours.copy(endMin = stepMinutes(prefs.quietHours.endMin, it))) },
            )
        }

        OemGuidanceSection()

        // Analytics opt-out (FR-11.3): honoured immediately; events are numeric-only.
        val analyticsOn by alerts.analyticsEnabled.collectAsState(initial = true)
        SectionTitle(stringResource(R.string.privacy_title))
        ToggleRow(
            label = stringResource(R.string.privacy_analytics),
            checked = analyticsOn,
            onChange = { alerts.setAnalyticsEnabled(it) },
        )

        SectionTitle(stringResource(R.string.settings_language))
        LanguagePicker(current = language, onSelect = onLanguageChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = RailcastTheme.colors.ink, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, fontSize = 15.sp, color = colors.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeStepper(label: String, minutes: Int, onStep: (Int) -> Unit) {
    val colors = RailcastTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 14.sp, color = colors.ink2, modifier = Modifier.weight(1f))
        StepArrow("−", stringResource(R.string.alert_time_earlier)) { onStep(-30) }
        Text(hhmm(minutes), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        StepArrow("+", stringResource(R.string.alert_time_later)) { onStep(30) }
    }
}

@Composable
private fun StepArrow(glyph: String, label: String, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Text(
        glyph, fontSize = 20.sp, color = colors.brand, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick).size(48.dp)
            .background(colors.brandSoft).padding(10.dp).semantics { contentDescription = label; role = Role.Button },
    )
}

@Composable
private fun OemGuidanceSection() {
    val colors = RailcastTheme.colors
    val context = LocalContext.current
    val vendor = OemGuidance.vendorOf(Build.MANUFACTURER ?: "")
    if (!OemGuidance.needsGuidance(vendor)) return
    // Progressive disclosure (design review, phase 3): most users never need
    // the OEM battery steps, so collapse them behind a one-tap prompt instead
    // of always occupying the screen.
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .heightIn(min = 48.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.alert_oem_expand),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▾" else "▸", fontSize = 16.sp, color = colors.ink3)
        }
        if (!expanded) return@Column
        Text(stringResource(oemBody(vendor)), fontSize = 13.sp, color = colors.ink2)
        Text(
            text = stringResource(R.string.alert_oem_open_settings),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.brand,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .background(colors.brandSoft)
                .border(1.dp, colors.brand, RoundedCornerShape(999.dp))
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

@StringRes
private fun alertTypeLabel(type: AlertType): Int = when (type) {
    AlertType.CHART -> R.string.alert_type_chart
    AlertType.DELAY -> R.string.alert_type_delay
    AlertType.PLATFORM -> R.string.alert_type_platform
    AlertType.CANCEL -> R.string.alert_type_cancel
    AlertType.ARRIVAL -> R.string.alert_type_arrival
    AlertType.TATKAL -> R.string.alert_type_tatkal
}

@StringRes
private fun oemBody(vendor: OemVendor): Int = when (vendor) {
    OemVendor.XIAOMI -> R.string.alert_oem_body_xiaomi
    OemVendor.OPPO_REALME -> R.string.alert_oem_body_oppo
    OemVendor.VIVO -> R.string.alert_oem_body_vivo
    OemVendor.OTHER -> R.string.alert_oem_body_xiaomi // unused (guarded by needsGuidance)
}

private fun hhmm(min: Int): String = "%02d:%02d".format((min / 60) % 24, min % 60)
private fun stepMinutes(current: Int, delta: Int): Int = ((current + delta) % 1440 + 1440) % 1440
