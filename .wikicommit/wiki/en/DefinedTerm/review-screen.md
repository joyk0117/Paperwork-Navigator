---
title: "Review Screen"
type: "schema:DefinedTerm"
lang: en
description: "Paperwork Navigator's main screen (S-02), where document analysis results are reviewed in a two-column source/translation layout, offering quick actions, PII mask editing, and document chat"
termCode: "MF-04"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: []
translated_from: .wikicommit/wiki/ja/DefinedTerm/review-screen.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

The Review Screen (MF-04, screen ID S-02) is the main screen on which [[SoftwareApplication/paperwork-navigator]] displays the results of an analyzed document, laid out as a vertically scrolling stack of: translation bar, review content (source-only, or a two-column source/translation display), quick actions, and the [[DefinedTerm/document-chat]] section. Deadlines/warnings (red badge), required documents/actions (orange badge), documents/contacts (blue badge), and notes (gray) are shown with category-specific colored badges, and candidate PII is highlighted in yellow and can be deselected with a tap.

## Usage

### Screen navigation

Paperwork Navigator's screens consist of five: S-01 (input screen), S-02 (review screen), S-03 (inquiry context confirmation screen), S-04 (inquiry document creation screen), and S-M (model manager). A successful analysis on S-01 navigates to S-02; tapping "Create Inquiry Document" on S-02 goes to S-04 (basic information input) → "Confirm Context" advances to S-03 (copy/share). Navigating back from S-02 to S-01 clears `ReviewResult` / `MaskResult` / chat history entirely, returning to the `Idle` state; navigating back from S-04 to S-02 returns to the `Review` state while preserving `ReviewResult` / `MaskResult` / chat history.

### State management (DocumentReviewUiState)

`DocumentReviewViewModel` manages state as a `sealed class` that transitions `Idle` (waiting on S-01) → `Processing` (analyzing, holding step and progress) → `Review` (S-02, holding `reviewResult` / `piiSpans` / translation state / chat state) → `InquiryWizard` (S-04) → `InquiryPreview` (S-03). When `Review.translation` is `null`, the document is untranslated (source only, single-column); when non-`null`, it is translated (two-column, chat runs in the translated language). Inference for MF-02, MF-03, MF-06a, and MF-07 runs under mutual exclusion via a `Mutex` inside `DocumentReviewViewModel`, guaranteeing that multiple inferences are never issued concurrently. In the `Processing` state (while MF-02/03 are running), both the chat input and the inquiry button are hidden, so no conflict can occur.

### Quick actions

| Button | Intent | Display condition |
|--------|--------|----------|
| 📅 Add deadline to calendar | `CalendarContract.Events.INSERT` (all-day event, event name: `{doc_name} - Submission deadline`) | `deadline.date != null` |
| 🗺 View on map | `geo:0,0?q={query}` (prefers `location.address_ja`, falling back to `location.name_ja`) | shown if `location.name_ja` or `location.address_ja` is present |

If no relevant data is available, the button is hidden; if no app on the device can handle the Intent, `ActivityNotFoundException` is caught to prevent a crash. Intent construction is centralized in `util/DocumentIntentBuilder.kt`.

### Translation bar

State transitions from untranslated (language-selection dropdown, default English + "Translate" button) → translating (`isTranslating = true`, `⟳ Translating to {language}...`, dropdown and button disabled) → translated (`{language} ✓` + a re-translate dropdown). Before translation completes, chat is initialized in the source language (`reviewResult.sourceLanguage`); once translation completes, the chat session is re-initialized in the translated language (history cleared, snackbar notification shown). When the "Create Inquiry Document" button is tapped, if untranslated the flow proceeds to S-04 in the source language as-is; if translated, a two-choice dialog is shown asking whether to proceed with the wizard in the source language or the translated language.

### Source text section (SourceTextSection)

Above the translation bar is a source-text display section, shown only when `state.sourceText` is non-empty, with a toggle button to collapse/expand it. When expanded, the full source text is shown as monospace text wrapped in a `SelectionContainer`, letting the user select and copy the text.

### Shared badge components (ReviewBadgeItem / EventDatesSection)

Each information item in the review content is uniformly displayed via `ReviewBadgeItem` (a shared component consisting of an icon emoji, a label badge, body text, and an optional trailing button). Badge color is expressed via `ReviewBadgeColor` (4 values: RED / ORANGE / BLUE / GRAY): high-importance deadlines and warnings use RED, action items use ORANGE, required documents use BLUE, and everything else uses GRAY. The event schedule component (`EventDatesSection`) is implemented as a shared component called from both the source and translation columns, and is excluded from translation.

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
- [[DefinedTerm/multilingual-translation]]
- [[DefinedTerm/document-chat]]
- [[DefinedTerm/inquiry-context-generation]]
- [[DefinedTerm/quick-action]]
