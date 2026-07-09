package app.railcast.core.i18n

/**
 * Launch languages (FR-10.1). P1 ships EN + HI at full parity; the enum is the
 * one place to add the remaining launch languages — each new entry just needs
 * a values-<tag>/strings.xml with full parity (StringsParityTest enforces it).
 * `nativeName` is shown in its own script in the picker (native-script names).
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी");

    companion object {
        val DEFAULT = ENGLISH

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}
