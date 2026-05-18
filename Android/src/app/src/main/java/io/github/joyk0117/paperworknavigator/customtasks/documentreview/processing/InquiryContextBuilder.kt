package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.util.Log
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.InquiryContext
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.InquiryRecipient
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import com.google.ai.edge.litertlm.Contents
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "InquiryContextBuilder"
private const val PURPOSE_TIMEOUT_MS = 60_000L

class InquiryContextBuilder(private val llmHelper: LlmModelHelper) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Suggests 3-5 inquiry purposes based on document content (MF-06a).
     * Returns empty list on any failure — caller falls back to free-text input.
     *
     * sourceText is the raw extracted document text passed to the user message so the
     * LLM can generate more context-aware purpose suggestions. On-device inference
     * permits passing raw text (same policy as MF-02).
     */
    suspend fun suggestPurposes(
        model: Model,
        reviewResult: ReviewResult,
        targetLanguage: String,
        sourceText: String,
        chatHistory: List<ChatMessage> = emptyList(),
    ): List<String> {
        if (model.instance == null) return emptyList()

        val t = reviewResult.translation
        val useTranslation = t != null && t.language == targetLanguage
        val summary = if (useTranslation) t!!.summary else reviewResult.summaryJa
        val actionItems = (if (useTranslation) t!!.actionItems.map { it.description } else reviewResult.actionItems.map { it.descriptionJa })
            .mapIndexed { i, desc -> "${i + 1}. $desc" }
            .joinToString("\n")
            .ifEmpty { "None" }

        return try {
            llmHelper.resetConversation(
                model = model,
                systemInstruction = Contents.of(
                    PromptBuilder.mf06aSystemPrompt(targetLanguage)
                ),
            )
            val userMessage = PromptBuilder.mf06aUserMessage(
                docName = reviewResult.docName,
                summary = summary,
                actionItems = actionItems,
                targetLanguage = targetLanguage,
                sourceText = sourceText,
                chatHistory = PromptBuilder.chatHistoryToText(chatHistory),
            )
            val raw = withTimeout(PURPOSE_TIMEOUT_MS) {
                runInferenceSuspend(model, userMessage)
            }
            parseStringArray(raw)
        } catch (e: Exception) {
            Log.w(TAG, "suggestPurposes failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Assembles InquiryContext from wizard inputs — no LLM call required.
     * Calls PiiMasker.mask() internally to generate maskedSourceText.
     */
    fun buildContext(
        reviewResult: ReviewResult,
        purpose: String,
        recipient: InquiryRecipient,
        maskedPiiSpans: List<PiiSpan>,
        allPiiSpans: List<PiiSpan>,
        targetLanguage: String,
        sourceText: String,
    ): InquiryContext {
        val t = reviewResult.translation
        val useTranslation = t != null && t.language == targetLanguage
        val summary = if (useTranslation) t!!.summary else reviewResult.summaryJa
        val maskResult = PiiMasker.mask(sourceText, maskedPiiSpans)
        return InquiryContext(
            language = targetLanguage,
            recipient = recipient,
            purpose = purpose,
            documentSummary = summary,
            maskedPiiSpans = maskedPiiSpans,
            allPiiSpans = allPiiSpans,
            reviewResult = reviewResult,
            maskedSourceText = maskResult.maskedText,
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
                        cont.resumeWithException(RuntimeException(message))
                    }
                },
            )
            cont.invokeOnCancellation {
                llmHelper.stopResponse(model)
            }
        }

    private fun parseStringArray(raw: String): List<String> {
        val trimmed = raw.trim().let { s ->
            if (s.startsWith("```")) s.lines().drop(1).dropLast(1).joinToString("\n").trim() else s
        }
        val startIdx = trimmed.indexOf('[')
        val endIdx = trimmed.lastIndexOf(']')
        if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) return emptyList()
        val arrayStr = trimmed.substring(startIdx, endIdx + 1)
        return json.parseToJsonElement(arrayStr).jsonArray
            .mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }
    }
}
