package io.github.joyk0117.paperworknavigator.customtasks.documentreview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class IncomingDocumentEvent {
    /** [fileName] is a hint from the sender (e.g. EXTRA_SUBJECT); may be null. */
    data class PdfUri(val uri: Uri, val fileName: String? = null) : IncomingDocumentEvent()
    data class RawText(val text: String) : IncomingDocumentEvent()
    data class ImageUri(val uri: Uri) : IncomingDocumentEvent()
}

/**
 * Activity-scoped ViewModel that parses incoming intents (ACTION_VIEW / ACTION_SEND)
 * and exposes them as one-shot events for DocumentReviewScreen to consume.
 *
 * Held by both MainActivity (via `by viewModels()`) and DocumentReviewScreen
 * (via ViewModelProvider(activity)), so both share the same instance.
 */
@HiltViewModel
class IncomingDocumentViewModel @Inject constructor() : ViewModel() {

    private val _events = MutableSharedFlow<IncomingDocumentEvent>(replay = 1)
    val events: SharedFlow<IncomingDocumentEvent> = _events.asSharedFlow()

    fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent: action=${intent.action} type=${intent.type}")
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                // Skip http(s) deep links — those are handled by GalleryNavGraph.
                // intent.type may be null when the caller relies on system MIME resolution
                // (common for file managers); the intent-filter already restricts to PDF.
                if (uri.scheme == "http" || uri.scheme == "https") return
                if (intent.type == null || intent.type == "application/pdf") {
                    intent.data = null  // prevent re-processing on Activity recreation
                    _events.tryEmit(IncomingDocumentEvent.PdfUri(uri))
                }
            }
            Intent.ACTION_SEND -> {
                val effectiveType = intent.type
                Log.d(TAG, "handleIntent ACTION_SEND: effectiveType=$effectiveType")
                when {
                    effectiveType?.startsWith("image/") == true -> {
                        val uri = getParcelableUriExtra(intent) ?: return
                        intent.action = null
                        _events.tryEmit(IncomingDocumentEvent.ImageUri(uri))
                    }
                    effectiveType == "application/pdf" || effectiveType == "*/*" -> {
                        val uri = getParcelableUriExtra(intent) ?: return
                        // EXTRA_SUBJECT often contains the filename (e.g. from Chrome downloads).
                        val fileName = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                            ?.takeIf { it.isNotBlank() }
                        Log.d(TAG, "handleIntent PdfUri: uri=$uri fileName=$fileName")
                        intent.action = null
                        _events.tryEmit(IncomingDocumentEvent.PdfUri(uri, fileName))
                    }
                    effectiveType == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                            ?.takeIf { it.isNotBlank() } ?: return
                        intent.action = null
                        _events.tryEmit(IncomingDocumentEvent.RawText(text))
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "IncomingDocumentVM"
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUriExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
