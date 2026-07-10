package app.railcast.core.data

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * Per-install random salt for PNR cache keys (FR-4.3, invariant 2). A bare
 * SHA-256 of a 10-digit PNR is brute-forceable offline (10^10 candidates), so
 * the key is salted with 32 random bytes minted once per install. The salt
 * lives in a separate file from the Room DB, so an exfiltrated database alone
 * cannot be reversed back to PNRs.
 */
class PnrKeySalt(private val context: Context) {
    val value: String by lazy {
        val file = File(context.filesDir, "pnr_key_salt")
        if (file.exists()) {
            file.readText()
        } else {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val hex = bytes.joinToString("") { "%02x".format(it) }
            file.writeText(hex)
            hex
        }
    }
}
