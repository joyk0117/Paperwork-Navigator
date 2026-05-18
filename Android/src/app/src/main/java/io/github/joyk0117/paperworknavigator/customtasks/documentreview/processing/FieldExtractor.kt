package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.util.Log
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ActionItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DeadlineInfo
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.FieldExtractionError
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.RequiredItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.Warning
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import com.google.ai.edge.litertlm.Contents
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private const val TAG = "FieldExtractor"
// 150 s: Gemma 4 E2B generates line-format output at ~25 chars/sec after a ~15 s prefill.
private const val TIMEOUT_MS = 150_000L
private const val MAX_ATTEMPTS = 3

class FieldExtractor(private val llmHelper: LlmModelHelper) {

    /**
     * Extracts structured information from document text using Gemma 4 (MF-02), 9-field format.
     * Retries up to 2 times on parse failure. PII span building is delegated to mergeEntities().
     *
     * @param sourceLanguage BCP-47 language code pre-identified by ML Kit (e.g. "ja", "en", "und")
     * @throws FieldExtractionError.ModelNotInitialized if the model is not yet loaded
     * @throws FieldExtractionError.InferenceError on timeout or runtime error
     * @throws FieldExtractionError.JsonParseError if all 3 attempts fail to produce valid output
     */
    suspend fun extract(
        model: Model,
        text: String,
        sourceLanguage: String,
        onProgress: (String) -> Unit,
    ): ReviewResult {
        if (model.instance == null) throw FieldExtractionError.ModelNotInitialized

        var lastParseError: String? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            llmHelper.resetConversation(
                model = model,
                systemInstruction = Contents.of(PromptBuilder.mf02SystemPrompt()),
            )
            val userMessage = PromptBuilder.mf02UserMessage(text, lastParseError)
            Log.d(TAG, "extract: attempt ${attempt + 1}/$MAX_ATTEMPTS")

            val raw = try {
                withTimeout(TIMEOUT_MS) {
                    runInferenceSuspend(model, userMessage, onProgress)
                }
            } catch (e: TimeoutCancellationException) {
                throw FieldExtractionError.InferenceError("Inference timed out after ${TIMEOUT_MS}ms")
            } catch (e: FieldExtractionError.InferenceError) {
                throw e
            }

            try {
                Log.d(TAG, "extract: parsing ${raw.length} chars")
                return parseLineFormat(raw, sourceLanguage)
            } catch (e: Exception) {
                lastParseError = e.message ?: "parse error"
                Log.w(TAG, "extract: parse failed on attempt ${attempt + 1}: $lastParseError")
            }
        }

        throw FieldExtractionError.JsonParseError
    }

    /**
     * Parses completed lines from a partial LLM output stream.
     * The last line is dropped because it may be incomplete.
     * Returns key-value pairs where neither key nor value is empty or "(none)".
     */
    internal fun parsePartialLines(raw: String): List<Pair<String, String>> =
        raw.lines()
            .dropLast(1)
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx == -1) return@mapNotNull null
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isEmpty() || value.isEmpty() || value == "(none)") null
                else key to value
            }

    internal fun parseLineFormat(raw: String, sourceLanguage: String = "und"): ReviewResult {
        val map = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }

        val docName = map["DOC_NAME"] ?: throw IllegalStateException("DOC_NAME missing")

        fun str(key: String): String? = map[key]?.takeIf { it != "(none)" && it.isNotBlank() }

        fun parseItems(key: String): List<String> {
            val v = str(key) ?: return emptyList()
            return v.split("|||").map { it.trim() }.filter { it.isNotEmpty() && it != "(none)" }
        }

        fun padId(prefix: String, i: Int) = "${prefix}_${(i + 1).toString().padStart(2, '0')}"

        val actionItems = parseItems("ACTION_ITEMS").mapIndexed { i, desc ->
            ActionItem(id = padId("action", i), descriptionJa = desc, priority = i + 1)
        }

        val requiredItems = parseItems("REQUIRED_ITEMS").mapIndexed { i, item ->
            val parts = item.split("|", limit = 2)
            RequiredItem(
                id = padId("item", i),
                nameJa = parts[0].trim(),
                noteJa = parts.getOrNull(1)?.trim()?.takeIf { it != "(none)" },
            )
        }

        // WARNING: <severity>|<description> or (none)  (single, most important warning only)
        val warningRaw = str("WARNING")
        val warning = if (warningRaw != null) {
            val parts = warningRaw.split("|", limit = 2)
            val hasTwoParts = parts.size >= 2
            Warning(
                id = "warn_01",
                severity = if (hasTwoParts) parts[0].trim() else "medium",
                descriptionJa = if (hasTwoParts) parts[1].trim() else parts[0].trim(),
            )
        } else null

        return ReviewResult(
            docName = docName,
            issuerName = str("ISSUER_NAME"),
            applicantName = str("APPLICANT_NAME"),
            otherName = str("OTHER_NAME"),
            importance = str("IMPORTANCE") ?: "medium",
            summaryJa = str("SUMMARY") ?: "",
            deadline = DeadlineInfo(),
            actionItems = actionItems,
            requiredItems = requiredItems,
            warning = warning,
            sourceLanguage = sourceLanguage,
        )
    }

    private suspend fun runInferenceSuspend(
        model: Model,
        input: String,
        onToken: (String) -> Unit,
    ): String = suspendCancellableCoroutine { cont ->
        val accumulated = StringBuilder()
        llmHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, _ ->
                accumulated.append(partial)
                onToken(partial)
                if (accumulated.length % 200 == 0) {
                    Log.d(TAG, "accumulated ${accumulated.length} chars | tail: ${accumulated.takeLast(80)}")
                }
                if (done && cont.isActive) {
                    Log.d(TAG, "inference done: ${accumulated.length} chars total")
                    cont.resumeWith(Result.success(accumulated.toString()))
                }
            },
            cleanUpListener = {},
            onError = { message ->
                if (cont.isActive) {
                    cont.resumeWithException(FieldExtractionError.InferenceError(message))
                }
            },
        )
        cont.invokeOnCancellation {
            llmHelper.stopResponse(model)
        }
    }
}
