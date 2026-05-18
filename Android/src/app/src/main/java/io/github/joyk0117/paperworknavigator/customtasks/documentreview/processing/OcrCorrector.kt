package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import com.google.ai.edge.litertlm.Contents
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private const val TAG = "OcrCorrector"
private const val TIMEOUT_MS = 60_000L

// LiteRT-LM processes images as 16×16 patches; max_num_patches = 2520 (~645K pixels).
// Pre-scaling to MAX_BITMAP_SIDE keeps patch count well below the limit.
// Bitmaps must also be software-backed (Config.ARGB_8888): hardware-backed bitmaps
// (Config.HARDWARE) make AndroidBitmap_lockPixels() return an error; if the native
// code doesn't check the result, pixels == null → SIGSEGV in the engine thread.
private const val MAX_BITMAP_SIDE = 800

/**
 * MF-01c: Gemma 4 multimodal OCR correction.
 * Takes ML Kit OCR output and the original image(s), identifies transcription errors,
 * and returns corrected text. Falls back to the original OCR text on any failure.
 * Only used for camera/gallery image input (not PDF text extraction).
 */
class OcrCorrector(private val llmHelper: LlmModelHelper) {

    /**
     * Compares OCR text against the source image(s) using Gemma 4 multimodal inference
     * and returns corrected text. Returns [ocrText] unchanged if no errors are found or
     * if correction fails.
     *
     * @param images source page images (one per page); must be non-empty
     */
    suspend fun correct(model: Model, ocrText: String, images: List<Bitmap>): String {
        if (images.isEmpty()) return ocrText
        if (model.instance == null) return ocrText

        val scaledImages = images.map { downscale(it) }
        Log.d(TAG, "Starting OCR correction on ${scaledImages.size} image(s), ${ocrText.length} chars " +
            "(original: ${images.first().width}x${images.first().height}, " +
            "scaled: ${scaledImages.first().width}x${scaledImages.first().height})")

        llmHelper.resetConversation(
            model = model,
            supportImage = true,
            systemInstruction = Contents.of(PromptBuilder.mf01cSystemPrompt()),
        )

        val userMessage = PromptBuilder.mf01cUserMessage(ocrText)

        val raw = try {
            withTimeout(TIMEOUT_MS) {
                runInferenceSuspend(model, userMessage, scaledImages)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "OCR correction timed out after ${TIMEOUT_MS}ms — using original text")
            return ocrText
        } catch (e: Exception) {
            Log.w(TAG, "OCR correction failed: ${e.message} — using original text")
            return ocrText
        }

        val corrections = parseCorrections(raw)
        if (corrections.isEmpty()) {
            Log.d(TAG, "No corrections needed")
            return ocrText
        }

        Log.d(TAG, "Applying ${corrections.size} correction(s): ${corrections.take(3).joinToString { "\"${it.first}\"→\"${it.second}\"" }}${if (corrections.size > 3) "..." else ""}")
        return applyCorrections(ocrText, corrections)
    }

    private fun parseCorrections(raw: String): List<Pair<String, String>> {
        val trimmed = raw.trim()
        if (trimmed == "(none)" || trimmed.isEmpty()) return emptyList()
        return raw.lines()
            .filter { it.trimStart().startsWith("CORRECT:") }
            .mapNotNull { line ->
                val body = line.trimStart().removePrefix("CORRECT:").trim()
                // Pipe format:  "wrong|right"
                val pipeIdx = body.indexOf('|')
                if (pipeIdx != -1) {
                    val wrong = body.substring(0, pipeIdx)
                    val right = body.substring(pipeIdx + 1)
                    if (wrong.isNotEmpty() && right.isNotEmpty() && wrong != right) wrong to right else null
                } else {
                    // Angle bracket format: "wrong<right>" (model sometimes uses this)
                    val angleStart = body.indexOf('<')
                    val angleEnd = body.lastIndexOf('>')
                    if (angleStart != -1 && angleEnd > angleStart) {
                        val wrong = body.substring(0, angleStart)
                        val right = body.substring(angleStart + 1, angleEnd)
                        if (wrong.isNotEmpty() && right.isNotEmpty() && wrong != right) wrong to right else null
                    } else null
                }
            }
    }

    private fun applyCorrections(ocrText: String, corrections: List<Pair<String, String>>): String {
        var result = ocrText
        for ((wrong, right) in corrections) {
            result = result.replace(wrong, right)
        }
        return result
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val scale = if (w > MAX_BITMAP_SIDE || h > MAX_BITMAP_SIDE) {
            MAX_BITMAP_SIDE.toFloat() / maxOf(w, h)
        } else 1f
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        // Always produce a software-backed ARGB_8888 bitmap so that LiteRT-LM's
        // native AndroidBitmap_lockPixels() can access pixel data without error.
        val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, Rect(0, 0, w, h), Rect(0, 0, newW, newH), null)
        return result
    }

    private suspend fun runInferenceSuspend(
        model: Model,
        input: String,
        images: List<Bitmap>,
    ): String = suspendCancellableCoroutine { cont ->
        val accumulated = StringBuilder()
        llmHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, _ ->
                accumulated.append(partial)
                if (done && cont.isActive) {
                    Log.d(TAG, "inference done: ${accumulated.length} chars")
                    cont.resumeWith(Result.success(accumulated.toString()))
                }
            },
            cleanUpListener = {},
            onError = { message ->
                if (cont.isActive) cont.resumeWithException(RuntimeException(message))
            },
            images = images,
        )
        cont.invokeOnCancellation {
            llmHelper.stopResponse(model)
        }
    }
}
