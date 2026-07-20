package app.railcast.directory

/**
 * The omni-input classifier (wireframe W8).
 *
 * The user should never have to tell the app what *kind* of thing they typed.
 * `12951`, `4512882882`, `Bhopal` and `Delhi to Goa` all go in one field, and
 * the shape of the input decides what it means. Mode toggles are where errors
 * live: picking the wrong one turns a valid query into an error message that
 * blames the user for the app's filing system.
 *
 * Classification is by shape only and never blocks — an ambiguous or partial
 * query still searches the directory. The classifier's job is to *rank* what
 * the user probably meant, not to gatekeep.
 */
object QueryClassifier {

    /** Words that mean "from A to B" across the launch languages. */
    private val ROUTE_SEPARATORS = listOf(
        " to ", " se ", " → ", "->", " - ", " se ", " तक ", " से ",
    )

    sealed interface Intent {
        /** Exactly 5 digits — a train number (FR-1.5). */
        data class TrainNumber(val digits: String) : Intent

        /** Exactly 10 digits — a PNR. Never rendered back in full (FR-4.3). */
        data class Pnr(val digits: String) : Intent

        /** "Delhi to Goa" — a journey to plan. */
        data class Route(val from: String, val to: String) : Intent

        /** Anything else: search names in the offline directory. */
        data class FreeText(val text: String) : Intent

        /** Nothing typed yet. */
        data object Empty : Intent
    }

    fun classify(raw: String): Intent {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Intent.Empty

        // Route first: "12951 to Goa" is a route, not a train number, and
        // checking digits first would misread it.
        routeParts(trimmed)?.let { (from, to) -> return Intent.Route(from, to) }

        val digits = trimmed.filter { it.isDigit() }
        val isAllDigits = digits.length == trimmed.count { !it.isWhitespace() && it != '-' }
        if (isAllDigits && digits.isNotEmpty()) {
            return when (digits.length) {
                FormatValidation.Field.TRAIN_NUMBER.digits -> Intent.TrainNumber(digits)
                FormatValidation.Field.PNR.digits -> Intent.Pnr(digits)
                // A partial number is still free text: the directory can match
                // "229" against train numbers while the user keeps typing.
                else -> Intent.FreeText(trimmed)
            }
        }
        return Intent.FreeText(trimmed)
    }

    /**
     * Splits "Delhi to Goa" into its ends. Returns null when there is no
     * separator, or when either side is empty — "Delhi to " is someone still
     * typing, not a route.
     */
    private fun routeParts(input: String): Pair<String, String>? {
        val lower = input.lowercase()
        for (sep in ROUTE_SEPARATORS) {
            val idx = lower.indexOf(sep)
            if (idx <= 0) continue
            val from = input.substring(0, idx).trim()
            val to = input.substring(idx + sep.length).trim()
            if (from.isNotEmpty() && to.isNotEmpty()) return from to to
        }
        return null
    }

    /**
     * The inline hint for a numeric query that is *nearly* a train number or a
     * PNR — guidance while typing, never an error after submitting (FR-1.5).
     * Null when there is nothing useful to say.
     */
    fun hint(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length != trimmed.length) return null // not a pure number
        return when {
            digits.length in 1..4 -> FormatValidation.Msg.TRAIN_LENGTH
            digits.length in 6..9 -> FormatValidation.Msg.PNR_LENGTH
            digits.length > 10 -> FormatValidation.Msg.PNR_LENGTH
            else -> null // 5 or 10: a valid shape, say nothing
        }
    }
}
