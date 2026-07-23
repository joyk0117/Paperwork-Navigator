---
title: "PII Masking"
type: "schema:DefinedTerm"
lang: en
description: "Rule-based masking of already-extracted entities to keep sensitive information such as names, addresses, and ID numbers from ever leaving the device — a non-LLM process"
termCode: "MF-05"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [On-device processing]
translated_from: .wikicommit/wiki/ja/DefinedTerm/pii-masking.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

PII Masking (MF-05) is a rule-based process in [[SoftwareApplication/paperwork-navigator]] that masks already entity-extracted information. It uses no LLM, and the user can toggle masking on/off per span.

## Usage

[[SoftwareApplication/paperwork-navigator]]'s privacy design classifies information into 3 tiers based on [[DefinedTerm/pii-tier-classification]]. Tier 1 (name, address, date of birth, national ID number, account number, etc. — `source.txt`, the `spanText` of `piiSpans`, and all of `meta.json`) never leaves the device; Tier 2 (issuer contact info, deadlines, amounts, etc. — `MaskResult.maskedText`, `EscalationPackage`) can only be sent externally in masked form and with user consent; Tier 3 (`DocumentMeta`'s `docName` / `importanceLevel` / `createdAt`, etc.) contains no PII. PII masking, based on this tier classification, is applied to entities obtained from ML Kit entity extraction (MF-02) and Gemma 4 entity annotation, at the stage before they are displayed on the review screen (MF-04); the categories of masked fields are made explicit in the UI. The masked text is used as input to [[DefinedTerm/inquiry-context-generation]] (MF-06).

Under the `maskRecommended` assignment rules, Tier 1 PII (`applicant_name`, `applicant_address`, `applicant_phone`, `applicant_email`, `date_of_birth`, `iban`, `payment_card`) gets `maskRecommended = true`, while Tier 2 PII (`issuer_name`, `issuer_address`, `issuer_phone`, `issuer_email`, `other_*`, MONEY entities, etc.) gets `maskRecommended = false`. In the initial state of the inquiry document creation screen (S-04), only spans with `maskRecommended = true` are checked (masked) by default.

### Building piiSpans (mergeEntities)

`piiSpans` is built by MF-02's `mergeEntities` processing. It collects all `DetectedEntity` items whose `piiTier` is 1 or 2, deduplicating by `rawText` before adding them. ML Kit entities have `sourceField = entity.contextLabel` set (e.g., `"issuer_address"`, `"applicant_phone"`); when `contextLabel` is `null` (on EntityAnnotator failure), `sourceField` is `null`. MONEY entities (`benefit_amount` / `fee` / `penalty` / `other_amount`, Tier 2) are also recorded in `piiSpans` with `category="other"`. Because `applicant_name` / `other_name` / `issuer_name` (all MF-02 output from FieldExtractor) are not `DetectedEntity` instances, `mergeEntities` explicitly constructs a `PiiSpan(spanText=rawText, category="name", sourceField=<field name>, maskRecommended=<boolean>)` for each. `applicant_name` is built as Tier 1 / `maskRecommended=true`; `other_name` as Tier 2 / `maskRecommended=false`; and `issuer_name` also as Tier 2 / `maskRecommended=false` (`issuer_name` is promoted to Tier 2 at this construction step).

### Name-variant masking

For `applicant_name` spans, in addition to an exact full-name match, partial-name variants are automatically generated and masked (`PiiMasker.nameVariants()`). For example, from "Carlos Rivera," variants such as "Carlos" / "Rivera" alone or honorific-prefixed forms like "Mr. Carlos" / "Mr. Rivera" are generated, preventing PII leakage in honorific-prefixed salutations such as "Dear Mr. Rivera,". Tokens under 3 characters (e.g., "Li") and full names without space separators (e.g., "山田太郎") are excluded from variant generation. Variant mask tokens use the same token as the full name (e.g., `[Applicant name]`); `issuer_name` / `other_name` are excluded from variant masking.

