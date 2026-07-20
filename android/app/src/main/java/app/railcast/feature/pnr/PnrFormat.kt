package app.railcast.feature.pnr

import app.railcast.core.data.MaskedPnr

/**
 * Client-side PNR masking (FR-4.3, invariant 2). The server already returns a
 * masked form; this masks the locally-entered value for any echo BEFORE the
 * first response, so the raw PNR is never shown back to the user in full.
 *
 * Delegates to [MaskedPnr] so there is exactly one masking implementation.
 * Two copies could drift, and drift here means a PNR rendered in a form the
 * privacy rule never sanctioned.
 */
fun maskPnr(raw: String): String = MaskedPnr.of(raw).value
