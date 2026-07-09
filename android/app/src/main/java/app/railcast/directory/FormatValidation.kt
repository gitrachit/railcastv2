package app.railcast.directory

/**
 * Client-side format checks so a malformed train number / PNR never becomes a
 * server error — the UI corrects gently, inline. Pure logic; the message key
 * maps to a localized string in the Compose layer. [FR-1.5]
 */
object FormatValidation {

    enum class Field(val digits: Int) { TRAIN_NUMBER(5), PNR(10) }

    sealed interface Result {
        /** Enough correct input to proceed; [value] is the cleaned digits. */
        data class Valid(val value: String) : Result
        /** Not yet usable. [messageKey] names the inline hint; null while still typing. */
        data class Invalid(val messageKey: String?) : Result
    }

    /** Message keys the UI resolves to localized, non-blaming inline hints. */
    object Msg {
        const val TRAIN_LENGTH = "validation_train_length"
        const val PNR_LENGTH = "validation_pnr_length"
        const val NON_DIGITS = "validation_digits_only"
    }

    fun validate(field: Field, raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Invalid(null) // pristine — no scolding
        val digits = trimmed.filter { it.isDigit() }
        val hadNonDigits = digits.length != trimmed.length

        return when {
            digits.length == field.digits && !hadNonDigits -> Result.Valid(digits)
            // Right number of digits but stray separators/letters slipped in — accept, quietly cleaned.
            digits.length == field.digits -> Result.Valid(digits)
            digits.length > field.digits -> Result.Invalid(lengthMsg(field))
            hadNonDigits && digits.length < field.digits -> Result.Invalid(Msg.NON_DIGITS)
            else -> Result.Invalid(null) // still short and all digits: user is mid-typing
        }
    }

    private fun lengthMsg(field: Field) =
        if (field == Field.TRAIN_NUMBER) Msg.TRAIN_LENGTH else Msg.PNR_LENGTH
}
