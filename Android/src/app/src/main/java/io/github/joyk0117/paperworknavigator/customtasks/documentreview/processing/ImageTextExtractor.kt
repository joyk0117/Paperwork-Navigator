package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ExtractionError
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageTextExtractor {

    /**
     * @param sourceLanguage BCP-47 language code of the document (e.g. "ja", "zh", "en").
     *   Selects the appropriate ML Kit OCR model.
     */
    suspend fun extract(context: Context, uri: Uri, sourceLanguage: String): String = withContext(Dispatchers.IO) {
        val inputImage = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            throw ExtractionError.IoError(e)
        }
        runOcr(inputImage, sourceLanguage)
    }

    /**
     * @param sourceLanguage BCP-47 language code of the document (e.g. "ja", "zh", "en").
     *   Selects the appropriate ML Kit OCR model.
     */
    suspend fun extractFromBitmap(bitmap: Bitmap, sourceLanguage: String): String = withContext(Dispatchers.IO) {
        runOcr(InputImage.fromBitmap(bitmap, 0), sourceLanguage)
    }

    private fun resolveRecognizerOptions(sourceLanguage: String): TextRecognizerOptionsInterface =
        when (sourceLanguage) {
            "ja" -> JapaneseTextRecognizerOptions.Builder().build()
            "zh" -> ChineseTextRecognizerOptions.Builder().build()
            "ko" -> KoreanTextRecognizerOptions.Builder().build()
            else -> TextRecognizerOptions.DEFAULT_OPTIONS
        }

    private fun runOcr(inputImage: InputImage, sourceLanguage: String): String {
        val recognizer = TextRecognition.getClient(resolveRecognizerOptions(sourceLanguage))
        try {
            val result = try {
                Tasks.await(recognizer.process(inputImage), 30, TimeUnit.SECONDS)
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is java.io.IOException) throw ExtractionError.IoError(cause)
                throw ExtractionError.OcrFailed
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ExtractionError.OcrFailed
            } catch (e: TimeoutException) {
                throw ExtractionError.OcrFailed
            }

            val text = result.textBlocks
                .joinToString("\n") { block ->
                    block.lines.joinToString("\n") { it.text }
                }
                .trim()
                .take(TextExtractor.MAX_CHARS)

            if (text.isBlank()) throw ExtractionError.OcrFailed
            return text
        } finally {
            recognizer.close()
            // JNI ネイティブバッファの解放は GC タイミングに依存する。
            // Gemma 4 起動前の System.gc() は呼び出し側（DocumentReviewViewModel）が担当する。
        }
    }
}
