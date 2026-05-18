package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatRole
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import com.google.ai.edge.litertlm.Contents
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private const val MAX_TURNS = 10        // 10 Q&A pairs = 20 messages
private const val MAX_TOTAL_CHARS = 4_000
private const val CHAT_TIMEOUT_MS = 20_000L

class DocumentChatSession(private val llmHelper: LlmModelHelper) {

    private val _history = mutableListOf<ChatMessage>()
    private var isInitialized = false

    /**
     * Sets up the MF-07 system context from ReviewResult fields and inserts the
     * language-appropriate initial assistant greeting.
     *
     * sourceText is the raw extracted document text passed to the system prompt so the
     * LLM can answer questions about details not captured in the structured fields.
     * As an on-device inference call, passing raw text is permitted (same as MF-02).
     */
    suspend fun initialize(
        model: Model,
        reviewResult: ReviewResult,
        targetLanguage: String,
        sourceText: String,
    ) {
        llmHelper.resetConversation(
            model = model,
            systemInstruction = Contents.of(
                PromptBuilder.mf07SystemPrompt(reviewResult, targetLanguage, sourceText)
            ),
        )
        _history.clear()
        _history.add(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = PromptBuilder.mf07InitialMessage(reviewResult.docName, targetLanguage),
            )
        )
        isInitialized = true
    }

    /**
     * Sends a user message and streams response tokens via [onToken].
     * Chat history is maintained internally; ViewModel appends the returned [ChatMessage]
     * to [Review.chatMessages] for UI display only.
     *
     * On inference failure, neither the user nor the assistant message is committed to history
     * (spec §4.3 TC-ERR-04: failed assistant messages must not persist).
     *
     * @throws ChatInferenceError if [initialize] has not been called
     * @throws ChatLimitReachedException when the turn (10 Q&A) or character (4,000) limit is reached
     * @throws ChatInferenceError on timeout or runtime failure
     */
    suspend fun sendMessage(
        model: Model,
        userMessage: String,
        onToken: (String) -> Unit,
    ): ChatMessage {
        if (!isInitialized) throw ChatInferenceError("Session not initialized; call initialize() first")

        val userTurns = _history.count { it.role == ChatRole.USER }
        if (userTurns >= MAX_TURNS) {
            throw ChatLimitReachedException.TurnLimitReached
        }

        // Counts all messages (user + assistant + initial greeting) to enforce the 4,000-char budget.
        val currentTotalChars = _history.sumOf { it.content.length }
        if (currentTotalChars + userMessage.length > MAX_TOTAL_CHARS) {
            throw ChatLimitReachedException.CharLimitReached
        }

        val userChatMessage = ChatMessage(role = ChatRole.USER, content = userMessage)

        val responseText = try {
            withTimeout(CHAT_TIMEOUT_MS) {
                runInferenceSuspend(model, userMessage, onToken)
            }
        } catch (e: TimeoutCancellationException) {
            throw ChatInferenceError("Chat inference timed out after ${CHAT_TIMEOUT_MS}ms")
        }

        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = responseText)
        _history.add(userChatMessage)
        _history.add(assistantMessage)
        return assistantMessage
    }

    /** Returns the canonical chat history (initial greeting + all Q&A turns). */
    fun getChatHistory(): List<ChatMessage> = _history.toList()

    /**
     * Resets conversation state. Always call [initialize] with the new document's
     * ReviewResult before the next [sendMessage]; the underlying LlmModelHelper
     * conversation context is reset inside [initialize] via resetConversation().
     */
    fun clear() {
        _history.clear()
        isInitialized = false
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
                if (done && cont.isActive) {
                    cont.resumeWith(Result.success(accumulated.toString()))
                }
            },
            cleanUpListener = {},
            onError = { message ->
                if (cont.isActive) {
                    cont.resumeWithException(ChatInferenceError(message))
                }
            },
        )
        cont.invokeOnCancellation {
            llmHelper.stopResponse(model)
        }
    }
}

// ─── Error types ──────────────────────────────────────────────────────────────

sealed class ChatLimitReachedException : Exception() {
    data object TurnLimitReached : ChatLimitReachedException()
    data object CharLimitReached : ChatLimitReachedException()
}

class ChatInferenceError(override val message: String?) : Exception(message)
