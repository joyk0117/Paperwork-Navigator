package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.util.Log
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DetectedEntity
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import com.google.ai.edge.litertlm.Contents
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private const val TAG = "EntityAnnotator"
private const val TIMEOUT_MS = 120_000L

private val ANNOTATABLE_TYPES = setOf("DATE_TIME", "ADDRESS", "PHONE", "EMAIL", "MONEY")

private val ALLOWED_LABELS = mapOf(
    "DATE_TIME" to setOf("deadline", "document_date", "event_date", "date_of_birth", "unknown"),
    "ADDRESS" to setOf("issuer_address", "applicant_address", "other_address", "unknown"),
    "PHONE" to setOf("issuer_phone", "applicant_phone", "other_phone", "unknown"),
    "EMAIL" to setOf("issuer_email", "applicant_email", "other_email", "unknown"),
    "MONEY" to setOf("benefit_amount", "fee", "penalty", "other_amount", "unknown"),
)

class EntityAnnotator(private val llmHelper: LlmModelHelper) {

    /**
     * Assigns semantic context_label to DATE_TIME/ADDRESS/PHONE/EMAIL/MONEY entities using Gemma 4.
     * On timeout or parse failure, returns entities unchanged (contextLabel = null).
     * IBAN/PAYMENT_CARD/URL/TRACKING_NUMBER/FLIGHT_NUMBER/ISBN are skipped (statically labeled).
     */
    suspend fun annotate(
        model: Model,
        entities: List<DetectedEntity>,
        issuerName: String?,
        applicantName: String?,
        otherName: String?,
        sourceText: String,
    ): List<DetectedEntity> {
        val annotatable = entities.filter { it.type in ANNOTATABLE_TYPES }
        if (annotatable.isEmpty()) return entities

        val numberedEntities = annotatable.mapIndexed { i, e ->
            "${i + 1}. ${e.type}: ${e.rawText}"
        }.joinToString("\n")

        val userMessage = PromptBuilder.entityAnnotatorUserMessage(
            issuerName = issuerName,
            applicantName = applicantName,
            otherName = otherName,
            numberedEntities = numberedEntities,
            sourceText = sourceText,
        )

        val raw = try {
            llmHelper.resetConversation(
                model = model,
                systemInstruction = Contents.of(PromptBuilder.entityAnnotatorSystemPrompt()),
            )
            withTimeout(TIMEOUT_MS) {
                runInferenceSuspend(model, userMessage)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Annotation timed out, returning entities without labels")
            return entities
        } catch (e: Exception) {
            Log.w(TAG, "Annotation failed: ${e.message}, returning entities without labels")
            return entities
        }

        val labelMap = parseAnnotations(raw, annotatable)
        if (labelMap.isEmpty()) return entities

        // Apply labels back to the full entity list by rawText+type identity
        val annotatableWithLabels = annotatable.mapIndexed { i, entity ->
            val label = labelMap[i + 1]?.takeIf { it != "unknown" }
            entity.copy(contextLabel = label)
        }

        // Merge annotated back into full list preserving order
        val annotatableIterator = annotatableWithLabels.iterator()
        return entities.map { entity ->
            if (entity.type in ANNOTATABLE_TYPES) annotatableIterator.next() else entity
        }
    }

    private fun parseAnnotations(raw: String, annotatable: List<DetectedEntity>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx == -1) continue
            val indexStr = trimmed.substring(0, colonIdx).trim()
            val label = trimmed.substring(colonIdx + 1).trim()
            val index = indexStr.toIntOrNull() ?: continue
            if (index < 1 || index > annotatable.size) continue
            val entity = annotatable.getOrNull(index - 1) ?: continue
            val allowed = ALLOWED_LABELS[entity.type] ?: continue
            if (label in allowed) {
                result[index] = label
            }
        }
        return result
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
}
