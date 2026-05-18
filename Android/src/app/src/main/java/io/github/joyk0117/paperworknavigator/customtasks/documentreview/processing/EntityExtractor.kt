package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.FlightNumberEntity
import com.google.mlkit.nl.entityextraction.IbanEntity
import com.google.mlkit.nl.entityextraction.MoneyEntity
import com.google.mlkit.nl.entityextraction.PaymentCardEntity
import com.google.mlkit.nl.entityextraction.TrackingNumberEntity
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DeadlineInfo
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DetectedEntity
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.EntityMetadata
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.EventDate
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.LocationEntry
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.computePiiTier
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EntityExtractor"
private const val TIMEOUT_SECONDS = 30L

object EntityExtractor {

    /**
     * Extracts all 11 ML Kit entity types from text, collecting metadata where available.
     * Statically sets contextLabel for ML Kit-only types (IBAN/PAYMENT_CARD/URL/TRACKING_NUMBER/
     * FLIGHT_NUMBER/ISBN). DATE_TIME/ADDRESS/PHONE/EMAIL/MONEY labels are assigned later by
     * EntityAnnotator (Gemma 4).
     *
     * Returns an empty list on model download failure or extraction error (silent skip).
     */
    suspend fun extract(text: String, sourceLanguage: String = "en"): List<DetectedEntity> {
        val modelCode = languageToModelCode(sourceLanguage)
        return withContext(Dispatchers.IO) {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(modelCode).build()
            )
            try {
                try {
                    Tasks.await(extractor.downloadModelIfNeeded(), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (e: ExecutionException) {
                    Log.w(TAG, "Model download failed, skipping entity extraction: ${e.cause?.message}")
                    return@withContext emptyList()
                } catch (e: TimeoutException) {
                    Log.w(TAG, "Model download timed out, skipping entity extraction")
                    return@withContext emptyList()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@withContext emptyList()
                }

                val params = EntityExtractionParams.Builder(text).build()
                val annotations: List<EntityAnnotation> = try {
                    Tasks.await(extractor.annotate(params), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (e: ExecutionException) {
                    Log.w(TAG, "Annotation failed: ${e.cause?.message}")
                    return@withContext emptyList()
                } catch (e: TimeoutException) {
                    Log.w(TAG, "Annotation timed out")
                    return@withContext emptyList()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@withContext emptyList()
                }

                val detectedEntities = mutableListOf<DetectedEntity>()
                for (annotation in annotations) {
                    val rawText = annotation.annotatedText
                    for (entity in annotation.entities) {
                        val typeLabel = entityTypeLabel(entity.type)
                        val staticLabel = staticContextLabel(entity.type)
                        val metadata = buildMetadata(entity)
                        Log.d(TAG, "ML Kit detected: type=$typeLabel rawText=\"$rawText\"")
                        detectedEntities.add(
                            DetectedEntity(
                                type = typeLabel,
                                rawText = rawText,
                                contextLabel = staticLabel,
                                metadata = metadata,
                            )
                        )
                    }
                }

                Log.d(TAG, "Extracted ${detectedEntities.size} entities from ${text.length} chars")
                detectedEntities
            } finally {
                extractor.close()
            }
        }
    }

    /**
     * Merges EntityAnnotator-labeled entities into the ReviewResult from FieldExtractor.
     * Derives deadline, docDate, issuerAddress, locations, and eventDates from contextLabels.
     * Builds PiiSpans from Tier-1/2 entities plus applicantName/otherName from LLM output.
     */
    fun mergeEntities(
        gemmaResult: ReviewResult,
        annotatedEntities: List<DetectedEntity>,
    ): Pair<ReviewResult, List<PiiSpan>> {
        fun firstOf(label: String) = annotatedEntities.firstOrNull { it.contextLabel == label }
        fun allOf(label: String) = annotatedEntities.filter { it.contextLabel == label }

        // Derive deadline from entity
        val deadlineEntity = firstOf("deadline")
        val deadline = DeadlineInfo(
            date = timestampToIsoDate(deadlineEntity?.metadata?.timestampMillis),
            noteJa = deadlineEntity?.rawText,
        )

        // Derive docDate from entity
        val docDate = firstOf("document_date")?.rawText

        // Derive issuerAddress from entity
        val issuerAddress = firstOf("issuer_address")?.rawText

        // Derive locations: issuer_address as primary, then other_address entries
        val locations = buildList {
            firstOf("issuer_address")?.let { add(LocationEntry(addressJa = it.rawText)) }
            allOf("other_address").forEach { add(LocationEntry(addressJa = it.rawText)) }
        }

        // Derive eventDates from event_date entities
        val eventDates = allOf("event_date").map { entity ->
            EventDate(
                date = timestampToIsoDate(entity.metadata?.timestampMillis),
                descriptionJa = entity.rawText,
            )
        }

        // Compute piiTier for all annotated entities and assign back
        val entitiesWithTier = annotatedEntities.map { entity ->
            entity.copy(piiTier = entity.computePiiTier())
        }

        val mergedResult = gemmaResult.copy(
            docDate = docDate,
            deadline = deadline,
            issuerAddress = issuerAddress,
            locations = locations,
            eventDates = eventDates,
            detectedEntities = entitiesWithTier,
        )

        // Build PiiSpans from Tier-1/2 entities
        val seen = mutableSetOf<String>()
        val piiSpans = mutableListOf<PiiSpan>()
        var piiIndex = 0

        fun addPiiSpan(spanText: String, category: String, sourceField: String? = null, maskRecommended: Boolean = true) {
            if (spanText.isBlank() || !seen.add(spanText)) return
            piiIndex++
            piiSpans.add(
                PiiSpan(
                    id = "pii_${piiIndex.toString().padStart(2, '0')}",
                    spanText = spanText,
                    category = category,
                    sourceField = sourceField,
                    maskRecommended = maskRecommended,
                )
            )
        }

        // applicantName from LLM output → Tier 1 → maskRecommended = true
        gemmaResult.applicantName?.let { addPiiSpan(it, "name", "applicant_name", maskRecommended = true) }

        // otherName from LLM output → Tier 2 → maskRecommended = false
        gemmaResult.otherName?.let { addPiiSpan(it, "name", "other_name", maskRecommended = false) }

        // issuerName from LLM output → Tier 2 → maskRecommended = false
        gemmaResult.issuerName?.let { addPiiSpan(it, "name", "issuer_name", maskRecommended = false) }

        // ML Kit entities with piiTier 1 or 2: Tier 1 → maskRecommended = true, Tier 2 → false
        for (entity in entitiesWithTier) {
            val tier = entity.piiTier ?: continue
            if (tier > 2) continue
            val category = when (entity.type) {
                "ADDRESS" -> "address"
                "PHONE" -> "phone"
                "EMAIL" -> "other"
                "DATE_TIME" -> if (entity.contextLabel == "date_of_birth") "dob" else continue
                "IBAN", "PAYMENT_CARD" -> "account"
                "TRACKING_NUMBER" -> "other"
                "MONEY" -> "other"
                else -> continue
            }
            addPiiSpan(entity.rawText, category, entity.contextLabel, maskRecommended = tier == 1)
        }

        return mergedResult to piiSpans
    }

    private fun staticContextLabel(type: Int): String? = when (type) {
        Entity.TYPE_URL -> "url"
        Entity.TYPE_IBAN -> "iban"
        Entity.TYPE_PAYMENT_CARD -> "payment_card"
        Entity.TYPE_TRACKING_NUMBER -> "tracking_number"
        Entity.TYPE_FLIGHT_NUMBER -> "flight_number"
        Entity.TYPE_ISBN -> "isbn"
        else -> null
    }

    private fun buildMetadata(entity: Entity): EntityMetadata? = when (entity.type) {
        Entity.TYPE_DATE_TIME -> {
            val dt = entity as? DateTimeEntity
            EntityMetadata(
                timestampMillis = dt?.getTimestampMillis(),
                granularity = dt?.getDateTimeGranularity()?.toString(),
            )
        }
        Entity.TYPE_MONEY -> {
            val money = entity as? MoneyEntity
            EntityMetadata(
                currency = money?.getUnnormalizedCurrency(),
                integerPart = money?.getIntegerPart()?.toLong(),
            )
        }
        Entity.TYPE_IBAN -> {
            val iban = entity as? IbanEntity
            EntityMetadata(ibanCountryCode = iban?.getIbanCountryCode())
        }
        Entity.TYPE_PAYMENT_CARD -> {
            val card = entity as? PaymentCardEntity
            EntityMetadata(cardNetwork = card?.getPaymentCardNetwork()?.toString())
        }
        Entity.TYPE_TRACKING_NUMBER -> {
            val tracking = entity as? TrackingNumberEntity
            EntityMetadata(carrier = tracking?.getParcelCarrier()?.toString())
        }
        Entity.TYPE_FLIGHT_NUMBER -> {
            val flight = entity as? FlightNumberEntity
            EntityMetadata(airlineCode = flight?.getAirlineCode())
        }
        else -> null
    }

    // Maps BCP-47 codes to ML Kit EntityExtractorOptions constants.
    // Supported: 12 languages. ru/ar/th are not supported by ML Kit Entity Extraction.
    private fun languageToModelCode(languageCode: String): String = when (languageCode) {
        "ja" -> EntityExtractorOptions.JAPANESE
        "zh" -> EntityExtractorOptions.CHINESE
        "ko" -> EntityExtractorOptions.KOREAN
        "en" -> EntityExtractorOptions.ENGLISH
        "es" -> EntityExtractorOptions.SPANISH
        "fr" -> EntityExtractorOptions.FRENCH
        "de" -> EntityExtractorOptions.GERMAN
        "it" -> EntityExtractorOptions.ITALIAN
        "pt" -> EntityExtractorOptions.PORTUGUESE
        "nl" -> EntityExtractorOptions.DUTCH
        "pl" -> EntityExtractorOptions.POLISH
        "tr" -> EntityExtractorOptions.TURKISH
        else -> {
            val reason = if (languageCode == "und") "language undetermined" else "unsupported language '$languageCode'"
            Log.d(TAG, "EntityExtractor: $reason, falling back to ENGLISH")
            EntityExtractorOptions.ENGLISH
        }
    }

    private fun entityTypeLabel(type: Int): String = when (type) {
        Entity.TYPE_PHONE -> "PHONE"
        Entity.TYPE_EMAIL -> "EMAIL"
        Entity.TYPE_ADDRESS -> "ADDRESS"
        Entity.TYPE_DATE_TIME -> "DATE_TIME"
        Entity.TYPE_URL -> "URL"
        Entity.TYPE_MONEY -> "MONEY"
        Entity.TYPE_IBAN -> "IBAN"
        Entity.TYPE_PAYMENT_CARD -> "PAYMENT_CARD"
        Entity.TYPE_FLIGHT_NUMBER -> "FLIGHT_NUMBER"
        Entity.TYPE_ISBN -> "ISBN"
        Entity.TYPE_TRACKING_NUMBER -> "TRACKING_NUMBER"
        else -> "UNKNOWN"
    }

    private fun timestampToIsoDate(timestampMillis: Long?): String? {
        timestampMillis ?: return null
        return try {
            Instant.ofEpochMilli(timestampMillis)
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
                .toString()
        } catch (_: Exception) {
            null
        }
    }
}
