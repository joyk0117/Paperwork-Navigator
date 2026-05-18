package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "LanguageIdentifier"
// ML Kit recommends at least 100 chars for reliable detection; 500 gives ample context.
private const val IDENTIFY_CHARS = 500

object LanguageIdentifier {

    /**
     * Identifies the primary language of [text] using ML Kit Language Identification.
     * Returns a BCP-47 language code (e.g. "ja", "en", "zh").
     * Returns "und" when confidence is too low to determine the language.
     */
    suspend fun identify(text: String): String {
        val sample = text.take(IDENTIFY_CHARS)
        val identifier = LanguageIdentification.getClient()
        return try {
            val code = suspendCancellableCoroutine { cont ->
                identifier.identifyLanguage(sample)
                    .addOnSuccessListener { langCode ->
                        if (cont.isActive) cont.resume(langCode)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Language identification failed: ${e.message}")
                        if (cont.isActive) cont.resume("und")
                    }
            }
            Log.d(TAG, "Identified language: $code")
            code
        } catch (e: Exception) {
            Log.w(TAG, "Language identification exception: ${e.message}")
            "und"
        } finally {
            identifier.close()
        }
    }
}
