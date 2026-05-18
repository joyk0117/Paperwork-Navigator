package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.MaskResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.maskToken

object PiiMasker {

    internal const val MASK_TOKEN = "[■■■]"

    private val HONORIFICS = listOf("Mr.", "Ms.", "Mrs.", "Dr.", "Sir")

    fun mask(text: String, spans: List<PiiSpan>): MaskResult = applyMask(text, spans)

    fun remask(text: String, spans: List<PiiSpan>): MaskResult = applyMask(text, spans)

    private fun applyMask(text: String, spans: List<PiiSpan>): MaskResult {
        val toMask = mutableListOf<PiiSpan>()
        val skipped = mutableListOf<PiiSpan>()

        for (span in spans) {
            val shouldMask = when {
                span.userOverride == true -> true
                span.userOverride == false -> false
                span.maskRecommended -> true
                else -> false
            }
            if (shouldMask) toMask.add(span) else skipped.add(span)
        }

        var result = text
        val applied = mutableListOf<PiiSpan>()
        val unmatched = mutableListOf<PiiSpan>()

        for (span in toMask) {
            val regex = buildMatchRegex(span.spanText)
            if (regex != null && regex.containsMatchIn(result)) {
                result = regex.replace(result, Regex.escapeReplacement(span.maskToken()))
                applied.add(span)
            } else {
                unmatched.add(span)
            }

            if (span.sourceField == "applicant_name") {
                val token = Regex.escapeReplacement(span.maskToken())
                for (variant in nameVariants(span.spanText)) {
                    val variantRegex = buildMatchRegex(variant) ?: continue
                    if (variantRegex.containsMatchIn(result)) {
                        result = variantRegex.replace(result, token)
                    }
                }
            }
        }

        return MaskResult(
            maskedText = result,
            appliedSpans = applied,
            skippedSpans = skipped,
            unmatchedSpans = unmatched,
        )
    }

    // Generates partial-name variants for applicant_name masking (姓のみ・敬称付き姓など).
    // Only first/last tokens with length >= 3 are used to avoid false positives.
    // Tokens without whitespace separators (e.g. "山田太郎") produce no variants.
    internal fun nameVariants(fullName: String): List<String> {
        val tokens = fullName.trim().split(Regex("\\s+"))
        if (tokens.size < 2) return emptyList()
        val candidates = linkedSetOf(tokens.first(), tokens.last()).filter { it.length >= 3 }
        return buildList {
            addAll(candidates)
            candidates.forEach { name -> HONORIFICS.forEach { add("$it $name") } }
        }
    }

    // Builds a regex that matches the span text with flexible whitespace in both directions:
    // - span has whitespace but source text does not (e.g. "山田 太郎" → "山田太郎")
    // - source text has whitespace but span does not (e.g. "山田太郎" → "山田 太郎")
    // Strips all whitespace from spanText, then inserts [\\s　]* between every character.
    // The optional whitespace also absorbs formatting differences in numeric strings
    // (e.g. account "1234567" matches "123 4567"), which is intentional for PDF text extraction.
    // Returns null only for empty spanText.
    internal fun buildMatchRegex(spanText: String): Regex? {
        if (spanText.isEmpty()) return null
        val stripped = spanText.trim().replace(Regex("[\\s　]+"), "")
        val pattern = stripped
            .map { Regex.escape(it.toString()) }
            .joinToString("[\\s　]*")
        return if (pattern.isEmpty()) null else Regex(pattern)
    }
}
