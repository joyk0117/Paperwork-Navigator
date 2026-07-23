---
title: "PII Tier Classification"
type: "schema:DefinedTerm"
lang: en
description: "A data classification system, foundational to the privacy design, that sorts each field handled by Paperwork Navigator into 3 tiers based on whether it may be sent off-device"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [Privacy design]
translated_from: .wikicommit/wiki/ja/DefinedTerm/pii-tier-classification.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

PII Tier Classification is a system that sorts each field handled by [[SoftwareApplication/paperwork-navigator]] into Tiers 1-3 based on whether it may be sent off-device, statically derived from `DetectedEntity.computePiiTier()` and `context_label`. This classification is used as the basis for [[DefinedTerm/pii-masking]] (MF-05)'s `maskRecommended` assignment and for deciding the data-sending policy for each LLM call.

## Usage

| Tier | May be sent off-device? | Representative data |
|------|------------|----------|
| Tier 1 (never leaves the device) | No | `source.txt`, the `spanText` of `piiSpans`, all of `meta.json` |
| Tier 2 (may be sent off-device with user consent) | User sends manually via the share sheet | `MaskResult.maskedText`, `EscalationPackage` |
| Tier 3 (no PII) | Yes | `DocumentMeta` (`docName`, `importanceLevel`, `createdAt`) |

`piiSpans` is statically built from every `DetectedEntity` whose `piiTier` is 1 or 2; `applicant_name` / `other_name` / `issuer_name` (FieldExtractor output) are explicitly added as `PiiSpan` in `mergeEntities` (`issuer_name` is Tier 2).

This classification is based on the following design principles.

- **On-device inference**: all LLM inference runs entirely on-device; text containing PII is never sent to an external server
- **Least privilege**: only the minimum necessary permissions are requested (no external storage read/write permission is requested)
- **User-consented escalation**: sending masked text off-device is done by the user themselves via the Android share sheet; the app never sends it automatically
- **Explicit PII masking**: any text that could be output externally always uses masked data only
- **Transparency**: the categories of masked fields are made explicit in the UI

Tier 1/Tier 2 determinations are also defined per processing phase: passing data to on-device ML Kit / LLM processing such as `TextExtractor` → `EntityExtractor` or `EntityExtractor` → `FieldExtractor` is permitted to carry Tier 1 PII since it stays on-device, whereas share intents (external sends via the Android share sheet) must never be passed Tier 1 PII — only the output of `toPlainText()` (masked text equivalent to Tier 2) may be passed. Even when cloud-LLM escalation is implemented in Phase 2, only the `EscalationPackage` (masked) will be sent, and implementation and review will guarantee that Tier 1 data is never sent.

### Data-sending policy per LLM call

| Inference | Data passed | Data not passed |
|------|-----------|--------------|
| MF-02 FieldExtractor | `sourceText` (includes raw PII) | — (permitted since it's on-device) |
| EntityAnnotator | `issuer_name` / `applicant_name` / `other_name` (name hints) + the `rawText` of the 5 entity types | the `spanText` of `piiSpans` |
| MF-03 Translator | `ReviewResult`'s structured fields such as `summary` (pre-masked) | the `spanText` of `piiSpans` |
| MF-06a `InquiryContextBuilder.suggestPurposes` | `ReviewResult`'s structured fields + `sourceText` (includes raw PII) | — (permitted since it's on-device, same as MF-02) |
| MF-06 `EscalationPackageGenerator` | `maskResult.maskedText` (masked) | raw PII text, `source.txt` |
| MF-07 `DocumentChatSession` | `ReviewResult`'s structured fields (`doc_name`, `summary`, etc.) + `sourceText` (includes raw PII) | the `spanText` of `piiSpans` (`sourceText` itself is permitted since it's on-device) |

`DocumentRepository.save()` saves under `filesDir`, which other apps cannot access. Share intents (external sends via the Android share sheet) must never be passed Tier 1 data — only the output of `toPlainText()` may be passed.

### computePiiTier() and per-field tiers

`DetectedEntity`'s `piiTier` is derived by the following logic.

```kotlin
fun DetectedEntity.computePiiTier(): Int = when (type) {
    "IBAN", "PAYMENT_CARD" -> 1
    "ADDRESS", "PHONE", "EMAIL" ->
        if (contextLabel?.startsWith("applicant") == true) 1 else 2
    "TRACKING_NUMBER" -> 2
    "DATE_TIME" -> if (contextLabel == "date_of_birth") 1 else 3
    "MONEY" -> 2
    else -> 3  // URL, ISBN, FLIGHT_NUMBER
}
```

`applicant_name` / `other_name` / `issuer_name` (FieldExtractor output), which are not treated as `DetectedEntity`, do not go through `computePiiTier()` — `mergeEntities` explicitly assigns their tier (`applicant_name` is Tier 1; `other_name` / `issuer_name` are Tier 2). Representative `context_label` values are as follows.

| Tier | Definition | Representative `context_label` values |
|------|------|----------------------|
| Tier 1 (highly sensitive) | Information that directly identifies an individual. Never leaves the device | `applicant_address` / `applicant_phone` / `applicant_email` / `date_of_birth` / `iban` / `payment_card` |
| Tier 2 (moderately sensitive) | Information that could be linked to an individual depending on context. Subject to masking | `issuer_address` / `issuer_phone` / `issuer_email` / `other_*` / `benefit_amount` / `fee` / `penalty` / `tracking_number` |
| Tier 3 (low sensitivity) | Organizational information, public information, identifiers | `deadline` / `document_date` / `event_date` / `url` / `isbn` / `flight_number` |

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/pii-masking]]
