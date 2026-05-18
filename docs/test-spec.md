# Paperwork Navigator Test Specification

> Version: 0.1.0
> Created: 2026-05-07
> Target: MVP (P1 Features MF-01–MF-07)
> Based on: Implementation Specification v0.1.0

---

## Table of Contents

1. [Test Policy & Scope](#1-test-policy--scope)
2. [Test Environment](#2-test-environment)
3. [MF-01 TextExtractor Test](#3-mf-01-textextractor-test)
4. [MF-02 FieldExtractor Test](#4-mf-02-fieldextractor-test)
5. [MF-03 Translator Test](#5-mf-03-translator-test)
6. [MF-05 PiiMasker Test](#6-mf-05-piimasker-test)
7. [MF-06 EscalationPackageGenerator Test](#7-mf-06-escalationpackagegenerator-test)
8. [MF-07 DocumentChatSession Test](#8-mf-07-documentchatsession-test)
9. [DocumentReviewViewModel State Transition Test](#9-documentreviewviewmodel-state-transition-test)
10. [Screen Tests (S-01 / S-02 / S-03)](#10-screen-tests-s-01--s-02--s-03)
11. [Error Handling Test](#11-error-handling-test)
12. [EscalationPackage.toPlainText() Test](#12-escalationpackagetoplaintext-test)
13. [DocumentRepository Test](#13-documentrepository-test)
14. [Non-Functional Test](#14-non-functional-test)
15. [EntityExtractor Test](#15-entityextractor-test)
16. [Test Data](#16-test-data)
17. [Intent Receipt Test](#17-intent-receipt-test)

---

## 1. Test Policy & Scope

### 1.1 Test Levels

| Level | Target | Tools |
|-------|--------|-------|
| Unit Test | Processing steps (`processing/`), data models, ViewModel logic | JUnit 4 / 5, Kotlin Coroutines Test |
| Integration Test | ViewModel + processing step interaction, Repository + file system | JUnit 4, Robolectric |
| UI Test | Compose screen display and operation | Compose UI Testing, Espresso |
| E2E Test | Complete flow S-01 → S-02 → S-03 (on device) | Manual test / UIAutomator |

### 1.2 Out of Scope (Non-MVP)

- Camera capture and OCR processing
- Cloud LLM escalation
- Encrypted storage
- Multi-document cross-search

### 1.3 LLM-Dependent Processing Test Strategy

MF-02, MF-03, MF-06, and MF-07 are unit-tested using mocked `LlmModelHelper` without the actual Gemma 4 model. The actual model is used in on-device E2E tests.

---

## 2. Test Environment

| Item | Value |
|------|-------|
| Device | Google Pixel 9 (RAM 12 GB, Android 15) |
| minSdk | 35 |
| Unit Test JVM | JDK 17 |
| Mock | MockK or Mockito-Kotlin |
| Test PDF | `edogawa_R7_genkyo_kinyuurei.pdf` (with text layer) |
| Timeout Test Implementation | Use `kotlinx.coroutines.test.TestCoroutineScheduler.advanceTimeBy()` to advance virtual time instead of actually waiting 150/60/30 seconds (to avoid slowing down CI), or substitute with mock throwing timeout exceptions immediately |

---

## 3. MF-01 TextExtractor Test

### TC-01-01: Normal Text Extraction from PDF with Text Layer

| Item | Content |
|------|---------|
| Target | `TextExtractor.extract()` |
| Type | Integration (Robolectric) |
| Prerequisite | Test PDF (with text layer) placed in `assets/` |
| Procedure | 1. Generate URI for test PDF<br>2. Call `TextExtractor.extract(context, uri)` |
| Expected Result | Non-empty string returned<br>Pages concatenated with `\n\n` |

### TC-01-02: Multi-Page PDF Concatenation

| Item | Content |
|------|---------|
| Prerequisite | 2+ page PDF with text layer |
| Procedure | Call `TextExtractor.extract()` |
| Expected Result | Text from each page concatenated with `\n\n` |

### TC-01-03: PDF Without Text Layer

| Item | Content |
|------|---------|
| Prerequisite | Scanned image-only PDF (no text layer) |
| Procedure | Call `TextExtractor.extract()` |
| Expected Result | `ExtractionError.NoPdfTextLayer` is thrown |

### TC-01-04: Text File (UTF-8)

| Item | Content |
|------|---------|
| Prerequisite | Plain text file with UTF-8 encoding |
| Procedure | Call `TextExtractor.extract()` |
| Expected Result | File contents returned as-is |

### TC-01-05: Trim Exceeding 8,000 Characters

| Item | Content |
|------|---------|
| Prerequisite | PDF containing 9,000 characters |
| Procedure | Call `TextExtractor.extract()` |
| Expected Result | Return value is maximum 8,000 characters<br>First 8,000 characters are preserved |

### TC-01-06: Unsupported Format

| Item | Content |
|------|---------|
| Prerequisite | `.docx` file URI |
| Procedure | Call `TextExtractor.extract()` |
| Expected Result | `ExtractionError.UnsupportedFormat` is thrown |

### TC-01-07: I/O Error

| Item | Content |
|------|---------|
| Prerequisite | Non-existent URI |
| Procedure | Call `TextExtractor.extract()` |
| Expected Result | `ExtractionError.IoError` is thrown |

### TC-01-08: Tab-Delimited Text Space Normalization

| Item | Content |
|------|---------|
| Target | `TextExtractor.normalizeSpaces()` |
| Type | Unit |
| Prerequisite | English PDF's `PdfRenderer.Page.getTextContents()` returns tab-delimited text as a single `TextContent` (with no position info: `bounds=[]`) |
| Procedure | Call `TextExtractor.normalizeSpaces("I\tconsent\tform")` directly |
| Expected Result | `"I consent form"` is returned |

### TC-01-09: Horizontal Whitespace Bulk Normalization

| Item | Content |
|------|---------|
| Target | `TextExtractor.normalizeSpaces()` |
| Type | Unit |
| Procedure | Call `TextExtractor.normalizeSpaces("a\t  　b")` (mixture of tab, normal space, non-breaking space, and full-width space) |
| Expected Result | `"a b"` is returned (consecutive horizontal whitespace collapsed to single space) |

---

## 4. MF-02 FieldExtractor Test

### TC-02-01: Normal JSON Extraction

| Item | Content |
|------|---------|
| Target | `FieldExtractor.extract()` |
| Type | Unit (LlmModelHelper mock) |
| Prerequisite | `llmHelper.runInference()` returns few-shot example JSON from specification §6.6 |
| Procedure | Call `FieldExtractor.extract(model, sampleText, {})` |
| Expected Result | `ReviewResult` correctly deserialized<br>`docType == "児童手当現況届"`<br>`importance == "high"`<br>`translation == null` |

### TC-02-02: All Fields Validation in pii_candidates

| Item | Content |
|------|---------|
| Prerequisite | Mock returns few-shot example JSON |
| Expected Result | `pii_candidates` contains 4 items<br>Each PiiSpan has correct `id`, `labelJa`, `spanText`, `category`, `maskRecommended` |

### TC-02-03: deadline.date in ISO 8601 Format

| Item | Content |
|------|---------|
| Prerequisite | Mock returns JSON with `"date": "2025-06-30"` |
| Expected Result | `reviewResult.deadline.date == "2025-06-30"` |

### TC-02-04: deadline.date is null

| Item | Content |
|------|---------|
| Prerequisite | Mock returns JSON with `"date": null` |
| Expected Result | `reviewResult.deadline.date == null` |

### TC-02-05: JSON Parse Error Retry (1st Fail, 2nd Success)

| Item | Content |
|------|---------|
| Prerequisite | 1st returns invalid JSON, 2nd returns valid JSON from mock |
| Procedure | Call `FieldExtractor.extract()` |
| Expected Result | Retry occurs and `ReviewResult` is returned<br>Total `runInference()` calls = 2 |

### TC-02-06: JSON Parse Error 3 Times in a Row

| Item | Content |
|------|---------|
| Prerequisite | Mock always returns invalid JSON |
| Expected Result | `FieldExtractionError.JsonParseError` is thrown<br>`runInference()` called maximum 3 times |

### TC-02-07: Model Not Initialized

| Item | Content |
|------|---------|
| Prerequisite | `llmHelper` simulates uninitialized model state |
| Expected Result | `FieldExtractionError.ModelNotInitialized` is thrown |

### TC-02-08: Inference Timeout (150 seconds)

| Item | Content |
|------|---------|
| Prerequisite | `llmHelper.runInference()` does not respond for 150+ seconds |
| Expected Result | `FieldExtractionError.InferenceError` is thrown |

### TC-02-09: onProgress Callback

| Item | Content |
|------|---------|
| Prerequisite | Mock streams multiple tokens |
| Expected Result | `onProgress` callback called multiple times |

### TC-02-10: conditions.applicable is null

| Item | Content |
|------|---------|
| Prerequisite | Mock returns JSON with `"applicable": null` |
| Expected Result | `conditions[0].applicable == null` |

### TC-02-11: MF-02 Input Text Trimmed to 6,000 Characters

| Item | Content |
|------|---------|
| Type | Unit (ViewModel) |
| Prerequisite | `TextExtractor` returns 7,000 characters |
| Expected Result | Text passed to `FieldExtractor.extract()` is trimmed to 6,000 characters or less<br>(Specification §9.4: MF-02 input limit is 6,000 characters) |

---

## 5. MF-03 Translator Test

### TC-03-01: Normal English Translation

| Item | Content |
|------|---------|
| Target | `Translator.translate()` |
| Type | Unit (mock) |
| Prerequisite | `reviewResult` has sample data, `targetLanguage = "en"` |
| Procedure | Call `Translator.translate(model, reviewResult, "en")` |
| Expected Result | Return value has `translation.language == "en"`<br>`translation.summary` is non-empty string<br>Number of `translation.actionItems` matches original `actionItems` |

### TC-03-02: id Field Preserved After Translation

| Item | Content |
|------|---------|
| Expected Result | `translation.actionItems[].id` matches original `actionItems[].id` |

### TC-03-03: translation.note is null when required_docs.note_ja is null

| Item | Content |
|------|---------|
| Prerequisite | `required_docs[0].noteJa = null` |
| Expected Result | `translation.requiredDocs[0].note == null` |

### TC-03-04: Non-Translated Fields Remain Unchanged

| Item | Content |
|------|---------|
| Expected Result | After translation, `reviewResult.piiCandidates`, `conditions`, `id` fields are identical to originals<br>(Specification §5.1 note: `conditions` is not translation target) |

### TC-03-05: Translation Failure

| Item | Content |
|------|---------|
| Prerequisite | `llmHelper.runInference()` throws exception |
| Expected Result | `Translator.translate()` throws exception<br>On ViewModel side, translation failure flag is set and transition to S-02 with `translation == null` |

### TC-03-06: All 15 Language Codes Accepted

| Item | Content |
|------|---------|
| Procedure | Pass `targetLanguage` as `"ja"`, `"en"`, `"zh"`, `"ko"`, `"es"`, `"fr"`, `"de"`, `"it"`, `"pt"`, `"ru"`, `"pl"`, `"nl"`, `"ar"`, `"th"`, `"tr"` respectively |
| Expected Result | All execute normally and `translation.language` matches passed language code |

---

## 6. MF-05 PiiMasker Test

### TC-05-01: Default Masking for Recommended Spans

| Item | Content |
|------|---------|
| Target | `PiiMasker.mask()` |
| Type | Unit |
| Prerequisite | `spanText = "山田太郎"`, `sourceField = "applicant_name"`, `maskRecommended = true`, `userOverride = null` |
| Procedure | Call `PiiMasker.mask(sourceText, listOf(span))` |
| Expected Result | "山田太郎" in `maskedText` is replaced with `[Applicant name]` |

### TC-05-02: userOverride = true (Force Mask)

| Item | Content |
|------|---------|
| Prerequisite | `maskRecommended = false`, `userOverride = true` |
| Expected Result | Span is masked |

### TC-05-03: userOverride = false (Force Exclude)

| Item | Content |
|------|---------|
| Prerequisite | `maskRecommended = true`, `userOverride = false` |
| Expected Result | Span is not masked<br>Span included in `skippedSpans` |

### TC-05-04: maskRecommended = false and userOverride = null (Default Exclude)

| Item | Content |
|------|---------|
| Prerequisite | `maskRecommended = false`, `userOverride = null` |
| Expected Result | Span is not masked<br>Span not included in `appliedSpans` |
| Note | Specification §5.3 defines `skippedSpans` as "spans excluded by user"; whether default exclusion (with `userOverride = null`) is included in this list is not explicitly specified. Update expected result after implementation confirmation, aligned with TC-TXT-04 |

### TC-05-05: Same Text Appearing at Multiple Locations All Masked

| Item | Content |
|------|---------|
| Prerequisite | `sourceText = "山田太郎と山田太郎は同一人物"`, `spanText = "山田太郎"` |
| Expected Result | Both occurrences are masked |

### TC-05-06: Span Not Found in Source Records as unmatchedSpans

| Item | Content |
|------|---------|
| Prerequisite | `spanText = "存在しない文字列"` (non-existent string) |
| Expected Result | `maskedText` unchanged<br>Span included in `unmatchedSpans`<br>Span not included in `appliedSpans` |

### TC-05-07: Match After Normalization (Whitespace and Newline Differences)

| Item | Content |
|------|---------|
| Prerequisite | `sourceText` contains full-width space in name part<br>`spanText` is separated by regular space |
| Expected Result | Matches after normalization and is masked |

### TC-05-08: Multiple Spans Masking Order

| Item | Content |
|------|---------|
| Prerequisite | 3 spans: name, address, date of birth |
| Expected Result | All are masked<br>`appliedSpans` contains 3 items |

### TC-05-09: remask for Post-User-Edit Re-masking

| Item | Content |
|------|---------|
| Prerequisite | After initial `mask()`, change span's `userOverride` |
| Procedure | Call `PiiMasker.remask(originalText, updatedSpans)` |
| Expected Result | New masking result follows updated `userOverride` |

### TC-05-10: maskToken() Returns Labeled Token

| Item | Content |
|------|---------|
| Procedure | Call `PiiMasker.mask()` for each span with `sourceField` set |
| Expected Result | Span with `sourceField = "applicant_name"` → replaced with `[Applicant name]`<br>Span with `sourceField = "issuer_address"` → replaced with `[Issuer address]`<br>Span with `sourceField = null` → fallback to `[■■■]` |

---

## 7. MF-06 EscalationPackageGenerator Test

### TC-06-01: Normal Package Generation

| Item | Content |
|------|---------|
| Target | `EscalationPackageGenerator.generate()` |
| Type | Unit (mock) |
| Prerequisite | Mock returns valid JSON (`consultation_summary`, `timeline`, `ai_hypotheses`) |
| Procedure | Call `generate(model, maskResult, reviewResult, "", emptyList(), "en")` |
| Expected Result | `EscalationPackage` returned<br>`consultationSummary` is non-empty<br>`language == "en"` |

### TC-06-02: key_points Language Priority (Translated Field Preferred)

| Item | Content |
|------|---------|
| Prerequisite | `reviewResult.translation` exists |
| Expected Result | `key_points[].description` uses translated field values |

### TC-06-03: key_points Language Priority (No Translation → _ja Fallback)

| Item | Content |
|------|---------|
| Prerequisite | `reviewResult.translation == null` |
| Expected Result | `key_points[].description` uses `_ja` field values |

### TC-06-04: maskedFields Contains List of Masked Categories

| Item | Content |
|------|---------|
| Prerequisite | `appliedSpans` has 3 categories: name, address, dob |
| Expected Result | `maskedFields` contains `["name", "address", "dob"]` (order irrelevant) |

### TC-06-05: relatedDocuments Records ReviewResult.docType

| Item | Content |
|------|---------|
| Expected Result | `relatedDocuments[0].name == reviewResult.docType` |

### TC-06-06: chatHistory Recorded As-Is

| Item | Content |
|------|---------|
| Prerequisite | `chatMessages` has 2 items (user, assistant) |
| Expected Result | `chatHistory` role / content matches source data |

### TC-06-07: When userNotes is Empty

| Item | Content |
|------|---------|
| Prerequisite | `userNotes = null` |
| Expected Result | `EscalationPackage.userNotes == null`<br>Section 7 in `toPlainText()` outputs "(メモなし)" |
| Note | Handling of empty string `""` vs. `null` is undefined in implementation specification §5.5 (only states `"string or null"`). Determine during implementation whether to normalize empty string to `null`, and confirm with TC-TXT-04 |

### TC-06-08: Only conditions with applicable=null Passed to Prompt

| Item | Content |
|------|---------|
| Prerequisite | `conditions` has 1 item with `applicable=true` and 1 with `applicable=null` |
| Expected Result | Mock's prompt argument `{conditions}` includes only the `applicable=null` item |

### TC-06-09: When conditions is Empty, "None" is Passed

| Item | Content |
|------|---------|
| Prerequisite | 0 conditions with `applicable=null` |
| Expected Result | Prompt's `{conditions}` is `"None"` |

### TC-06-10: maskedSourceText Matches maskResult.maskedText

| Item | Content |
|------|---------|
| Prerequisite | `maskResult.maskedText = "受給者 [Applicant name]、住所 [Applicant address]..."` |
| Procedure | Call `generate()` |
| Expected Result | `escalationPackage.maskedSourceText == maskResult.maskedText` |

---

## 8. MF-07 DocumentChatSession Test

### TC-07-01: First Message After initialize (English)

| Item | Content |
|------|---------|
| Target | `DocumentChatSession.initialize()`, `sendMessage()` |
| Type | Unit (mock) |
| Prerequisite | `targetLanguage = "en"` |
| Procedure | Call `sendMessage()` after `initialize()` |
| Expected Result | `getChatHistory()` contains 1 user and 1 assistant message each |

### TC-07-02: Initial Assistant Message Language Switching

| Item | Content |
|------|---------|
| Procedure | Call `initialize()` for each `targetLanguage`: `"ja"`, `"en"`, `"zh"`, `"ko"` |
| Expected Result | Initial message in each language matches specification §6.5 table |

### TC-07-03: Chat History Accumulation

| Item | Content |
|------|---------|
| Procedure | Call `sendMessage()` 3 times |
| Expected Result | `getChatHistory()` returns 3 exchanges (6 messages) |

### TC-07-04: History Reset After clear()

| Item | Content |
|------|---------|
| Procedure | Call `sendMessage()` 2 times, then call `clear()` |
| Expected Result | `getChatHistory()` returns empty list |

### TC-07-05: 20 Turn Limit

| Item | Content |
|------|---------|
| Procedure | Call `sendMessage()` 11 times (Specification §9.4: 20 turns = 10 Q&A exchanges = 10 `sendMessage()` calls max) |
| Expected Result | Calls 1–10 process normally<br>11th call detects limit reached and enters "chat history limit reached" state |

### TC-07-06: Cumulative 4,000 Character Limit

| Item | Content |
|------|---------|
| Procedure | Send 2 messages of 2,100 characters each |
| Expected Result | Limit reached is detected and new input is disabled |

### TC-07-07: onToken Callback Streaming

| Item | Content |
|------|---------|
| Prerequisite | Mock streams multiple tokens |
| Expected Result | `onToken` callback called multiple times<br>Return value `ChatMessage.content` is string with all tokens concatenated |

### TC-07-08: ChatMessage role and id

| Item | Content |
|------|---------|
| Expected Result | Return value `role == ChatRole.ASSISTANT`<br>`id` is UUID format (non-empty) |

### TC-07-09: System Prompt Does Not Include PII spanText

| Item | Content |
|------|---------|
| Prerequisite | `reviewResult.piiCandidates` has "山田太郎" (registered as piiSpan's spanText) |
| Expected Result | System prompt passed to mock does not include `piiSpans[].spanText` ("山田太郎" etc.). However, `sourceText` is included in system prompt (permitted for on-device inference) |

### TC-07-10: System Prompt Includes Response Constraint

| Item | Content |
|------|---------|
| Target | `PromptBuilder.mf07SystemPrompt()` |
| Type | Unit |
| Procedure | Call `mf07SystemPrompt()` with arbitrary `ReviewResult` and `targetLanguage` |
| Expected Result | Return value system prompt includes `"Limit your reply to 1–2 sentences."` |

### TC-07-11: Typical Question with Response in 1–2 Sentences (On-Device)

| Item | Content |
|------|---------|
| Target | `DocumentChatSession.sendMessage()` |
| Type | Manual (on-device Pixel 9, Gemma 4 E2B) |
| Procedure | 1. Analyze 児童手当現況届 and display S-02<br>2. Send "締め切りはいつですか？"<br>3. Send "何を持参すれば良いですか？" |
| Expected Result | Each answer is 1–2 sentences |
| Note | Prompt constraint (`Limit your reply to 1–2 sentences.`) serves as safety net replacing `maxOutputTokens` (Prompt Specification §7.1 note) |

---

## 9. DocumentReviewViewModel State Transition Test

### TC-VM-01: Initial State is Idle

| Item | Content |
|------|---------|
| Expected Result | `viewModel.uiState.value` is `DocumentReviewUiState.Idle` |

### TC-VM-02: Transition to Processing on Analysis Start

| Item | Content |
|------|---------|
| Procedure | Input text and call `startAnalysis()` |
| Expected Result | `uiState` transitions to `Processing(step=EXTRACTING_TEXT, progress=0.0f)` |

### TC-VM-03: Processing Step Progression

| Item | Content |
|------|---------|
| Expected Result | After MF-01 completion: `step=EXTRACTING_FIELDS`<br>After MF-02 completion: `step=TRANSLATING`<br>After MF-03 completion: `Review` state |

### TC-VM-04: Transition to Review on Analysis Success

| Item | Content |
|------|---------|
| Prerequisite | MF-01–03 all successful |
| Expected Result | `uiState` transitions to `Review`<br>`Review.reviewResult`, `Review.maskResult` are set |

### TC-VM-05: Chat Session Initialized on Review Transition

| Item | Content |
|------|---------|
| Expected Result | At same time as `Review` transition, `DocumentChatSession.initialize()` is called |

### TC-VM-06: Transition to Error on Analysis Failure

| Item | Content |
|------|---------|
| Prerequisite | MF-02 throws `FieldExtractionError.JsonParseError` |
| Expected Result | `uiState` transitions to `Error(message="JSONの解析に失敗しました")` |

### TC-VM-07: Retry from Error State Returns to Idle

| Item | Content |
|------|---------|
| Procedure | Call `retryAnalysis()` |
| Expected Result | `uiState` transitions to `Idle` |

### TC-VM-08: Transition to GeneratingEscalation on Escalation File Creation

| Item | Content |
|------|---------|
| Prerequisite | `uiState` is `Review` |
| Procedure | Call `generateEscalation(userNotes)` |
| Expected Result | `uiState` transitions to `GeneratingEscalation` |

### TC-VM-09: Transition to OutputPreview on Escalation Generation Complete

| Item | Content |
|------|---------|
| Prerequisite | MF-06 completes normally |
| Expected Result | `uiState` transitions to `OutputPreview(pkg=...)` |

### TC-VM-10: Data Cleared on Review → Idle Transition

| Item | Content |
|------|---------|
| Procedure | Execute navigation back from S-02 |
| Expected Result | `uiState` is `Idle`<br>`ReviewResult`, `MaskResult`, chat history are cleared |

### TC-VM-11: Data Preserved on OutputPreview → Review Transition

| Item | Content |
|------|---------|
| Procedure | Navigate back from S-03 to S-02 |
| Expected Result | `uiState` is `Review`<br>`ReviewResult`, `MaskResult`, chat history are preserved |

### TC-VM-12: Escalation Button Disabled While chatIsGenerating

| Item | Content |
|------|---------|
| Prerequisite | `uiState` is `Review(chatIsGenerating=true)` |
| Expected Result | `generateEscalation()` call is disabled (or blocked by Mutex) |

### TC-VM-13: Exclusive Control of LLM Inference

| Item | Content |
|------|---------|
| Procedure | Call `generateEscalation()` in parallel while chat is generating |
| Expected Result | Mutex prevents simultaneous execution<br>Escalation inference starts after preceding inference completes |

### TC-VM-14: Partial Transition on Translation Failure

| Item | Content |
|------|---------|
| Prerequisite | MF-03 fails |
| Expected Result | `uiState` transitions to `Review` (without translation)<br>Translation failure flag is set |

### TC-VM-15: MF-05 (PiiMasker) Executed Immediately After MF-02 Completion

| Item | Content |
|------|---------|
| Type | Unit (ViewModel) |
| Prerequisite | MF-01 and MF-02 successful; MF-03 still running |
| Procedure | Check ViewModel state immediately after MF-02 completion |
| Expected Result | `PiiMasker.mask()` is called without waiting for MF-03 completion<br>(Specification §4.1: MF-05 can execute immediately after MF-02, LLM not required) |

---

## 10. Screen Tests (S-01 / S-02 / S-03)

### S-01: Input Screen

#### TC-UI-S01-01: Idle Display

| Item | Content |
|------|---------|
| Prerequisite | `uiState = Idle` |
| Expected Result | PDF selection button displayed<br>Text paste area displayed<br>4 translation target language radio buttons displayed<br>"Start Analysis" button displayed |

#### TC-UI-S01-02: Language Selection Default Value

| Item | Content |
|------|---------|
| Expected Result | English is selected by default in initial state |
| Note | Specification §3.3 states "MVP focuses on English verification" but does not explicitly specify UI default selection. Add to specification that English is default and finalize this test case during implementation |

#### TC-UI-S01-03: Display During Analysis (Progress Display)

| Item | Content |
|------|---------|
| Prerequisite | `uiState = Processing(step=EXTRACTING_FIELDS, progress=0.6f)` |
| Expected Result | Current step label "Extracting Important Fields..." is displayed<br>Progress bar reflects 60%<br>Input area and language selection disabled<br>"Start Analysis" button hidden |

#### TC-UI-S01-04: Error Display and Retry Button

| Item | Content |
|------|---------|
| Prerequisite | `uiState = Error("JSONの解析に失敗しました")` |
| Expected Result | Error message displayed<br>"Retry" button displayed |

#### TC-UI-S01-05: File Name Display After PDF Selection

| Item | Content |
|------|---------|
| Procedure | Select a PDF file |
| Expected Result | File name displayed in PDF selection button area |

#### TC-UI-S01-06: Guidance When Model Not Downloaded

| Item | Content |
|------|---------|
| Prerequisite | Model not downloaded |
| Procedure | Tap "Start Analysis" |
| Expected Result | Navigate to model manager screen (S-M) |

### S-02: Review Screen

#### TC-UI-S02-01: Japanese-English Side-by-Side Display

| Item | Content |
|------|---------|
| Prerequisite | `uiState = Review(reviewResult=..., targetLanguage="en")` |
| Expected Result | Left column shows Japanese, right column shows English<br>Both columns display same document content |

#### TC-UI-S02-02: Translation Failure Banner Display

| Item | Content |
|------|---------|
| Prerequisite | Translation failure flag set |
| Expected Result | Banner displays "Translation failed. Displaying Japanese only"<br>English column hidden |

#### TC-UI-S02-03: Color-Coded Badge Display

| Item | Content |
|------|---------|
| Expected Result | `severity: high` warning shows red badge<br>`action_items` show orange badge |

#### TC-UI-S02-04: PII Masking Section Display

| Item | Content |
|------|---------|
| Expected Result | Masked PII displayed as `[Applicant name]` / `[Issuer address]` etc. labeled tokens<br>"Review/Edit Masking Range" button displayed |

#### TC-UI-S02-05: PII Edit Panel Expand/Collapse

| Item | Content |
|------|---------|
| Procedure | Tap "Review/Edit Masking Range ▼" |
| Expected Result | List of each PiiSpan expands inline |

#### TC-UI-S02-06: PII Toggle to Exclude from Masking

| Item | Content |
|------|---------|
| Procedure | Uncheck account span checkbox in PII edit panel |
| Expected Result | `remask()` is called<br>PII preview in upper section updates |

#### TC-UI-S02-07: Chat Section Initial Message

| Item | Content |
|------|---------|
| Prerequisite | Immediately after `Review` transition |
| Expected Result | AI's initial message is displayed |

#### TC-UI-S02-08: Input Disabled While Chat Generating

| Item | Content |
|------|---------|
| Prerequisite | `chatIsGenerating = true` |
| Expected Result | Text input field and send button disabled<br>"Create Escalation File" button disabled |

#### TC-UI-S02-09: Chat Send and Answer Streaming

| Item | Content |
|------|---------|
| Procedure | Input message and tap send button |
| Expected Result | User message displayed<br>AI answer streams and displays progressively |

#### TC-UI-S02-10: "Create Escalation File" Button Starts Escalation

| Item | Content |
|------|---------|
| Procedure | Tap "Create Escalation File" button |
| Expected Result | Button switches to spinner with progress display<br>Chat input disabled<br>Navigate to S-03 after inference completes |

#### TC-UI-S02-11: Chat History Limit Reached Display

| Item | Content |
|------|---------|
| Prerequisite | Chat history reaches limit (20 turns or 4,000 characters) |
| Expected Result | "Chat history limit reached" is displayed<br>New input field disabled |

#### TC-UI-S02-12: User Notification When unmatchedSpans Exist

| Item | Content |
|------|---------|
| Prerequisite | `maskResult.unmatchedSpans` has 1+ items |
| Expected Result | S-02 PII section displays notification like "Items that could not be masked"<br>(Specification §7.4: Notify user of unmatchedSpans on UI) |

### S-03: Output Confirmation Screen

#### TC-UI-S03-01: Handoff Text Preview Display

| Item | Content |
|------|---------|
| Prerequisite | `uiState = OutputPreview(pkg=...)` |
| Expected Result | Sections from `## 1. 相談概要` to `## 8. 事前質問・確認事項` displayed |

#### TC-UI-S03-02: Masked Information Warning Banner

| Item | Content |
|------|---------|
| Expected Result | "⚠️ Personal information masked / masked: name, address..." banner displayed |

#### TC-UI-S03-03: Copy Button

| Item | Content |
|------|---------|
| Procedure | Tap "📋 Copy" button |
| Expected Result | `toPlainText()` content copied to clipboard |

#### TC-UI-S03-04: Share Button

| Item | Content |
|------|---------|
| Procedure | Tap "↑ Share" button |
| Expected Result | Android share sheet (ACTION_SEND) launches<br>Share text is `toPlainText()` content |

#### TC-UI-S03-05: Back Button Transitions to S-02

| Item | Content |
|------|---------|
| Procedure | Tap "←" button |
| Expected Result | Navigate to S-02<br>`ReviewResult` and `MaskResult` preserved |

---

## 11. Error Handling Test

### TC-ERR-01: PDF Without Text Layer

| Item | Content |
|------|---------|
| Prerequisite | PDF without text layer |
| Expected Result | "This PDF cannot read text" is displayed on S-01 |

### TC-ERR-02: MF-02 Timeout (150 seconds)

| Item | Content |
|------|---------|
| Prerequisite | `FieldExtractor` does not respond for 150+ seconds |
| Expected Result | "Analysis failed. Please retry." is displayed on S-01<br>`uiState` is `Error` |

### TC-ERR-03: MF-06 Timeout (30 seconds)

| Item | Content |
|------|---------|
| Prerequisite | `EscalationPackageGenerator` does not respond for 30+ seconds (in `GeneratingEscalation` state) |
| Expected Result | "Analysis failed. Please retry." displayed on S-02<br>(Specification §4.3: MF-06 timeout uses same message as MF-02)<br>`uiState` returns to `Review` and "Create Escalation File" button re-enabled |

### TC-ERR-04: MF-07 Chat Timeout (60 seconds)

| Item | Content |
|------|---------|
| Prerequisite | `DocumentChatSession.sendMessage()` does not respond for 60+ seconds |
| Expected Result | "Failed to generate answer. Please send again." displayed above chat input<br>Failed assistant message not retained in history |

### TC-ERR-05: MF-07 Chat Session Initialization Failure

| Item | Content |
|------|---------|
| Prerequisite | `DocumentChatSession.initialize()` throws exception |
| Expected Result | Chat section hidden<br>Review and escalation features remain usable |

### TC-ERR-06: Direct Text Input Exceeds 8,000 Characters

| Item | Content |
|------|---------|
| Prerequisite | **Paste 8,001 characters directly in text area** (text input path, not PDF selection) |
| Expected Result | "Document too long (limit 8,000 characters)" is displayed<br>`startAnalysis()` is aborted |
| Note | PDF selection path auto-trims to 8,000 via TextExtractor (confirmed by TC-01-05), so this error only occurs for direct text input |

### TC-ERR-07: Model Not Downloaded

| Item | Content |
|------|---------|
| Prerequisite | Gemma 4 model not downloaded |
| Procedure | Start analysis |
| Expected Result | Routed to model manager screen |

### TC-ERR-08: MF-03 Translation Failure

| Item | Content |
|------|---------|
| Prerequisite | `Translator.translate()` throws exception |
| Expected Result | English column hidden on S-02<br>"Translation failed. Displaying Japanese only." banner displayed at top of S-02 |

---

## 12. EscalationPackage.toPlainText() Test

### TC-TXT-01: Section Headings and Content Normal Output

| Item | Content |
|------|---------|
| Target | `EscalationPackage.toPlainText()` |
| Expected Result | 8 sections from `## 1. 相談概要` to `## 8. 事前質問・確認事項` included |

### TC-TXT-02: First Line Masking Warning

| Item | Content |
|------|---------|
| Expected Result | First line starts with `⚠️ 個人情報はマスク済み / masked: `<br>`maskedFields` enumerated with `, ` separator |

### TC-TXT-03: Section 6 Omitted When ai_hypotheses Empty

| Item | Content |
|------|---------|
| Prerequisite | `aiHypotheses = emptyList()` |
| Expected Result | Section `## 6. AI Hypotheses・Unclear Points` not output |

### TC-TXT-04: "(No notes)" Output When user_notes Empty

| Item | Content |
|------|---------|
| Prerequisite | `userNotes = null` |
| Expected Result | Section 7 outputs "(メモなし)" |

### TC-TXT-05: Section 8 Empty When chat_history Empty

| Item | Content |
|------|---------|
| Prerequisite | `chatHistory = emptyList()` |
| Expected Result | Section 8 heading output but content empty |

### TC-TXT-06: timeline Date and Event Correctly Formatted

| Item | Content |
|------|---------|
| Prerequisite | `timeline = [{"date": "2025-06-30", "event": "提出期限"}]` |
| Expected Result | Output as `- 2025-06-30: 提出期限` format |

### TC-TXT-07: Section Separator is Single Blank Line

| Item | Content |
|------|---------|
| Expected Result | Each section separated by `\n\n` (not `\n\n\n` or multiple blank lines) |

---

## 13. DocumentRepository Test

### TC-REPO-01: File Created by save()

| Item | Content |
|------|---------|
| Target | `DocumentRepository.save()` |
| Type | Integration (Robolectric) |
| Expected Result | `{filesDir}/documents/{docId}/meta.json` and `source.txt` created |

### TC-REPO-02: escalation.json Created by saveEscalation()

| Item | Content |
|------|---------|
| Expected Result | `{filesDir}/documents/{docId}/escalation.json` created |

### TC-REPO-03: Saved Data Correctly Restored by load()

| Item | Content |
|------|---------|
| Procedure | Call `load(docId)` after `save()` |
| Expected Result | `DocumentBundle.reviewResult` matches saved state |

### TC-REPO-04: list() Returns Saved Documents List

| Item | Content |
|------|---------|
| Procedure | Call `list()` after 2 `save()` operations |
| Expected Result | `List<DocumentMeta>` with 2 items returned |

### TC-REPO-05: File Deleted by delete()

| Item | Content |
|------|---------|
| Procedure | Call `delete(docId)` after `save()` |
| Expected Result | `{filesDir}/documents/{docId}/` directory no longer exists |

### TC-REPO-06: External Storage Not Used

| Item | Content |
|------|---------|
| Expected Result | All read/write operations within `context.filesDir` |

---

## 14. Non-Functional Test

### TC-PERF-01: MF-01 Text Extraction (< 1 second)

| Item | Content |
|------|---------|
| Type | Performance (on-device Pixel 9) |
| Procedure | Process `edogawa_R7_genkyo_kinyuurei.pdf` with `TextExtractor.extract()` |
| Expected Result | Completes in under 1 second |

### TC-PERF-02: MF-02 Field Extraction (60–150 seconds)

| Item | Content |
|------|---------|
| Prerequisite | Gemma 4 E2B model used |
| Expected Result | Completes within 150 seconds |

### TC-PERF-03: MF-03 Translation (15–60 seconds)

| Item | Content |
|------|---------|
| Expected Result | Completes within 60 seconds |

### TC-PERF-04: MF-06 Summary Generation (5–15 seconds)

| Item | Content |
|------|---------|
| Expected Result | Completes within 15 seconds |

### TC-PERF-05: MF-07 Chat Response (3–10 seconds)

| Item | Content |
|------|---------|
| Procedure | Send one-turn question |
| Expected Result | First token arrives within 3 seconds (streaming) |