package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ExtractionError
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TextExtractor {

    private val PDF_MAGIC = "%PDF".toByteArray(Charsets.ISO_8859_1)

    suspend fun extract(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        when {
            mimeType == "application/pdf" -> extractPdf(context, uri)
            mimeType?.startsWith("text/") == true -> extractText(context, uri)
            mimeType != null -> throw ExtractionError.UnsupportedFormat
            hasPdfMagicBytes(context, uri) -> extractPdf(context, uri)
            else -> throw ExtractionError.UnsupportedFormat
        }
    }

    private fun hasPdfMagicBytes(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(PDF_MAGIC.size)
            val bytesRead = stream.read(header)
            bytesRead == PDF_MAGIC.size && isPdfBytes(header)
        } ?: false
    }.getOrDefault(false)

    // Internal for unit testing
    internal fun isPdfBytes(header: ByteArray): Boolean =
        header.size >= PDF_MAGIC.size &&
            header.copyOf(PDF_MAGIC.size).contentEquals(PDF_MAGIC)

    private fun extractPdf(context: Context, uri: Uri): String {
        val parcelFd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw ExtractionError.IoError(null)

        parcelFd.use { pfd ->
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: Exception) {
                throw ExtractionError.IoError(e)
            }
            renderer.use {
                val pageTexts = mutableListOf<String>()
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val pageText = page.getTextContents()
                            .joinToString(" ") { it.text }
                            .let { normalizeSpaces(it) }
                            .trim()
                        if (pageText.isNotEmpty()) {
                            pageTexts.add(pageText)
                        }
                    }
                }
                if (pageTexts.isEmpty()) {
                    throw ExtractionError.NoPdfTextLayer
                }
                return joinPageTexts(pageTexts)
            }
        }
    }

    private fun extractText(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw ExtractionError.IoError(null)
        return try {
            stream.use { readFromStream(it) }
        } catch (e: Exception) {
            throw ExtractionError.IoError(e)
        }
    }

    // Internal for unit testing
    internal fun readFromStream(stream: InputStream): String =
        stream.bufferedReader(Charsets.UTF_8).readText()

    internal fun joinPageTexts(pageTexts: List<String>): String =
        pageTexts.joinToString("\n\n")

    // Normalize horizontal whitespace (tabs, non-breaking spaces, etc.) to a single regular space.
    // PDF text extraction can yield tab characters as word separators instead of U+0020,
    // which renders inconsistently across Compose components (TextField vs Text).
    internal fun normalizeSpaces(text: String): String =
        text.replace(Regex("\\h+"), " ")
}
