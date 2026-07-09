package app.railcast.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.design.RailcastTheme
import app.railcast.core.i18n.AppLanguage

/**
 * First-run flow (PRD §7, FR-10.5): native-script language picker → one intent
 * question → straight into value. No tutorial carousel, no permission wall, no
 * forced login. Picking a language re-localizes this screen live (LocalizedContent
 * wraps it), so the intent question is already in the chosen script.
 */
private enum class Step { LANGUAGE, INTENT }

@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onDone: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    var step by remember { mutableStateOf(Step.LANGUAGE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            Step.LANGUAGE -> {
                Text(
                    text = stringResource(R.string.onboarding_language_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                )
                for (lang in AppLanguage.entries) {
                    LanguageRow(
                        nativeName = lang.nativeName,
                        selected = lang == language,
                        onClick = { onLanguageChange(lang) },
                    )
                }
                Spacer(Modifier.width(0.dp))
                Button(
                    onClick = { step = Step.INTENT },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brand),
                ) {
                    Text(stringResource(R.string.onboarding_continue), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Step.INTENT -> {
                Text(
                    text = stringResource(R.string.onboarding_intent_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                )
                for (intent in OnboardingIntent.entries) {
                    IntentCard(
                        icon = intent.icon,
                        title = stringResource(intent.title),
                        subtitle = stringResource(intent.subtitle),
                        onClick = { onDone(intent) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(nativeName: String, selected: Boolean, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Text(
        text = nativeName,
        fontSize = 20.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) colors.brand else colors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) colors.brandSoft else colors.surface2)
            .border(1.dp, if (selected) colors.brand else colors.line, RoundedCornerShape(14.dp))
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun IntentCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .heightIn(min = 72.dp)
            .clearAndSetSemantics { contentDescription = title }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = icon, fontSize = 26.sp, modifier = Modifier.size(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(subtitle, fontSize = 14.sp, color = colors.ink2)
        }
    }
}
