package app.railcast.core.i18n

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.railcast.core.design.RailcastTheme

/**
 * A minimal inline language switch (the full native-script picker is the
 * onboarding flow, 4.1). Each option shows its own native name; selecting one
 * switches the whole app instantly (FR-10.1). ≥48dp targets, labelled.
 */
@Composable
fun LanguagePicker(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (lang in AppLanguage.entries) {
            val on = lang == current
            Text(
                text = lang.nativeName,
                color = if (on) colors.brand else colors.ink2,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(lang) })
                    .background(if (on) colors.brandSoft else colors.surface2)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