### UI display and removal

Masked spans are displayed as labeled tokens such as `[Applicant name]` / `[Issuer address]`, falling back to `[■■■]` when `sourceField` is unknown. At the top of the inquiry context confirmation screen (S-03), "⚠️ Personal information has been masked / masked: {categories}" is displayed. If there are any unmatched spans (`unmatchedSpans`), "Some items could not be masked" is shown as a notice. Users can set `userOverride = false` (exclude from masking) per span in the PII edit panel; for spans excluded from masking, the original text remains in `maskedText` and in `escalation.json`'s `masked_source_text` as information the user deliberately chose to share.

Deletion is limited to `DocumentRepository.delete(docId)`, which deletes the entire `{filesDir}/documents/{docId}/` directory, or to the OS deleting all of `filesDir` when the app is uninstalled. Since document management UI (list/delete) is not implemented in the MVP, uninstalling is the only way for a user to delete data through app operations; a deletion feature is planned to be added alongside the document management screen in Phase 2.

### Local storage

Document data is stored under `{filesDir}/documents/doc_{yyyyMMdd_HHmmss}_{shortUuid}/` as `meta.json` (`ReviewResult` + `PiiSpan` info, including PII), `source.txt` (extracted source text, including PII), `escalation.json` (masked `EscalationPackage`, only if the user ran MF-06), and `inquiry.json` (`InquiryContext`, which may include PII the user explicitly included in S-04); no external storage is used. `source.txt` and `meta.json` are never sent off-device. `DocumentRepository` provides `save()` / `saveEscalation()` / `saveInquiry()` / `list()` / `load()` / `delete()`; the `DocumentMeta` returned by `list()` (`docId` / `docName` / `importanceLevel` / `createdAt` / `hasEscalation` / `hasInquiry`) is listing information equivalent to Tier 3 and contains no PII. The MVP performs no encryption; use of the Keystore is under consideration for Phase 2.

`PiiSpan` has the fields `id` / `spanText` / `category` (`"name"` / `"address"` / `"phone"` / `"account"` / `"dob"` / `"id_number"` / `"other"`) / `sourceField` / `maskRecommended` / `userOverride`. Category labels are localized via the `PiiSpan.categoryLabel(lang)` extension function, and mask tokens are generated by the `PiiSpan.maskToken()` extension function, which builds tokens such as `[Applicant name]` from `sourceField` (falling back to `[■■■]` when `sourceField` is `null`).

### Implementation (PiiMasker)

`PiiMasker.mask(text, spans)` uses no LLM: it strips all whitespace from `spanText`, escapes each character with `Regex.escape()`, and joins them with `[\s　]*` to build a regular expression that it searches for in the text (this absorbs whitespace differences and full-width spaces introduced during PDF text extraction). A `spanText` that becomes empty after whitespace stripping (a whitespace-only span) cannot produce a regex and is recorded in `unmatchedSpans`. Every match is replaced with the token from `span.maskToken()`; spans with no match are excluded from `appliedSpans` and recorded in `unmatchedSpans`, notifying the user in the UI.

Masking is applied in the following priority order.

1. `userOverride == true` → force mask
2. `userOverride == false` → force exclude
3. `userOverride == null && maskRecommended == true` → mask (default)
4. `userOverride == null && maskRecommended == false` → exclude

When the user changes a span's `userOverride` in the PII edit panel, `PiiMasker.remask(text, spans)` re-applies masking.

### Per-row UI in the PII edit panel (PiiEditRow)

Each row in the PII edit panel consists of a checkbox, the span text (shown as `[■■■]` while masked), and a category label. Spans in `unmatchedSpans` have their checkbox disabled and are shown in red with a note roughly equivalent to "could not be masked." Spans currently masked show a note reading "masked" in the primary color.

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/inquiry-context-generation]]
- [[DefinedTerm/pii-tier-classification]]
- [[DefinedTerm/structured-field-extraction]]
