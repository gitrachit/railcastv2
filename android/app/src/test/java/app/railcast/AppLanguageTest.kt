package app.railcast

import app.railcast.core.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun resolvesKnownTags() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromTag("hi"))
    }

    @Test
    fun unknownOrNullTagFallsBackToDefault() {
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.DEFAULT)
    }

    @Test
    fun everyLanguageHasANativeName() {
        for (lang in AppLanguage.entries) {
            assert(lang.nativeName.isNotBlank()) { "${lang.tag} missing nativeName" }
        }
    }
}
