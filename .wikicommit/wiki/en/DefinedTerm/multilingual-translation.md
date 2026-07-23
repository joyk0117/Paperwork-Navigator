---
title: "Multilingual Translation"
type: "schema:DefinedTerm"
lang: en
description: "Processing that translates ReviewResult's source-language fields into the user-selected language via Gemma 4 (Translator)"
termCode: "MF-03"
inDefinedTermSet: "[[SoftwareApplication/paperwork-navigator]]"
tags: [On-device processing]
translated_from: .wikicommit/wiki/ja/DefinedTerm/multilingual-translation.md
source_commit: 01e633ce43ed0a07659b72696ba35016c4520336
translated_at: "2026-07-23"
review_status: pending
---

Multilingual Translation (MF-03) is processing in which the `Translator` class, triggered by the user tapping "Translate," translates `ReviewResult`'s source-language fields and returns a `ReviewResult` with its `translation` field populated. The fields translated are `summary_ja`, `action_items[].description_ja`, `required_items[].name_ja` / `note_ja`, `warning.description_ja`, and `deadline.note_ja`; `PiiSpan` (since span positions would shift) and `id`-type fields are not translated.

## Usage

15 target languages are supported (`ja` / `en` / `zh` / `ko` / `es` / `fr` / `de` / `it` / `pt` / `ru` / `pl` / `nl` / `ar` / `th` / `tr`), and the UI-facing language code is managed separately from the LLM prompt notation (e.g., `zh` → "Chinese (Simplified)"). The output format uses the same line-based format (5 fields) as MF-02.

The translation bar transitions through states: "untranslated" (language-selection dropdown + translate button) → "translating" (`isTranslating = true`, dropdown and button disabled) → "translated" (`{language} ✓` + a re-translate dropdown). Once translation completes, the review screen (S-02) switches to a two-column source/translation display, and the [[DefinedTerm/document-chat]] session is re-initialized in the translated language (history cleared, snackbar notification shown). On translation failure, `isTranslating` is reset to false and the translation bar shows "Translation failed. Please retry."

### Translation data model

`Translation`, attached via `reviewResult.copy(translation = ...)` once MF-03 completes, consists of `language` (target language code) / `summary` / `deadlineNote` / `actionItems` (`TranslatedActionItem`: id, description) / `requiredItems` (`TranslatedRequiredItem`: id, name, note) / `warning` (`TranslatedWarning`: description; only the single item corresponding to `Warning`). `warning.severity` (high/medium/low) is excluded from translation since it needs no localization.

### Pre-masking and prompt design

Before the fields to be translated are passed to Gemma 4, pre-masking via `PiiMasker.mask()` is applied, and the System Prompt explicitly instructs that bracketed placeholders such as `[Applicant name]` be preserved as opaque strings in the output as-is. This means the translation result is automatically already in masked form. The output format follows the same line-based format as MF-02 (5 fields: `SUMMARY` / `DEADLINE_NOTE` / `ACTION_ITEMS` / `REQUIRED_ITEMS` / `WARNING`), and on a parse error it retries after appending error information.

### Target-language dropdown

The target-language dropdown (`TranslationBar` / `DropdownMenuLanguageSelector`) excludes the source language (`sourceLanguage`) from its candidates among the 15 supported languages.

## Related Terms

- [[SoftwareApplication/paperwork-navigator]]
- [[DefinedTerm/structured-field-extraction]]
- [[DefinedTerm/document-chat]]
- [[DefinedTerm/review-screen]]
