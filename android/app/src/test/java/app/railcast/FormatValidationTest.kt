package app.railcast

import app.railcast.directory.FormatValidation
import app.railcast.directory.FormatValidation.Field
import app.railcast.directory.FormatValidation.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatValidationTest {

    private fun train(raw: String) = FormatValidation.validate(Field.TRAIN_NUMBER, raw)
    private fun pnr(raw: String) = FormatValidation.validate(Field.PNR, raw)

    @Test fun `5-digit train number is valid`() {
        assertEquals(Result.Valid("12780"), train("12780"))
    }

    @Test fun `10-digit pnr is valid`() {
        assertEquals(Result.Valid("2458692882"), pnr("2458692882"))
    }

    @Test fun `empty input is silently invalid — no hint while pristine`() {
        assertEquals(Result.Invalid(null), train(""))
        assertEquals(Result.Invalid(null), train("   "))
    }

    @Test fun `short all-digit input is mid-typing — no scolding`() {
        assertEquals(Result.Invalid(null), train("127"))
    }

    @Test fun `too many digits flags a length hint`() {
        assertEquals(Result.Invalid(FormatValidation.Msg.TRAIN_LENGTH), train("127801"))
        assertEquals(Result.Invalid(FormatValidation.Msg.PNR_LENGTH), pnr("24586928820"))
    }

    @Test fun `letters mixed into a short entry ask for digits only`() {
        assertEquals(Result.Invalid(FormatValidation.Msg.NON_DIGITS), train("12a"))
    }

    @Test fun `separators are cleaned when the digit count is right`() {
        assertEquals(Result.Valid("12780"), train(" 12-780 "))
        assertEquals(Result.Valid("2458692882"), pnr("245 869 2882"))
    }
}
