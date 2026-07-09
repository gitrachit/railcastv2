package app.railcast.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.railcast.R
import app.railcast.core.design.RailcastTheme
import app.railcast.core.i18n.AppLanguage
import app.railcast.core.i18n.LanguagePicker

// Alerts doubles as the settings home until the full prefs screen lands (4.8);
// the language switch lives here so the runtime switch is reachable (FR-10.1).
@Composable
fun AlertsScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_alerts),
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
        )
        Text(text = stringResource(R.string.placeholder_alerts), color = colors.ink2)

        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            modifier = Modifier.padding(top = 16.dp),
        )
        LanguagePicker(current = language, onSelect = onLanguageChange)
    }
}
