package app.railcast.feature.alerts

/**
 * Aggressive OEM battery managers silently kill background delivery, so we guide
 * the user to allow-list the app (FR-7 client). Vendor detection is pure and
 * unit-tested; the UI maps the vendor to localized steps.
 */
enum class OemVendor { XIAOMI, OPPO_REALME, VIVO, OTHER }

object OemGuidance {
    fun vendorOf(manufacturer: String): OemVendor {
        val m = manufacturer.trim().lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> OemVendor.XIAOMI
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> OemVendor.OPPO_REALME
            m.contains("vivo") || m.contains("iqoo") -> OemVendor.VIVO
            else -> OemVendor.OTHER
        }
    }

    /** True when this vendor is known to need extra battery allow-listing. */
    fun needsGuidance(vendor: OemVendor): Boolean = vendor != OemVendor.OTHER
}
