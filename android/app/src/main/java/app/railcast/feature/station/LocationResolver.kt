package app.railcast.feature.station

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper

/**
 * Resolves a coarse device location for "trains near me" (FR-5.2) using the
 * framework LocationManager only — no Play Services dependency (NFR-1).
 * Coarse accuracy is plenty for nearest-station resolution. Callers must hold
 * ACCESS_COARSE_LOCATION; a SecurityException (e.g. permission revoked
 * mid-flight) resolves to null rather than crashing.
 */
object LocationResolver {

    fun resolve(context: Context, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
            ?: return onResult(null)
        try {
            // Freshest last-known fix from any provider is usually enough.
            val last = lm.allProviders
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null) return onResult(last)

            val provider = preferredProvider(lm) ?: return onResult(null)
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, context.mainExecutor) { onResult(it) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(
                    provider,
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) = onResult(location)
                        @Deprecated("framework callback")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(p: String) = Unit
                        override fun onProviderDisabled(p: String) = onResult(null)
                    },
                    Looper.getMainLooper(),
                )
            }
        } catch (_: SecurityException) {
            onResult(null)
        }
    }

    private fun preferredProvider(lm: LocationManager): String? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.allProviders.contains(it) }
}
