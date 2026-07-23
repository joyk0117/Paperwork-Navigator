---
title: "Inquiry Context Generation"
type: "schema:DefinedTerm"
lang: en
description: "A feature that assembles PII-masked text through a wizard flow to generate inquiry context for consulting an external AI or a professional"
termCode: "MF-06"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: []
translated_from: .wikicommit/wiki/ja/DefinedTerm/inquiry-context-generation.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Inquiry Context Generation (MF-06) is a feature that assembles context through a wizard flow from [[SoftwareApplication/paperwork-navigator]]'s review screen, generating text that has been through [[DefinedTerm/pii-masking]]. The generated text helps the user consult an external AI or a professional. `InquiryContextBuilder` handles this processing, consisting of two functions: `suggestPurposes()` (Gemma 4 inference #3) and `buildContext()` (no LLM call).

## Usage

Tapping the "Create Inquiry Document" button on the review screen (MF-04) navigates to the wizard (S-04), where `InquiryContextBuilder.suggestPurposes()` proposes candidate inquiry purposes from the document's summary and action_items (a spinner is shown while generating; on failure it falls back to an empty list and shows free-text input only). The recipient is auto-filled from `ReviewResult.issuer` or `ReviewResult.contact` and can be overwritten manually. Personal information to mask can be selected span by span via checkboxes, and Tier 1 spans (`maskRecommended = true`) are checked by default.

`buildContext()` assembles an `InquiryContext` from `ReviewResult` and the wizard input, and `toContextText()` generates the final structured text. In addition to the consultation message, inquiry purpose, and recipient, the output includes any unmasked PII spans (`allPiiSpans` minus `maskedPiiSpans`) as "sender information" (the section is omitted if empty). The document summary, deadline, required actions, required items, and notes use the translated version only when `reviewResult.translation` exists and the translation language matches the context language; otherwise the source-language fields are used. Headings and labels are localized according to `InquiryContext.language`, falling back to Japanese for unsupported languages.

The generated context is displayed immediately on the inquiry context confirmation screen (S-03) (no LLM call needed), and can be copied or shared via the Android share sheet to send to an external AI or a professional.

### InquiryContext data model

`InquiryContext` consists of `language` / `recipient` (`InquiryRecipient`: `organizationName` / `contactName` / `email` / `phone`) / `purpose` / `documentSummary` / `maskedPiiSpans` (spans the user chose to mask) / `allPiiSpans` (all spans, retained for computing unmasked spans) / `reviewResult` / `maskedSourceText`. `toContextText()` outputs structured text in the order: consultation message (localized per `InquiryContext.language`), inquiry purpose, recipient, sender information (unmasked PII, omitted if empty), document summary, deadline (omitted if `null`), required actions, required items (omitted if empty), notes (omitted if empty), and masked source text (omitted if `maskedSourceText` is empty).

### Prompt design for suggestPurposes()

`suggestPurposes()` (Gemma 4 inference) is given the document's summary, required actions, and source text (up to 16,000 characters), plus any prior [[DefinedTerm/document-chat]] history as material for gauging the user's interests, and generates 3-5 candidate purposes of roughly 5-15 words each as a JSON array. If the chat history is empty (no user utterances), that item is omitted from the prompt entirely. On JSON parse failure or timeout (15 seconds), it falls back immediately to an empty list without retrying.

### The evolution of MF-06 and escalation package generation

MF-06 originally consisted of three stages: purpose candidate suggestion (MF-06a), missing-information question list generation (MF-06b), and inquiry document generation (MF-06c). MF-06b was removed after on-device validation found it to have low usefulness, and MF-06c was deferred to Phase 2 due to quality concerns with on-device-generated documents (writing style, politeness register, language mismatch). Per the prompt spec, MF-06 today consists of three elements: purpose candidate suggestion (MF-06a), escalation package generation (MF-06), and context text assembly (no LLM required). Escalation package generation is a prompt intended to run when the "Create Handoff File" button is pressed; from the masked text it generates JSON containing `consultation_summary` (a summary of the consultation content), `timeline` (a list of dates and events), and `ai_hypotheses` (points that cannot be determined from the document alone, or interpretive hypotheses).

### Escalation package output screen (S03OutputPreviewContent)

Escalation package generation, described as "intended" in the prompt spec, is implemented in `DocumentReviewScreen.kt` as a UI state transition `GeneratingEscalation` → `OutputPreview`. When the `onGenerateEscalation(userNotes: String)` callback (which optionally takes user notes) is invoked, generation begins; once complete, `S03OutputPreviewContent` displays `EscalationPackage.toPlainText()` in a scrollable monospace font. The top of the screen shows a banner that includes or omits masked field names depending on whether `pkg.maskedFields` is non-empty, and the "Copy"/"Share" buttons at the bottom reuse the same `copyToClipboard()` / `shareText()` helpers as the inquiry context confirmation screen (`S03InquiryPreviewContent`). While in `GeneratingEscalation`, the screen body continues to reuse `S02ReviewContent` with state duplicated from the `Review` state (`reviewResult` / `piiSpans` / `sourceText` / `chatMessages`, etc.), so the review screen keeps being displayed as-is during generation, with no navigation to another screen or remount.

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/pii-masking]]
- [[DefinedTerm/document-chat]]
