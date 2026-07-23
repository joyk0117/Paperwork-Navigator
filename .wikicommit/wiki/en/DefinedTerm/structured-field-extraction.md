---
title: "Structured Field Extraction"
type: "schema:DefinedTerm"
lang: en
description: "Processing that derives ReviewResult and PiiSpan from document text using Gemma 4 and ML Kit Entity Extraction (FieldExtractor / EntityAnnotator / mergeEntities)"
termCode: "MF-02"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [On-device processing]
translated_from: .wikicommit/wiki/ja/DefinedTerm/structured-field-extraction.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Structured Field Extraction (MF-02) is processing by which [[SoftwareApplication/paperwork-navigator]] derives `ReviewResult` (document title, deadline, required actions, warnings, etc.) and PII spans from document text, consisting of three stages: `FieldExtractor` (Gemma 4 inference #1), `EntityAnnotator` (Gemma 4 inference #2), and `EntityExtractor.mergeEntities` (no LLM needed).

## Usage

**FieldExtractor**: passes the document text (up to 16,000 characters) to Gemma 4 and has it output 9 fields in line format (key-value lines). The response is received as a stream and parsed as line format once complete. On a parse error, it retries up to 2 times (appending error information to the prompt); on failure it throws `FieldExtractionError.JsonParseError` or similar. Inference has a 150-second timeout; on timeout, "Analysis failed. Please retry" is shown.

**EntityAnnotator**: of the 11 entity types extracted by ML Kit Entity Extraction, annotates 5 of them — `DATE_TIME` / `ADDRESS` / `PHONE` / `EMAIL` / `MONEY` — with a `context_label` (Gemma 4 inference #2). It is passed `issuer_name` / `applicant_name` / `other_name` (name hints from FieldExtractor's output) as contextual clues.

**mergeEntities**: derives `deadline` / `docDate` / `issuerAddress` / `locations` / `eventDates` from `context_label`, and collects every entity with `piiTier ∈ {1, 2}` into `piiSpans` (see [[DefinedTerm/pii-tier-classification]]). The result is saved as `meta.json` (Tier 1).

The reason a line format was adopted is that on-device testing found that the LiteRT-LM 0.11.0 Kotlin API lacks Constrained Decoding, and that complex nested JSON caused key-as-value collapse; this is a design decision shared by MF-02 and MF-03. The System Prompt is written in English, and including few-shot examples helps stabilize accuracy.

### Design principles

MF-02 extraction is split into two layers: ML Kit Entity Extraction (value detection — fast and deterministic) and Gemma 4 (semantic labeling and structuring — flexible and context-aware). ML Kit can reliably detect that a string is a phone number, but cannot determine whether it belongs to the issuer or the applicant, whereas an LLM is good at inferring meaning from context but poor at the kind of regex-like deterministic value extraction ML Kit handles — so the design lets each layer focus on what it does best. FieldExtractor runs before EntityAnnotator so that `issuer_name` / `applicant_name` / `other_name` are settled first, giving EntityAnnotator clues to use when attributing ADDRESS / PHONE / EMAIL (to issuer, applicant, or other).

The meaning of every ML Kit-derived entity is settled via `context_label`, from which the PII tier (see [[DefinedTerm/pii-tier-classification]]), the [[DefinedTerm/quick-action]] type, and whether it is included when building `piiSpans` (see [[DefinedTerm/pii-masking]]) are all statically derived. For the 5 types DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY, EntityAnnotator assigns `context_label` from context; for the 6 types IBAN / PAYMENT_CARD / URL / TRACKING_NUMBER / FLIGHT_NUMBER / ISBN, EntityExtractor statically sets the type name as-is. LLM-only items such as `importance` / `warning` / `action_items` are direct fields of `ReviewResult` rather than `DetectedEntity`, so they have no `context_label`. When EntityAnnotator returns `unknown`, or on parse failure or timeout (no retry), all entities fall back to `contextLabel = null`.

### Data model

`DetectedEntity` (`type` / `rawText` / `contextLabel` / `piiTier` / `metadata`) is the basic unit for an ML Kit-derived entity, with `piiTier` derived via `computePiiTier()`. `EntityMetadata` holds per-type auxiliary information for DATE_TIME (`timestampMillis` / `granularity`), MONEY (`currency` / `integerPart`), IBAN (`ibanCountryCode`), PAYMENT_CARD (`cardNetwork`), TRACKING_NUMBER (`carrier`), and FLIGHT_NUMBER (`airlineCode`).

### Extraction item catalog and mergeEntities

Extraction targets consist of 32 fields total, in this order (which also matches display order on the review screen, S-02): urgent information (6 items) — `importance` / `deadline` / `event_date` / `warning` / `action_items` / `required_items`; basic information (3 items) — `doc_name` / `document_date` / `summary`; issuer contact (4 items) — `issuer_name` / `issuer_address` / `issuer_phone` / `issuer_email`; applicant personal information (5 items) — `applicant_name` / `applicant_address` / `applicant_phone` / `applicant_email` / `date_of_birth`; financial information (6 items) — `benefit_amount` / `fee` / `penalty` / `other_amount` / `iban` / `payment_card`; other people/places (4 items) — `other_name` / `other_address` / `other_phone` / `other_email`; digital/identifiers (4 items) — `url` / `tracking_number` / `flight_number` / `isbn`. `mergeEntities` aggregates `DetectedEntity` per `context_label`, using either `firstOrNull` (single-value items such as `deadline` / `issuer_address` / `applicant_phone`) or a full list (items that can take multiple values, such as `other_address` / `event_date` / `url`).

### ReviewResult data model and errors

`ReviewResult` consists of the fields `docName` / `docDate` / `issuerName` / `applicantName` / `otherName` / `importance` (high/medium/low) / `summaryJa` / `deadline` (`DeadlineInfo`: `date` + `noteJa`) / `issuerAddress` / `locations` (a list of `LocationEntry`) / `actionItems` (`ActionItem`: id, `descriptionJa`, `priority`) / `requiredItems` (`RequiredItem`: id, `nameJa`, `noteJa`) / `warning` (`Warning`: id, `descriptionJa`, `severity` — only the single most important item) / `eventDates` (`EventDate`) / `translation` / `sourceLanguage` / `detectedEntities`. The `_ja` suffix on field names does not mean Japanese-only — it means the field holds text in the document's source language (`sourceLanguage`). `FieldExtractor.extract()` throws `FieldExtractionError.JsonParseError` on a JSON parse error, `ModelNotInitialized` when the model is not yet initialized, and `InferenceError` for other inference errors.

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/text-extraction]]
- [[DefinedTerm/pii-masking]]
- [[DefinedTerm/pii-tier-classification]]
- [[DefinedTerm/quick-action]]
- [[DefinedTerm/review-screen]]
