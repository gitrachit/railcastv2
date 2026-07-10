package app.railcast.feature.pnr

/**
 * Client-side PNR masking (FR-4.3, invariant 2). The server already returns a
 * masked form; this masks the locally-entered value for any echo BEFORE the
 * first response, so the raw PNR is never shown back to the user in full.
 */
fun maskPnr(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    val last4 = digits.takeLast(4)
    return "••••$last4"
}
