package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.Translation
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TranslatedActionItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TranslatedRequiredItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TranslatedWarning
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import com.google.ai.edge.litertlm.Contents
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private const val TIMEOUT_MS = 60_000L
private const val MAX_ATTEMPTS = 3

class Translator(private val llmHelper: LlmModelHelper) {

    /**
     * Translates the Japanese fields of a ReviewResult into the target language (MF-03).
     * Uses line-format output (same approach as MF-02) to avoid JSON collapse in Gemma 4 E2B.
     * Retries up to 2 times on parse failure.
     *
     * @throws TranslationError.ModelNotInitialized if the model is not yet loaded
     * @throws TranslationError.InferenceError on timeout or runtime error
     * @throws TranslationError.JsonParseError if all 3 attempts fail to produce valid output
     */
    suspend fun translate(
        model: Model,
        reviewResult: ReviewResult,
        targetLanguage: String,
        piiSpans: List<PiiSpan> = emptyList(),
    ): ReviewResult {
        if (model.instance == null) throw TranslationError.ModelNotInitialized

        val sourceLanguage = reviewResult.sourceLanguage
        val rawFieldsText = buildFieldsText(reviewResult)
        val fieldsText = if (piiSpans.isEmpty()) rawFieldsText
                         else PiiMasker.mask(rawFieldsText, piiSpans).maskedText

        var lastParseError: String? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            llmHelper.resetConversation(
                model = model,
                systemInstruction = Contents.of(PromptBuilder.mf03SystemPrompt(sourceLanguage, targetLanguage)),
            )
            val userMessage = PromptBuilder.mf03UserMessage(fieldsText, sourceLanguage, targetLanguage, lastParseError)

            val raw = try {
                withTimeout(TIMEOUT_MS) {
                    runInferenceSuspend(model, userMessage)
                }
            } catch (e: TimeoutCancellationException) {
                throw TranslationError.InferenceError("Translation timed out after ${TIMEOUT_MS}ms")
            }

            try {
                val translation = parseTranslationLines(raw, targetLanguage)
                return reviewResult.copy(translation = translation)
            } catch (e: Exception) {
                lastParseError = e.message ?: "parse error"
            }
        }

        throw TranslationError.JsonParseError(lastParseError)
    }

    /**
     * Builds the line-format text of translatable fields to send to the LLM.
     * Uses the same KEY: value / ||| separator convention as MF-02 to reduce LLM parsing burden.
     */
    internal fun buildFieldsText(reviewResult: ReviewResult): String {
        val actionItemsLine = if (reviewResult.actionItems.isEmpty()) "(none)"
        else reviewResult.actionItems.joinToString("|||") { "${it.id}|${it.descriptionJa}" }

        val requiredItemsLine = if (reviewResult.requiredItems.isEmpty()) "(none)"
        else reviewResult.requiredItems.joinToString("|||") { "${it.id}|${it.nameJa}|${it.noteJa ?: "(none)"}" }

        val warningLine = reviewResult.warning?.descriptionJa ?: "(none)"

        return buildString {
            // Replace newlines so the parser can read SUMMARY as a single line.
            appendLine("SUMMARY: ${reviewResult.summaryJa.replace('\n', ' ')}")
            appendLine("DEADLINE_NOTE: ${reviewResult.deadline.noteJa ?: "(none)"}")
            appendLine("ACTION_ITEMS: $actionItemsLine")
            appendLine("REQUIRED_ITEMS: $requiredItemsLine")
            append("WARNING: $warningLine")
        }
    }

    /**
     * Parses the LLM's line-format translation output into a Translation object.
     * Throws IllegalStateException if required fields are missing or malformed.
     */
    internal fun parseTranslationLines(raw: String, targetLanguage: String): Translation {
        val map = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) map[key] = value
        }

        fun str(key: String): String? = map[key]?.takeIf { it != "(none)" && it.isNotBlank() }

        val summary = str("SUMMARY") ?: throw IllegalStateException("SUMMARY missing or empty")

        val actionItems = str("ACTION_ITEMS")
            ?.split("|||")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it != "(none)" }
            ?.map { item ->
                val parts = item.split("|", limit = 2)
                if (parts.size < 2) throw IllegalStateException("ACTION_ITEMS format error: $item")
                TranslatedActionItem(id = parts[0].trim(), description = parts[1].trim())
            } ?: emptyList()

        val requiredItems = str("REQUIRED_ITEMS")
            ?.split("|||")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it != "(none)" }
            ?.map { item ->
                val parts = item.split("|", limit = 3)
                if (parts.size < 2) throw IllegalStateException("REQUIRED_ITEMS format error: $item")
                TranslatedRequiredItem(
                    id = parts[0].trim(),
                    name = parts[1].trim(),
                    note = parts.getOrNull(2)?.trim()?.takeIf { it != "(none)" },
                )
            } ?: emptyList()

        val warningDesc = str("WARNING")
        val translatedWarning = if (warningDesc != null) TranslatedWarning(description = warningDesc) else null

        return Translation(
            language = targetLanguage,
            summary = summary,
            deadlineNote = str("DEADLINE_NOTE"),
            actionItems = actionItems,
            requiredItems = requiredItems,
            warning = translatedWarning,
        )
    }

    private suspend fun runInferenceSuspend(model: Model, input: String): String =
        suspendCancellableCoroutine { cont ->
            val accumulated = StringBuilder()
            llmHelper.runInference(
                model = model,
                input = input,
                resultListener = { partial, done, _ ->
                    accumulated.append(partial)
                    if (done && cont.isActive) {
                        cont.resumeWith(Result.success(accumulated.toString()))
                    }
                },
                cleanUpListener = {},
                onError = { message ->
                    if (cont.isActive) {
                        cont.resumeWithException(TranslationError.InferenceError(message))
                    }
                },
            )
            cont.invokeOnCancellation {
                llmHelper.stopResponse(model)
            }
        }
}

// ─── Error types ──────────────────────────────────────────────────────────────

sealed class TranslationError : Exception() {
    data object ModelNotInitialized : TranslationError()
    data class JsonParseError(override val message: String?) : TranslationError()
    data class InferenceError(override val message: String?) : TranslationError()
}
