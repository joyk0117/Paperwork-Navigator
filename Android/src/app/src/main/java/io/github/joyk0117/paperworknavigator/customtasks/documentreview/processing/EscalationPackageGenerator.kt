package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.AiHypothesis
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatHistoryEntry
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatRole
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.EscalationPackage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.KeyPoint
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.MaskResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.RelatedDocument
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TimelineEvent
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import com.google.ai.edge.litertlm.Contents
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TIMEOUT_MS = 30_000L

class EscalationPackageGenerator(private val llmHelper: LlmModelHelper) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generates an escalation package for expert hand-off (MF-06).
     * LLM inference produces only consultation_summary, timeline, and ai_hypotheses.
     * All other fields are assembled from ReviewResult / MaskResult without LLM calls.
     *
     * Privacy: only Tier-2 masked data is sent to the LLM — PII originals are never included.
     *
     * @throws EscalationGenerationError.ModelNotInitialized if the model is not yet loaded
     * @throws EscalationGenerationError.InferenceError on timeout or runtime failure
     * @throws EscalationGenerationError.JsonParseError if LLM output cannot be parsed
     */
    suspend fun generate(
        model: Model,
        maskResult: MaskResult,
        reviewResult: ReviewResult,
        userNotes: String,
        chatMessages: List<ChatMessage>,
        targetLanguage: String,
    ): EscalationPackage {
        if (model.instance == null) throw EscalationGenerationError.ModelNotInitialized

        llmHelper.resetConversation(
            model = model,
            systemInstruction = Contents.of(PromptBuilder.mf06SystemPrompt(targetLanguage)),
        )

        val maskedCategories = maskResult.appliedSpans
            .map { it.category }
            .distinct()
            .joinToString(", ")
            .ifEmpty { "None" }

        val trimmedNotes = userNotes.trim()
        val notesForPrompt = trimmedNotes.ifEmpty { "(no notes)" }

        val userMessage = PromptBuilder.mf06UserMessage(
            maskedText = maskResult.maskedText,
            maskedCategories = maskedCategories,
            userNotes = notesForPrompt,
            chatHistory = PromptBuilder.chatHistoryToText(chatMessages),
        )

        val rawJson = try {
            withTimeout(TIMEOUT_MS) {
                runInferenceSuspend(model, userMessage)
            }
        } catch (e: TimeoutCancellationException) {
            throw EscalationGenerationError.InferenceError("Escalation generation timed out after ${TIMEOUT_MS}ms")
        }

        // TODO: MVP ではリトライなし。精度不足の場合は FieldExtractor 同様に最大2回リトライを検討する
        val llmOutput = try {
            json.decodeFromString<EscalationLlmOutput>(stripMarkdownFences(rawJson))
        } catch (e: Exception) {
            throw EscalationGenerationError.JsonParseError(e.message)
        }

        return buildPackage(
            llmOutput = llmOutput,
            maskResult = maskResult,
            reviewResult = reviewResult,
            userNotes = trimmedNotes.ifEmpty { null },
            chatMessages = chatMessages,
            targetLanguage = targetLanguage,
        )
    }

    internal fun buildPackage(
        llmOutput: EscalationLlmOutput,
        maskResult: MaskResult,
        reviewResult: ReviewResult,
        userNotes: String?,
        chatMessages: List<ChatMessage>,
        targetLanguage: String,
    ): EscalationPackage = EscalationPackage(
        language = targetLanguage,
        maskedFields = maskResult.appliedSpans.map { it.category }.distinct(),
        consultationSummary = llmOutput.consultationSummary,
        keyPoints = buildKeyPoints(reviewResult),
        timeline = llmOutput.timeline,
        relatedDocuments = listOf(RelatedDocument(name = reviewResult.docName, note = null)),
        maskedSourceText = maskResult.maskedText,
        aiHypotheses = llmOutput.aiHypotheses,
        userNotes = userNotes,
        chatHistory = chatMessages.map { msg ->
            ChatHistoryEntry(
                role = if (msg.role == ChatRole.USER) "user" else "assistant",
                content = msg.content,
            )
        },
    )

    internal fun buildKeyPoints(reviewResult: ReviewResult): List<KeyPoint> {
        val t = reviewResult.translation
        return buildList {
            val deadlineDesc = t?.deadlineNote ?: reviewResult.deadline.noteJa
            if (deadlineDesc != null) {
                add(KeyPoint(category = "deadline", description = deadlineDesc))
            }
            if (t != null) {
                t.actionItems.forEach { add(KeyPoint(category = "action", description = it.description)) }
                t.warning?.let { add(KeyPoint(category = "warning", description = it.description)) }
                t.requiredItems.forEach { add(KeyPoint(category = "required_doc", description = it.name)) }
            } else {
                reviewResult.actionItems.forEach { add(KeyPoint(category = "action", description = it.descriptionJa)) }
                reviewResult.warning?.let { add(KeyPoint(category = "warning", description = it.descriptionJa)) }
                reviewResult.requiredItems.forEach { add(KeyPoint(category = "required_doc", description = it.nameJa)) }
            }
        }
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
                        cont.resumeWithException(EscalationGenerationError.InferenceError(message))
                    }
                },
            )
            cont.invokeOnCancellation {
                llmHelper.stopResponse(model)
            }
        }
}

// ─── Utilities ────────────────────────────────────────────────────────────────

private fun stripMarkdownFences(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    return trimmed.lines().drop(1).dropLast(1).joinToString("\n").trim()
}

// ─── LLM output (3 fields generated by inference) ────────────────────────────

@Serializable
data class EscalationLlmOutput(
    @SerialName("consultation_summary") val consultationSummary: String,
    val timeline: List<TimelineEvent> = emptyList(),
    @SerialName("ai_hypotheses") val aiHypotheses: List<AiHypothesis> = emptyList(),
)

// ─── Error types ──────────────────────────────────────────────────────────────

sealed class EscalationGenerationError : Exception() {
    data object ModelNotInitialized : EscalationGenerationError()
    data class JsonParseError(override val message: String?) : EscalationGenerationError()
    data class InferenceError(override val message: String?) : EscalationGenerationError()
}
