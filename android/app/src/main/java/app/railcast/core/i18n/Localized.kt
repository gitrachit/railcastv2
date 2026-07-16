package app.railcast.core.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Applies the chosen language to everything below it by overriding the context
 * and configuration locale. Switching `language` re-composes with the new
 * locale instantly — no Activity recreation, no state loss (FR-10.1) — and
 * works on minSdk 24 without AppCompat.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current // recomposes on config change
    val localizedContext = remember(language, configuration) {
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(configuration).apply { setLocale(locale) }
        // Wrap rather than replace: a bare createConfigurationContext() result
        // severs the ContextWrapper chain to the Activity, so anything that
        // unwraps LocalContext for an owner — rememberLauncherForActivityResult
        // on Home/Station/Alerts — crashes with "No ActivityResultRegistryOwner".
        // Only the resources are overridden with the localized ones.
        LocalizedContextWrapper(context, context.createConfigurationContext(config).resources)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
        content()
    }
}

/** Base context (and its Activity chain) intact, resources localized. */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}
