package app.railcast.directory

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract
import java.util.Locale

/**
 * Voice search available on every search field (FR-1.4). Uses the system
 * speech-to-text UI (ACTION_RECOGNIZE_SPEECH) so no RECORD_AUDIO permission is
 * required — the recognized text feeds the same directory autocomplete. The
 * prompt locale follows the app language so native-script dictation works
 * toward the P2 localization (FR-1.3).
 */
class VoiceSearchContract : ActivityResultContract<String, String?>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, input)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()

    companion object {
        /** True when a speech recognizer is present to handle the intent. */
        fun isAvailable(context: Context): Boolean =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .resolveActivity(context.packageManager) != null
    }
}
