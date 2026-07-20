package app.railcast.core.data

/**
 * PNR types (FR-4.3, invariant 2: "masked in every response/log/UI").
 *
 * The rule was previously a convention — remember to call `maskPnr` and never
 * log the raw string. Conventions hold until the one call site that forgets,
 * and a PNR in a crash log or an analytics payload is a privacy incident that
 * no later fix undoes.
 *
 * These types make the safe thing the default:
 *
 *  - [RawPnr] can be sent upstream but **cannot be printed**. Its `toString()`
 *    returns the masked form, so an accidental `"looking up $pnr"` in a log,
 *    an exception message, or an analytics property emits `••••2882`. Reading
 *    the real digits requires [RawPnr.reveal], which is greppable in review.
 *  - [MaskedPnr] is the only form the UI accepts, so a screen physically
 *    cannot render the full number.
 *
 * The raw value still exists — it has to, the lookup needs it — but it can no
 * longer *escape by accident*, only deliberately.
 */
@JvmInline
value class RawPnr private constructor(private val digits: String) {

    /**
     * The real digits, for the request path only. Named to be conspicuous:
     * every call site is a deliberate, reviewable decision.
     */
    fun reveal(): String = digits

    /** The only form safe to display or persist. */
    fun masked(): MaskedPnr = MaskedPnr.of(digits)

    /**
     * Masked on purpose. Every implicit stringification -- string templates,
     * logs, exception messages, analytics -- routes here.
     */
    override fun toString(): String = masked().value

    companion object {
        /** PNRs are exactly 10 digits (FR-1.5); anything else is not a PNR. */
        const val LENGTH = 10

        fun parse(input: String): RawPnr? {
            val digits = input.filter { it.isDigit() }
            return if (digits.length == LENGTH) RawPnr(digits) else null
        }
    }
}

/**
 * A PNR that has already been reduced to its last four digits. Carrying this
 * rather than a `String` is what stops a raw value reaching a surface that
 * expected a masked one — the two are no longer the same type.
 */
@JvmInline
value class MaskedPnr(val value: String) {

    override fun toString(): String = value

    companion object {
        /** Masking is last-4 with a fixed prefix, matching the server's form. */
        fun of(raw: String): MaskedPnr {
            val digits = raw.filter { it.isDigit() }
            return MaskedPnr("••••" + digits.takeLast(4))
        }
    }
}
