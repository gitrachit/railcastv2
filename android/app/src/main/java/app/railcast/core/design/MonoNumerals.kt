package app.railcast.core.design

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily

// A number "run": a digit, then any digits/time-and-money punctuation glued to
// it (16:25, 2,470, 12951, 03.4). Words are left untouched.
private val NUMBER_RUN = Regex("""\d[\d:.,/]*""")

/**
 * The mono-numerals signature (design blueprint §2.2): in a mixed string, only
 * the number runs — times, IDs, counts, fares — take the tabular monospace
 * face; the words stay in the UI sans. Returns an [AnnotatedString] the caller
 * renders in a normal Text, so colour, size and weight still come from that
 * Text. Pure and unit-tested, so the span maths is verified rather than
 * eyeballed — and screens can adopt it one Text at a time.
 */
fun monoNumerals(text: String, mono: FontFamily = RailcastMono): AnnotatedString =
    buildAnnotatedString {
        append(text)
        for (match in NUMBER_RUN.findAll(text)) {
            addStyle(SpanStyle(fontFamily = mono), match.range.first, match.range.last + 1)
        }
    }
