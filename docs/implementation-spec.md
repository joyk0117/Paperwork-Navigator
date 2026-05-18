# Paperwork Navigator Implementation Specification

> Version: 0.3.0
> Created: 2026-05-07
> Last Updated: 2026-05-18 (PiiMasker span matching: each character escaped with Regex.escape() · whitespace only recorded in unmatchedSpans — §7.5)
> Target: MVP (P1 Features MF-01–MF-07)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Overview](#2-architecture-overview)
3. [Screen Design & Navigation](#3-screen-design--navigation)
4. [Processing Flow](#4-processing-flow)
5. [Data Models (JSON Schema)](#5-data-modelsjson-schema)
6. [Gemma 4 Prompt Design](#6-gemma-4-prompt-design)
7. [Processing Steps (Skill) Interface](#7-processing-stepsskill-interface)
8. [Local Storage](#8-local-storage)
9. [Non-Functional Requirements](#9-non-functional-requirements)

---

## 1. Overview

### 1.1 Project

**Paperwork Navigator** is a privacy-first document navigator that reads, analyzes, and translates documents received (regardless of language or type) on the device, masks PII, and escalates to high-performance LLM or experts as needed.

### 1.2 Sample Documents for Evaluation & Demo

For MVP evaluation and demo, Japanese administrative documents are used as primary samples, but the app targets documents of all languages and types.

- **Sample**: Kodomo Teate Genkyo Kishindoku (Child Allowance Status Report, Edogawa Ward FY2025 Sample)
- **File**: `edogawa_R7_genkyo_kinyuurei.pdf`

### 1.3 MVP Feature Scope (P1)

| ID | Feature |
|----|---------|
| MF-01 | Text Extraction (Text PDF / Text Files / Camera / Gallery Images) |
| MF-02 | Structured JSON Extraction |
| MF-03 | Multilingual Translation |
| MF-04 | Review Screen UI |
| MF-05 | On-Device PII Masking |
| MF-06 | Inquiry Context Generation (Wizard Format) |
| MF-07 | Document Understanding Chat (On-Device, Integrated into Review Screen) |

### 1.4 Related Specifications

In addition to this specification, refer to the following two specifications.

#### [Prompt Specification](プロンプト仕様書.md)

A specification document summarizing the complete text of System Prompts and User Messages used in each LLM call for MF-02, MF-03, MF-06, and MF-07, along with variable references needed during implementation. Section §6 of this specification documents the design approach and overview of prompt design; the actual prompt strings, variable definitions, few-shot examples, and evaluation criteria details are in the prompt specification.

| Content | Location |
|---------|----------|
| Design Approach (English System Prompt, structured output, context length) | §1 |
| MF-02 Field Extraction Prompt & Retry Policy | §2 |
| MF-03 Translation Prompt & Language Code Mapping | §3 |
| MF-06 Escalation Generation Prompt & Variable List | §4 |
| MF-07 Chat System Prompt & Initial Message | §5 |
| Few-shot Example (Child Allowance Status Report Sample) | §6 |
| Complete Variable & Source Class Mapping | §7 |
| Evaluation Criteria for Each MF · E2B vs E4B Switch Criteria | §8 |

#### [Privacy Specification](プライバシー仕様書.md)

A specification that defines how Paperwork Navigator classifies PII (Personally Identifiable Information) and how it is protected throughout the lifecycle from acquisition to deletion. It establishes judgment criteria for "which data may and must not be output externally" and specifies which data to pass in each LLM call. During implementation, refer to this specification together with §8 (Local Storage) of this specification.

| Content | Location |
|---------|----------|
| Design Principles (On-Device Inference · Least Privilege · User Consent) | §1 |
| Data Classification (Tier 1–3) | §2 |
| PII Lifecycle (Input → Inference → Masking → Storage → Output) | §3 |
| Input Data Policy per LLM Call | §4 |
| Privacy-Conscious UI/UX (Mask Display · Transparency) | §5 |
| MVP Limitations & Future Plans | §6 |

---

## 2. Architecture Overview

### 2.1 Technology Stack

| Element | Technology |
|---------|-----------|
| Base | AI Edge Gallery Fork |
| LLM Runtime | LiteRT-LM 0.11.0 |
| Model (Recommended) | Gemma 4 E2B |
| Model (High-Accuracy Option) | Gemma 4 E4B |
| UI | Jetpack Compose |
| PDF Text Extraction | Android Standard PdfRenderer |
| OCR (Images · Camera) | ML Kit Text Recognition Japanese 16.0.1 |
| Language Identification | ML Kit Language Identification 17.0.6 |
| Document Scanner | ML Kit Document Scanner 16.0.0-beta1 |
| Camera Preview | CameraX 1.4.2 |
| DI | Hilt |
| State Management | ViewModel + StateFlow |

### 2.2 Existing Framework

Leverage the following interfaces from AI Edge Gallery.

- **`CustomTask`** Interface: Task registration, model initialization, cleanup (`io.github.joyk0117.privatepaperwork.customtasks.CustomTask`)
- **`LlmModelHelper`** Interface: LiteRT-LM inference abstraction
- **`LlmChatModelHelper`**: LiteRT-LM implementation of `LlmModelHelper` (existing)
- **ModelManagerViewModel**: Model download management (existing)

### 2.3 Components to Add

```
customtasks/
└── documentreview/                   ← New Addition
    ├── DocumentReviewTask.kt         ← CustomTask Implementation (Task Registration)
    ├── DocumentReviewViewModel.kt    ← State Management · Inference Orchestration
    ├── DocumentReviewScreen.kt       ← Main Compose Screen
    ├── model/
    │   └── DocumentReview.kt        ← ReviewResult, PiiSpan, DocumentInput, etc.
    ├── processing/                  ← Processing Steps (Corresponds to "Skill" in Design Notes)
    │   ├── TextExtractor.kt         ← MF-01a: PDF/Text Extraction
    │   ├── ImageTextExtractor.kt    ← MF-01b: ML Kit OCR (Camera · Gallery Images)
    │   ├── LanguageIdentifier.kt    ← MF-01 Post: ML Kit Language Identification
    │   ├── EntityExtractor.kt       ← MF-02 Pre-Processing: ML Kit Entity Extraction (11 Types)
    │   ├── FieldExtractor.kt        ← MF-02: Structured Field Extraction (Gemma 4, 9 Fields)
    │   ├── EntityAnnotator.kt       ← MF-02 Post-Processing: Gemma 4 context_label Assignment
    │   ├── Translator.kt            ← MF-03: Translation
    │   ├── PiiMasker.kt             ← MF-05: PII Masking (Non-LLM)
    │   ├── InquiryContextBuilder.kt      ← MF-06: Inquiry Context Generation (Wizard)
    │   └── DocumentChatSession.kt   ← MF-07: Document Understanding Chat
    └── util/
        └── DocumentIntentBuilder.kt ← Android Intent Generation Utility
```

> **Why Dedicated Screen Instead of AgentChat?**
> AgentChat JS Skill assumes chat UI, and creating a review screen (color-coded, mask editing, bilingual parallel) is more appropriate as a custom `CustomTask` with its own Compose screen.
> The inference itself for LiteRT-LM uses the existing `LlmChatModelHelper` via `LlmModelHelper`, so the inference core is shared.

---

## 3. Screen Design & Navigation

### 3.1 Screen List

| Screen ID | Screen Name | Overview |
|-----------|------------|----------|
| S-01 | Input Screen (Launch Screen) | File Selection / Text Paste / Receive Intent from Other Apps. During analysis, replace button with progress display |
| S-02 | Review Screen | Main Screen. Bilingual parallel display, color-coded, mask editing, document understanding chat |
| S-03 | Inquiry Context Confirmation Screen | Confirm information collected by wizard as context text, copy, share. Intended for pasting into external AI assistants |
| S-04 | Inquiry Document Creation Screen | Enter inquiry purpose/recipient, missing information Q&A (Step 1/2), generate context text |
| S-M | Model Manager (Existing) | Gemma 4 Download Management. Accessible from S-01 |

### 3.2 Navigation Diagram

```
Launch (Direct Launch)
  │
Intent from Other App (ACTION_VIEW PDF / ACTION_SEND PDF · Text · Image)
  │
  ▼
S-01 Input (Launch Screen)
  │                          ┌─────────────────────────┐
  ├─[Model Not Downloaded]──► S-M Model Manager        │
  │                          └─────────────────────────┘
  │
  ├─[Start Analysis]── Button switches to progress display
  │              (Input disabled during analysis)
  │
  ├─[Analysis Success]──► S-02 Review
  │                   │
  │         ┌─────────────────────┴──────────┐
  │  [Create Inquiry Document]          [← Back]
  │         │                               │
  │         ▼                          Return to S-01
  │      S-04 Inquiry Document Creation (Basic Info Input)
  │         │
  │      [Confirm Context]
  │         │                   ┌──────────────────────┐
  │         ▼             [← Back]  → Return to S-04   │
  │      S-03 Inquiry Context Confirmation             │
  │         │                                           │
  │      ┌──┴──────────────┐                           │
  │  [Copy]  [Share]  [← Back]                         │
  │      │       │       │                             │
  │  Copy to Share S-04 Return                         │
  │  Clipboard Sheet                                    │
  │                                                     │
  └─[Analysis Failure]── Display error message on S-01, retry available
```

> **State Management on Back Navigation**: When returning from S-02 → S-01, clear all `ReviewResult`, `MaskResult`, and chat history and return to `Idle` state. When returning from S-04 → S-02, return from `InquiryWizard` to `Review` state, preserving `ReviewResult`, `MaskResult`, and chat history. When returning from S-03 → S-04, return from `InquiryPreview` to `InquiryWizard`, preserving data (context text regeneration is unnecessary and immediate).

### 3.3 Screen Details

#### S-01 Input Screen (Launch Screen)

Use the same screen for both waiting and analysis states, switching only the button section.

**Waiting (No Text / Less Than 200 Characters):**
```
┌─────────────────────────────────┐
│  Paperwork Navigator            │
│  Please Select a Document       │
├─────────────────────────────────┤
│                                 │
│  ┌───────────────────────────┐  │
│  │  📄 Open PDF File         │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  📷 Take Photo            │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  🖼 Choose from Gallery   │  │
│  └───────────────────────────┘  │
│                                 │
│  ─────────── or ────────────    │
│                                 │
│  ┌───────────────────────────┐  │
│  │  Paste text here          │  │
│  │                           │  │
│  │                           │  │
│  └───────────────────────────┘  │
│  ← No expand button < 200 chars  │
│                                 │
│        [Start Analysis]          │
└─────────────────────────────────┘
```

**Waiting (Text ≥ 200 Characters · Collapsed):**
```
┌─────────────────────────────────┐
│  ...（File selection buttons）   │
│  ─────────── or ────────────    │
│  ┌───────────────────────────┐  │
│  │  Paste text here          │  │
│  │  FY2025 Child Allowance   │  │  ← Show first 3–4 lines
│  │  Status Report...         │  │
│  └───────────────────────────┘  │
│               ▼ Show Full (N chars)│  ← Right-aligned button
│                                 │
│        [Start Analysis]          │
└─────────────────────────────────┘
```

**Waiting (Text ≥ 200 Characters · Expanded):**
```
┌─────────────────────────────────┐
│  ...（File selection buttons）   │
│  ─────────── or ────────────    │
│  ┌───────────────────────────┐  │
│  │  Paste text here          │  │
│  │  FY2025 Child Allowance   │  │  ← Scrollable area
│  │  Status Report            │  │    (Max 350dp)
│  │  Recipient Name: ○○ Taro  │  │
│  │  ...full text display...  │  │
│  └───────────────────────────┘  │
│                      ▲ Collapse   │  ← Right-aligned button
│                                 │
│        [Start Analysis]          │
└─────────────────────────────────┘
```

**Text Area Collapse/Expand Rules:**

| Condition | Text Area Height | Expand Button |
|-----------|------------------|---------------|
| Text < 200 chars | Unrestricted (Full Display) | Hidden |
| Text ≥ 200 chars · Collapsed | 100dp Fixed (Approx. 3–4 Lines) | `▼ Show Full (N chars)` |
| Text ≥ 200 chars · Expanded | 200–350dp (Scrollable) | `▲ Collapse` |

In Processing state, the text area becomes collapsed (Processing state is displayed by a separate composable, so returning to Idle resets to collapsed).

**Intent Reception (Sharing from Other Apps · "Open with This App"):**

| Intent | MIME Type | Behavior |
|--------|-----------|----------|
| `ACTION_SEND` / `ACTION_VIEW` | `application/pdf` | Start text extraction and reflect in text area after completion (filename displays in button area during extraction) |
| `ACTION_SEND` | `text/plain` | Set content to text area |
| `ACTION_SEND` | `image/*` | Navigate to perspective correction screen and reflect OCR text to text area after confirmation |

No automatic analysis. After reception, user follows the normal flow by pressing "Start Analysis" button.
If the app is already running (`onNewIntent`), reset current analysis results, return to S-01, and set new input.

**Camera/Gallery Input Language Selection UI:**

Since the document language is unknown before OCR execution for camera/gallery input, display a language selection dialog after image acquisition. Pass the selected language code to `ImageTextExtractor.extract()` / `ImageTextExtractor.extractFromBitmap()` and select the appropriate ML Kit OCR model.

- Camera: Display language selection dialog after scan completion
- Gallery (1 Image): Display language selection dialog after perspective correction completion
- Gallery (2+ Images): Display language selection dialog once after all images are perspective corrected

Supported 12 Languages (`SupportedLanguage.supportsOcr = true`):

| Script | Language |
|--------|----------|
| Japanese | 日本語 (`ja`) |
| Chinese | 中文 (`zh`) |
| Korean | 한국어 (`ko`) |
| Latin | English (`en`) / Español (`es`) / Français (`fr`) / Deutsch (`de`) / Italiano (`it`) / Português (`pt`) / Polski (`pl`) / Nederlands (`nl`) / Türkçe (`tr`) |

`ru` / `ar` / `th` are excluded from language selection options as no corresponding OCR models are available in ML Kit. No changes to PDF/text file input paths (language identification is handled by `LanguageIdentifier`, so language selection UI is unnecessary).

**During Analysis (Button Replacement, Input Disabled):**
```
┌─────────────────────────────────┐
│  Paperwork Navigator            │
│  Please Select a Document       │
├─────────────────────────────────┤
│                                 │
│  ┌───────────────────────────┐  │
│  │  📄 ○○○.pdf              │  │  ← Selected filename
│  └───────────────────────────┘  │
│                                 │
│  ⟳ Extracting key items...      │  ← Current step
│  [========>      ] 60%          │
│  Gemma 4 (On-Device)            │
│                                 │
└─────────────────────────────────┘
```

**On Error (Retry Available):**
```
│  ⚠️ Analysis Failed              │
│  JSON Parse Error               │
│        [Retry]                  │
```

#### S-02 Review Screen

Screen scrolls vertically. From top to bottom: "Translation Bar", "Review (Original Only or Original & Translation Parallel)", "PII", "Chat" sections.

**Before Translation:**
```
┌────────────────────────────────────────┐
│  [←]  Child Allowance Status Report    │
├────────────────────────────────────────┤
│  🌐 [English ▼]  [Translate]          │  ← Translation Bar
├────────────────────────────────────────┤
│  Original                              │  ← 1-Column Display
├────────────────────────────────────────┤
│  ⚠️ Deadline                           │
│  June 30 (Monday)                      │
│                                        │
│  📋 Required Action                    │
│  Fill in and submit status report     │
│  ...
```

**During Translation:**
```
├────────────────────────────────────────┤
│  🌐 ⟳ Translating to English...       │  ← Translation Bar (Progress Display)
├────────────────────────────────────────┤
│  Original only displayed (No translation column)│
```

**After Translation:**
```
┌────────────────────────────────────────┐
│  [←]  Child Allowance Status Report    │
├────────────────────────────────────────┤
│  🌐 English ✓  [Retranslate ▼]        │  ← Translation Bar (Complete)
├───────────────────┬────────────────────┤
│  Original         │  English           │  ← 2-Column Display
├───────────────────┼────────────────────┤
│  ⚠️ Deadline      │  ⚠️ Deadline        │
│  June 30 (Monday) │  June 30 (Mon)     │
│                   │                    │
│  📋 Required      │  📋 Required       │
│  Action           │  Action            │
│  Fill in and      │  Fill in and       │
│  submit report    │  submit report     │
│                   │                    │
│  📎 Required Docs │  📎 Required Docs   │
│  ・Health Ins.    │  ・Health ins.     │
│   Card            │   card             │
│  ・Personal Seal  │  ・Personal seal   │
│                   │                    │
│  ⚠️ Warning       │  ⚠️ Warning         │
│  Failure to       │  Failure to submit │
│  submit will      │  will stop         │
│  stop payments    │  payments          │
├───────────────────┴────────────────────┤
│  [📅 Add Deadline to Calendar] [🗺 View Map]│  ← Quick Actions
├────────────────────────────────────────┤
│  💬 Ask Gemma 4                        │
│  ┌──────────────────────────────────┐  │
│  │ 🤖 Hello. Feel free to ask me    │  │  ← AI Initial Message
│  │    anything about this document. │  │
│  │                                  │  │
│  │          👤 What happens if I    │  │  ← User
│  │             miss the deadline?   │  │
│  │                                  │  │
│  │ 🤖 If you fail to submit by the │  │  ← AI Response
│  │    deadline, your payments will  │  │
│  │    be suspended the following    │  │
│  │    month. After 2 years of no    │  │
│  │    submission, eligibility       │  │
│  │    expires.                      │  │
│  │                                  │  │
│  │                ⟳ Generating...  │  │  ← Streaming
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────┬────┐  │
│  │ Type your question here...   │ ▶  │  │
│  └──────────────────────────────┴────┘  │
├────────────────────────────────────────┤
│  [Create Inquiry Document]              │
└────────────────────────────────────────┘
```

**Quick Actions:**

| Button | Intent | Display Condition |
|--------|--------|-------------------|
| 📅 Add Deadline to Calendar | `CalendarContract.Events.INSERT` (All-Day Event, Event Name: `{doc_name} - Submission Deadline`, Description: `deadline.note_ja`) | `deadline.date != null` |
| 🗺 View Map | `geo:0,0?q={query}` (`location.address_ja` priority, else `location.name_ja`) | `location.name_ja != null` or `location.address_ja != null` |

Hide buttons if corresponding data is absent. Catch `ActivityNotFoundException` if no app can handle the intent, preventing crashes.
Intent generation is centralized in `util/DocumentIntentBuilder.kt`.

**Translation Bar Behavior:**

| State | Display |
|-------|---------|
| Not Translated | Language selection dropdown (Default: English) + "Translate" button |
| Translating (`isTranslating = true`) | `⟳ Translating to {Language}...` (Dropdown & button disabled) |
| Translated | `{Language} ✓` + "Retranslate ▼" dropdown (Can retranslate to different language) |

**Translation Target Language Code List (`selectedLanguage` value):**

| UI Display | Language Code | In LLM Prompt |
|-----------|---------------|---------------|
| 日本語 | `ja` | Japanese |
| English | `en` | English |
| 中文 | `zh` | Chinese (Simplified) |
| 한국어 | `ko` | Korean |
| Español | `es` | Spanish |
| Français | `fr` | French |
| Deutsch | `de` | German |
| Italiano | `it` | Italian |
| Português | `pt` | Portuguese |
| Русский | `ru` | Russian |
| Polski | `pl` | Polish |
| Nederlands | `nl` | Dutch |
| العربية | `ar` | Arabic |
| ภาษาไทย | `th` | Thai |
| Türkçe | `tr` | Turkish |

> The `{target_language}` in LLM prompts should be filled with the "In LLM Prompt" value, not the code. Internally in the app, always use language codes and convert when generating prompts. English is the main validation target in MVP.

**Chat Language:**
- Before Translation: Chat initialized with document's original language (`reviewResult.sourceLanguage`)
- After Translation: Chat session **re-initialized** in translation language (history cleared). Notify with snackbar: "Translation complete. Chat has been reset."
- Chat Language = `reviewResult.translation?.language ?: reviewResult.sourceLanguage`

> **"Create Inquiry Document" Button Tap Behavior:**
> - **No Translation**: Call `startInquiryWizard()` with `sourceLanguage` as `targetLanguage` without dialog, navigating to S-04.
> - **With Translation**: Display 2-choice dialog. Choice 1 is original language (`reviewResult.sourceLanguage`), Choice 2 is translation language (`reviewResult.translation.language`). Display each language name with `SupportedLanguage.displayName` inside the dialog. After selection, call `startInquiryWizard(targetLanguage = selectedLanguage)`. On cancel, stay on S-02.
>
> Chat input remains available on S-02 after S-04 navigation if returning. Disable button when chat is generating (`chatIsGenerating = true`).

**Color-Coding Rules:**

| Color | Meaning |
|-------|---------|
| Red Badge | Deadline · Warning (`severity: high`) |
| Orange Badge | Required Items · Actions |
| Blue Badge | Documents · Contact Info |
| Gray | Notes · Supplementary |
| Yellow Highlight | PII Candidate (Tap to Deselect) |

#### S-03 Inquiry Context Confirmation Screen

Display information collected by the wizard as structured context text. No LLM calls required—immediate display. User copies this text and pastes it into external AI assistants (Claude.ai / ChatGPT, etc.) to request inquiry document generation.

```
┌─────────────────────────────────┐
│  [←]  Inquiry Context           │
├─────────────────────────────────┤
│  Recipient: Edogawa City Hall   │
│            Child Support Dept   │
│  Purpose: Confirm Deadline      │
├─────────────────────────────────┤
│  ┌───────────────────────────┐  │
│  │ 📄 Document: FY2025       │  │
│  │    Child Allowance Status │  │
│  │    Report                 │  │
│  │                           │  │
│  │ Overview: Status report   │  │
│  │ required annually in June.│  │
│  │ Deadline is June 30, 2025.│  │
│  │                           │  │
│  │ Required Action:          │  │
│  │ 1. Fill in and submit     │  │
│  │                           │  │
│  │ Warning:                  │  │
│  │ - Failure stops payments  │  │
│  │   (High)                  │  │
│  │                           │  │
│  │ ---                       │  │
│  │ Inquiry Purpose:          │  │
│  │ Confirm Deadline          │  │
│  │                           │  │
│  │ Q: Did you submit the     │  │
│  │    same document last     │  │
│  │    year?                  │  │
│  │ A: Yes                    │  │
│  │                           │  │
│  │ ※ Personal info masked    │  │
│  └───────────────────────────┘  │
│                                 │
│  [📋 Copy]  [↑ Share]          │
└─────────────────────────────────┘
```

- **Copy**: Copy `InquiryContext.toContextText()` output to clipboard. Display snackbar on completion
- **Share**: Launch Android share sheet (`ACTION_SEND`), letting user choose destination

#### S-04 Inquiry Document Creation Screen

**Basic Information Input**

```
┌─────────────────────────────────┐
│  [←]  Create Inquiry Document   │
├─────────────────────────────────┤
│  📧 Inquiry Purpose             │
│  ┌───────────┐ ┌──────────────┐ │
│  │ Confirm   │ │How to Write   │ │  ← LLM Candidate Buttons
│  │ Deadline  │ │Document       │ │
│  └───────────┘ └──────────────┘ │
│  ┌──────────────────────────────┐│
│  │ Or enter freely...           ││  ← Free Text Input
│  └──────────────────────────────┘│
│                                 │
│  📮 Recipient                  │
│  Org Name: [Edogawa City Hall  ]│
│           [Child Support Dept] │
│  Contact: [                   ] │
│  Email:   [                   ] │
│  Phone:   [                   ] │
│                                 │
│  🔒 Mask Personal Information    │
│  ■ Applicant name [Applicant n.]│  ← Tier 1: Checked by default (Masked)
│  □ Issuer name [Edogawa City Hall]│  ← Tier 2: Unchecked by default (Show Value)
│  □ Issuer address [Edogawa,]   │ ← Tier 2: Tap to check & mask
│                                 │
│  [Confirm Context]              │
└─────────────────────────────────┘
```

> **Language Selection Happens at S-02 Button Tap**: S-04 has no language selection UI. Inquiry language is confirmed when "Create Inquiry Document" button is tapped on S-02 and passed to S-04 as `InquiryWizard.targetLanguage` (See §3.3 S-02).

**Purpose Candidate Behavior:**
- LLM begins generating purpose candidates simultaneously with `InquiryWizard` transition (`purposeSuggestionsLoading = true`)
- Show spinner while generating. Display candidate buttons after completion
- If candidate generation fails, hide buttons and display free text input only

**Recipient Auto-Fill:**
- Preset `ReviewResult.issuer` to organization name and each field of `ReviewResult.contact` to corresponding form
- All fields can be manually overwritten

---

## 4. Processing Flow

### 4.1 Overall Flow

```
Input (Text or PDF or Image)
  │
  ├─[PDF / Text]──► [MF-01a] TextExtractor.extract()
  │                   Returns text string
  │
  └─[Camera / Gallery Image]
      │
      ├─(Camera) GmsDocumentScanner → JPEG
      └─(Gallery) Image Selection → Perspective Correction (Optional)
          │
          ▼
         [MF-01b] ImageTextExtractor.extract() / extractFromBitmap()
           ML Kit Japanese OCR returns text string
           Timeout: 30 seconds
          │
          ▼
         [MF-01c] OcrCorrector.correct(ocrText, images)  ← Gemma 4 Multimodal Inference (New)
           Compare OCR text and original images, get replacement pairs for errors, apply them
           Timeout: 60 seconds. On failure, use ocrText as-is (no crash)
  │
  ▼ (For MF-01a, skip MF-01c and merge below)
LanguageIdentifier.identify(text.take(500))              ← ML Kit Language ID (< 1 sec)
  Returns source_language (BCP-47 code). Low confidence → "und"
  │
  ▼
[MF-02] FieldExtractor.extract(text, sourceLanguage)     ← Gemma 4 Inference #1 (9 Fields)
  Returns ReviewResult (issuerName / applicantName / otherName, etc.)
  │
  ▼
EntityAnnotator.annotate(model, entities, issuerName, applicantName, otherName)  ← Gemma 4 Inference #2
  Assign context_label to DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY
  │
  ▼
EntityExtractor.mergeEntities(gemmaResult, annotatedEntities)
  Derive deadline / docDate / issuerAddress / locations / eventDates from context_label
  Collect entities with piiTier ∈ {1,2} into piiSpans
  │
  ├──► [MF-05] PiiMasker.mask(sourceText, piiSpans)      ← Executable immediately after mergeEntities
  │      Returns masked text (no translation needed, no LLM)
  │
  ├──► [MF-04] Display Review Screen (S-02) ← After MF-01/02/08/05 Completion
  │      Render UI in source language
  │
  ├──► [MF-07] DocumentChatSession.initialize(language=sourceLanguage)
  │      Set ReviewResult as system context in source language
  │      Call sendMessage() for each user action
  │
  ├──► [MF-03] Translator.translate(reviewResult, lang)  ← When User Taps "Translate" on S-02
  │      Add translation fields to ReviewResult.translation
  │      After completion: Update S-02 to 2-column display
  │                       Re-initialize DocumentChatSession in translation language (clear history)
  │
  └──► [MF-06] InquiryContextBuilder (S-04 Wizard) ← After "Create Inquiry Document" Button Press
                Display S-04 with:
                  ・Suggest purpose candidates (Inference #3)
                  ・Generate context text (no LLM, immediate)
                Return InquiryContext (context text) → Navigate to S-03
```

### 4.2 DocumentReviewViewModel State Transitions

S-01 UI monitors `uiState` and switches button to progress display during `Processing`.
When `Review` is reached, navigate to S-02 (chat session re-initialized in source language), when `InquiryWizard` is reached navigate to S-04, and when `InquiryPreview` is reached navigate to S-03.
When translation completes, re-initialize chat session in translation language. Disable "Create Inquiry Document" button while `chatIsGenerating = true`.
"Create Inquiry Document" button tap on S-02 finalizes language and passes it to `startInquiryWizard(targetLanguage)`. If translation exists, pass through a 2-choice dialog (See §3.3 S-02).

```kotlin
sealed class DocumentReviewUiState {
  // S-01: Waiting
  data object Idle : DocumentReviewUiState()

  // S-01: Analyzing (Switch button to progress display)
  data class Processing(
    val step: ProcessingStep,
    val progress: Float,      // 0.0–1.0
    val partialFields: List<Pair<String, String>> = emptyList(), // MF-02 streaming partial display
  ) : DocumentReviewUiState()

  // S-01: Error (Retry available)
  data class Error(@StringRes val messageRes: Int) : DocumentReviewUiState()

  // S-02: Review Screen
  data class Review(
    val reviewResult: ReviewResult,
    val piiSpans: List<PiiSpan>,               // PII span list built by mergeEntities
    val sourceText: String = "",               // Hold for masking (Tier-1, not displayed in UI)
    val selectedLanguage: String = "en",       // Translation bar selected language (before & after translation)
    val isTranslating: Boolean = false,        // MF-03 Running
    val translationError: Boolean = false,     // MF-03 Failure Flag (show error in translation bar)
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatIsGenerating: Boolean = false,     // Chat answer streaming
    val partialChatResponse: String? = null,   // Streaming response (UI only)
    @StringRes val chatErrorRes: Int? = null,  // Chat error message
    val chatLimitReached: Boolean = false,     // Chat limit reached flag
    val chatAvailable: Boolean = true,         // false if session init failed
  ) : DocumentReviewUiState()
  // reviewResult.translation is null → Not translated (original only, 1-column)
  // reviewResult.translation != null → Translated (2-column, chat in translation language)

  // S-02 → S-03 (Old format escalation. Generating escalation)
  data class GeneratingEscalation(
    val piiSpans: List<PiiSpan>,               // PII span list built by mergeEntities
    val reviewResult: ReviewResult,
    val sourceText: String,
    val userNotes: String,
    val chatMessages: List<ChatMessage>,
  ) : DocumentReviewUiState()

  // S-03 (Old format escalation): EscalationPackage Confirmation · Copy · Share
  data class OutputPreview(val pkg: EscalationPackage) : DocumentReviewUiState()

  // S-04: Inquiry Document Creation Wizard
  data class InquiryWizard(
    val reviewResult: ReviewResult,
    val piiSpans: List<PiiSpan>,               // All PII spans (mask selection source)
    val purposeSuggestions: List<String> = emptyList(),
    val purposeSuggestionsLoading: Boolean = false,
    val userPurpose: String = "",
    val recipient: InquiryRecipient,
    val targetLanguage: String,
    val maskedPiiSpans: List<PiiSpan> = emptyList(),  // Checked (masked) spans by user
  ) : DocumentReviewUiState()

  // S-03: Inquiry Context Confirmation · Copy (No LLM calls, immediate)
  data class InquiryPreview(
    val contextText: String,
  ) : DocumentReviewUiState()
}

enum class ProcessingStep(@StringRes val labelRes: Int) {
  EXTRACTING_TEXT(R.string.doc_review_step_extracting_text),    // MF-01
  EXTRACTING_FIELDS(R.string.doc_review_step_extracting_fields), // MF-02 (PII extracted simultaneously in EXTRA_PII field)
  // Translation (MF-03) is user-triggered from S-02 translation bar, not in Processing
}
```

> **Serial LLM Inference Execution**: MF-02, MF-03, MF-06a, and MF-07 inferences execute mutually exclusively. `DocumentReviewViewModel` controls with internal `Mutex`, ensuring multiple inferences are never issued simultaneously. Disable "Create Inquiry Document" button while chat is generating (`chatIsGenerating = true`). During `Processing` state (MF-02/03), both chat input and inquiry button are hidden, so no conflict occurs. Navigation to `InquiryPreview` involves no LLM calls, so no `Mutex` needed. MF-01c (OCR Correction) is user-triggered via `Mutex` and controlled with `correctOcr()` / `cancelCorrection()`.

### 4.3 Error Handling

| Error | Handling |
|-------|----------|
| PDF has no text layer | Display "This PDF cannot be read for text" on S-01 |
| OCR Failure (`ExtractionError.OcrFailed`) | Display "Could not read text from image" on S-01. Retry available |
| OCR Timeout (30 seconds) | Treat as `ExtractionError.OcrFailed`, display same message |
| MF-02 Gemma 4 Inference Timeout (150 seconds) | Display "Analysis failed. Please retry" |
| MF-06 Purpose Candidate Generation Failure (Inference #3) | Hide candidate buttons, display free text input only |
| MF-02 Line Format Parse Error | Retry maximum 2 times (3 total attempts). On failure, display error |
| Model Not Downloaded | Redirect to ModelManager screen |
| Input Text Exceeds Limit | Display "Document too long (8,000 character limit)" |
| MF-03 Translation Failure | Set `isTranslating` to false, display "Translation failed. Please retry" in translation bar. Maintain Japanese-only display |
| MF-07 Chat Inference Timeout (20 seconds) | Display "Answer generation failed. Try again" above chat input. Do not add failed assistant message to history |
| MF-07 Chat Session Init Failure | Hide chat section, keep S-02 review and inquiry features usable |

---

## 5. Data Models (JSON Schema)

### 5.1 ReviewResult (MF-02 + mergeEntities Output)

Complete definition of data class for FieldExtractor (Gemma 4 MF-02) outputting 9 fields in line format, which after `context_label` assignment by EntityAnnotator, mergeEntities() completes with ML Kit entity-derived fields.

```kotlin
@Serializable
data class ReviewResult(
    @SerialName("doc_name") val docName: String,           // Document Title (MF-02)
    @SerialName("doc_date") val docDate: String? = null,   // Document Issue Date (from document_date entity)
    @SerialName("issuer_name") val issuerName: String? = null, // Issuing Org/Officer (MF-02)
    @SerialName("applicant_name") val applicantName: String? = null, // Recipient Person (MF-02)
    @SerialName("other_name") val otherName: String? = null,         // Other Person (MF-02)
    val importance: String,                                // "high" | "medium" | "low" (MF-02)
    @SerialName("summary_ja") val summaryJa: String,       // Summary in Source Language (MF-02)
    val deadline: DeadlineInfo,                            // Derived from deadline entity
    @SerialName("issuer_address") val issuerAddress: String? = null, // From issuer_address entity
    val locations: List<LocationEntry> = emptyList(),      // From issuer_address + other_address entities
    @SerialName("action_items") val actionItems: List<ActionItem> = emptyList(), // MF-02
    @SerialName("required_items") val requiredItems: List<RequiredItem> = emptyList(), // MF-02
    val warning: Warning? = null,                          // MF-02 (Most important warning only)
    @SerialName("event_dates") val eventDates: List<EventDate> = emptyList(), // From event_date entity
    val translation: Translation? = null,                  // Added after MF-03
    @SerialName("source_language") val sourceLanguage: String = "ja", // BCP-47 Language Code
    @SerialName("detected_entities") val detectedEntities: List<DetectedEntity> = emptyList(), // ML Kit All 11 Types
)

// Contact info derived from detectedEntities as extension functions
fun ReviewResult.issuerPhone(): String? =
    detectedEntities.firstOrNull { it.contextLabel == "issuer_phone" }?.rawText
fun ReviewResult.issuerEmail(): String? =
    detectedEntities.firstOrNull { it.contextLabel == "issuer_email" }?.rawText

@Serializable
data class DeadlineInfo(
    val date: String? = null,                              // ISO 8601 Format or null
    @SerialName("note_ja") val noteJa: String? = null,    // deadline entity rawText
)

@Serializable
data class LocationEntry(
    @SerialName("name_ja") val nameJa: String? = null,
    @SerialName("address_ja") val addressJa: String? = null,
)

@Serializable
data class ActionItem(
    val id: String,
    @SerialName("description_ja") val descriptionJa: String = "",
    val priority: Int = 1,
)

@Serializable
data class RequiredItem(
    val id: String,
    @SerialName("name_ja") val nameJa: String = "",
    @SerialName("note_ja") val noteJa: String? = null,
)

@Serializable
data class Warning(
    val id: String,
    @SerialName("description_ja") val descriptionJa: String = "",
    val severity: String = "medium",                       // "high" | "medium" | "low"
)

@Serializable
data class EventDate(
    val date: String? = null,
    @SerialName("description_ja") val descriptionJa: String = "",
)

@Serializable
data class DetectedEntity(
    val type: String,                                      // "PHONE" | "EMAIL" | "ADDRESS" | "DATE_TIME" | "URL" | "MONEY" | "IBAN" | "PAYMENT_CARD" | "FLIGHT_NUMBER" | "ISBN" | "TRACKING_NUMBER"
    @SerialName("raw_text") val rawText: String,
    @SerialName("context_label") val contextLabel: String? = null, // Set by EntityAnnotator or Static
    @SerialName("pii_tier") val piiTier: Int? = null,             // Statically Derived by computePiiTier()
    val metadata: EntityMetadata? = null,
)

@Serializable
data class EntityMetadata(
    val timestampMillis: Long? = null,   // DATE_TIME
    val granularity: String? = null,     // DATE_TIME
    val currency: String? = null,        // MONEY
    val integerPart: Long? = null,       // MONEY
    val ibanCountryCode: String? = null, // IBAN
    val cardNetwork: String? = null,     // PAYMENT_CARD
    val carrier: String? = null,         // TRACKING_NUMBER (ML Kit ParcelCarrier int toString)
    val airlineCode: String? = null,     // FLIGHT_NUMBER (IATA 2-letter code)
)
```

> **`_ja` Suffix**: Actually holds text in the document's source language (`sourceLanguage`). Not limited to Japanese documents.
>
> **PiiSpan Management**: PII spans are not fields within `ReviewResult` but returned by `mergeEntities()` as `Pair<ReviewResult, List<PiiSpan>>`. Built from all `DetectedEntity` with `piiTier ∈ {1, 2}` and `applicantName` / `otherName`. Details in §5.3 and [Extraction Architecture Specification §7.2](抽出アーキテクチャ仕様書.md).
>
> **context_label and PII Tier Details**: See [Extraction Architecture Specification](抽出アーキテクチャ仕様書.md) §2–§4.

### 5.2 Translation (MF-03 Additional Fields)

Set after MF-03 completion via `reviewResult.copy(translation = ...)`.

```kotlin
@Serializable
data class Translation(
    val language: String,                                       // Translation target language code (e.g., "en", "zh")
    val summary: String,
    @SerialName("deadline_note") val deadlineNote: String? = null,
    @SerialName("action_items") val actionItems: List<TranslatedActionItem> = emptyList(),
    @SerialName("required_items") val requiredItems: List<TranslatedRequiredItem> = emptyList(),
    val warning: TranslatedWarning? = null,                    // Single warning (corresponds to Warning)
)

@Serializable
data class TranslatedActionItem(val id: String, val description: String)

@Serializable
data class TranslatedRequiredItem(val id: String, val name: String, val note: String? = null)

@Serializable
data class TranslatedWarning(val description: String)
```

### 5.3 MaskResult / PiiSpan (MF-05 Output)

Generated in-app without LLM. `PiiSpan` returned by `FieldExtractor.extract()` alongside `ReviewResult` and passed to `PiiMasker.mask()`.

```kotlin
data class MaskResult(
    val maskedText: String,             // Text After Masking
    val appliedSpans: List<PiiSpan>,    // Spans Actually Masked
    val skippedSpans: List<PiiSpan>,    // Spans Not Masked (userOverride=false or maskRecommended=false)
    val unmatchedSpans: List<PiiSpan>,  // Spans Not Matched in Original Text (UI Notification)
)

@Serializable
data class PiiSpan(
    val id: String,
    @SerialName("span_text") val spanText: String = "",
    val category: String = "other",    // "name" | "address" | "phone" | "account" | "dob" | "id_number" | "other"
    @SerialName("source_field") val sourceField: String? = null, // "applicant_name" / "issuer_name" / "other_name" / contextLabel, etc.
    @SerialName("mask_recommended") val maskRecommended: Boolean = true,
    @SerialName("user_override") val userOverride: Boolean? = null, // true=Force Mask, false=Exclude, null=Default
)

// Category label multilingual display via categoryLabel(lang: String) extension function
// fun PiiSpan.categoryLabel(lang: String): String { ... }

// Mask Token: Returns "[Applicant name]" etc. based on sourceField. null → "[■■■]"
// fun PiiSpan.maskToken(): String { ... }
```

**PiiSpan Construction Source and `maskRecommended` Setting Rules:**

| Source | Tier | `maskRecommended` |
|--------|------|-------------------|
| `applicantName` (FieldExtractor output) | Tier 1 | `true` |
| `otherName` (FieldExtractor output) | Tier 2 | `false` |
| `issuerName` (FieldExtractor output) | Tier 2 | `false` |
| ML Kit Entity (`piiTier == 1`) | Tier 1 | `true` |
| ML Kit Entity (`piiTier == 2`) | Tier 2 | `false` |

- ML Kit Entity → `sourceField = entity.contextLabel` (e.g., `"issuer_address"`, `"applicant_phone"`, etc.)

### 5.4 ChatMessage (MF-07)

```kotlin
enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
  val id: String,                                    // UUID
  val role: ChatRole,
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
)
```

### 5.5 InquiryContext (MF-06 Output)

Inquiry context collected by wizard. No LLM-based document generation—output as structured text. User intended to paste into external AI assistant to request inquiry document generation.

```kotlin
data class InquiryContext(
  val language: String,                              // Language Code (en, ja, zh, ko)
  val recipient: InquiryRecipient,                   // Recipient
  val purpose: String,                               // Inquiry Purpose (User Input)
  val documentSummary: String,                       // Summary Text Generated from ReviewResult
  val maskedPiiSpans: List<PiiSpan>,                 // PII Spans User Chose to Mask (S-04 Checked)
  val allPiiSpans: List<PiiSpan>,                    // All PII Spans (Preserved to Compute Unmasked Spans)
  val reviewResult: ReviewResult,                    // Original ReviewResult (for toContextText())
  val maskedSourceText: String = "",                 // Output of PiiMasker.mask() (Tier 2 Data)
)

data class InquiryRecipient(
  val organizationName: String,
  val contactName: String? = null,
  val email: String? = null,
  val phone: String? = null,
)
```

`InquiryContext.toContextText(): String` generates structured text for clipboard copy and `ACTION_SEND` intent.

**`toContextText()` Output Format:**

```
{intro_text}           ← Consultation-style text. Multilingual per language

━━━━━━━━━━━━━━━━━━━━━━━━

Inquiry Purpose: {purpose}
Recipient: {organizationName}

Sender Info (Optional):    ← Omit section if unmasked spans from (allPiiSpans - maskedPiiSpans) are empty
- {label}: {span_text}
...

---

📄 Document: {doc_name}
Importance: {importance}

📝 Overview
{summary}

📅 Deadline
{deadline_note}        ← Omit section if deadline is null

✅ Required Actions
1. {action_item_1}
...

📎 Required Documents  ← Omit if empty
- {required_doc_1} ({note})
...

⚠️ Warning             ← Omit if empty
- {warning_1} ({severity})
...

※ Personal information masked

━━━━━━━━━━━━━━━━━━━━━━━━
📄 Original (Personal Info Masked) ← Omit section if maskedSourceText is empty
Cross-check above extracted content; correct if incomplete or erroneous.

{masked_source_text}
```

**Content Field Language Selection Logic:** Header and label fields along with content fields are all based on `InquiryContext.language`. Translated fields (summary, action_items, required_items, warning, deadline_note) use the translated version only if `reviewResult.translation` exists and `translation.language == InquiryContext.language`; otherwise use source fields (`summaryJa`, etc.). This prevents translation fields from mixing in when source language is selected.

Output unmasked PII spans (`allPiiSpans` minus `maskedPiiSpans`) as sender info. Do not include `spanText` of masked spans in this text.

All headers and labels are multilingual per `InquiryContext.language` (`ja` / `en` / `zh` / `ko` / `es` / `fr` / `de` / `it` / `pt` / `ru` / `pl` / `nl` / `ar` / `th` / `tr` supported; unsupported languages fallback to Japanese).

---

## 6. Gemma 4 Prompt Design

For complete prompt text, variable definitions, and evaluation criteria for each LLM call, refer to the **[Prompt Specification](プロンプト仕様書.md)**. This section documents approach and overview necessary for implementation decisions.

### 6.1 Design Approach

- System Prompt written in English (leverage LLM multilingual capability to maintain uniform output quality across documents of all languages)
- Pass document text (any language) in user message, fix output to **line format (Key-Value lines)** (MF-02 / MF-03 common)
- Real-device validation confirmed LiteRT-LM 0.11.0 Kotlin API has no Constrained Decoding, and complex nested JSON exhibits key-as-value collapse. Adopt line format for both MF-02 and MF-03
- Include few-shot example to stabilize accuracy
- Context length limit: trim input text to 8,000 characters maximum (§9.4)

### 6.2 Prompt Overview per MF

| Inference | Input | Output | Notes |
|-----------|-------|--------|-------|
| MF-02 FieldExtractor | Document Text (≤8,000 chars) | Line Format (16 Fields) → ReviewResult + List\<PiiSpan\> | Retry max 2 times on line format parse error |
| MF-03 Translator | ReviewResult `_ja` Fields (Source Language Text) | Line Format (5 Fields) → Translation | Do not translate PiiSpan, id fields |
| MF-06a InquiryContextBuilder (Purpose Candidates) | ReviewResult Summary · action_items | Purpose Candidate List (JSON array) | Fallback to empty list on failure |
| MF-07 DocumentChatSession | ReviewResult Structured Fields (Excluding PII Raw Text) | Chat Response (Streaming) | System Prompt excludes PII spanText |

---

## 7. Processing Steps (Skill) Interface

Each processing step is implemented as an independent class in the `processing/` package.
`DocumentReviewViewModel` orchestrates and calls each step sequentially.

### 7.1 TextExtractor (MF-01a)

```kotlin
object TextExtractor {
  /**
   * Extract text from PDF or text file URI.
   * Text-layer PDF: Extract per page with PdfRenderer.
   * Text file: Read via InputStream.
   *
   * @throws ExtractionError.NoPdfTextLayer if no text layer
   */
  suspend fun extract(context: Context, uri: Uri): String
}

sealed class ExtractionError : Exception() {
  object NoPdfTextLayer : ExtractionError()
  object UnsupportedFormat : ExtractionError()
  data class IoError(override val cause: Throwable?) : ExtractionError()
  object OcrFailed : ExtractionError()
}
```

**Implementation Approach:**
- Use `android.graphics.pdf.PdfRenderer` (API 35) `Page.getPageContent()` to extract text
- minSdk = 35 requires no API branching
- Join pages with `\n\n`
- Extraction result limit: 8,000 characters (prioritize beginning if exceeded)

### 7.2 ImageTextExtractor (MF-01b)

```kotlin
object ImageTextExtractor {
  /**
   * Extract text from image file URI using ML Kit OCR.
   * Switch OCR model per sourceLanguage.
   * Support camera scan results (JPEG URI) via GmsDocumentScanner.
   *
   * @param sourceLanguage BCP-47 Language Code ("ja", "zh", "ko", "en", etc.)
   * @throws ExtractionError.OcrFailed on OCR failure or timeout (30 seconds)
   */
  suspend fun extract(context: Context, uri: Uri, sourceLanguage: String): String

  /**
   * Run OCR directly from Bitmap.
   * Used after perspective correction (perspective correction) of gallery images.
   *
   * @param sourceLanguage BCP-47 Language Code ("ja", "zh", "ko", "en", etc.)
   * @throws ExtractionError.OcrFailed on OCR failure or timeout (30 seconds)
   */
  suspend fun extractFromBitmap(bitmap: Bitmap, sourceLanguage: String): String
}
```

**Supported Languages and OCR Models (12 Languages):**

| Script | Language Code | ML Kit Model |
|--------|---------------|-------------|
| Japanese | `ja` | `JapaneseTextRecognizerOptions` |
| Chinese | `zh` | `ChineseTextRecognizerOptions` |
| Korean | `ko` | `KoreanTextRecognizerOptions` |
| Latin | `en`, `es`, `fr`, `de`, `it`, `pt`, `nl`, `pl`, `tr` | `TextRecognizerOptions.DEFAULT_OPTIONS` |

`ru` / `ar` / `th` excluded from language selection UI on camera/gallery input as no corresponding OCR models available in ML Kit (`SupportedLanguage.supportsOcr = false`).

**Implementation Approach:**
- `resolveRecognizerOptions(sourceLanguage)` selects `TextRecognizerOptionsInterface` from language code
- `Tasks.await()` waits maximum 30 seconds. Timeout throws `ExtractionError.OcrFailed`
- Join text blocks with `\n`
- Extraction result limit: 8,000 characters (share `TextExtractor.MAX_CHARS`)
- Empty (blank) extraction also throws `ExtractionError.OcrFailed`

**Camera Input Flow (S-01):**
1. User taps "Take Photo" → Launch `GmsDocumentScanner` (SCANNER_MODE_FULL)
2. After scan completion, get JPEG URIs for all pages → Display language selection dialog (12 languages)
3. After language selection → OCR each URI sequentially with `ImageTextExtractor.extract(context, uri, selectedOcrLanguage)`, concatenate with `\n\n` and trim with `TextExtractor.MAX_CHARS`
4. Store OCR result in `pastedText`, enable "Start Analysis"

**Gallery Image Input Flow (S-01):**
1. User taps "Choose from Gallery" → Launch image picker (`OpenMultipleDocuments`, JPEG / PNG)
2. Branch per image count selected:
   - **1 Image**: Perspective correction screen (corner drag) → Apply perspective transformation → Display OCR language selection dialog
   - **2+ Images**: Show perspective correction screen sequentially for each image and apply perspective transformation → Display OCR language selection dialog once after all are complete
3. After language selection → OCR each corrected Bitmap sequentially with `ImageTextExtractor.extractFromBitmap(bitmap, selectedOcrLanguage)`, concatenate with `\n\n` and trim with `TextExtractor.MAX_CHARS`
4. Store OCR result in `pastedText`, enable "Start Analysis"

> **Note**: If any image fails OCR, throw `ExtractionError.OcrFailed` and display error.

### 7.3 OcrCorrector (MF-01c)

```kotlin
class OcrCorrector(private val llmHelper: LlmModelHelper) {

  /**
   * Pass OCR text and original images to Gemma 4 multimodal inference,
   * get replacement pairs for errors in line format, apply them, return corrected text.
   * If correction unnecessary or fails, return ocrText as-is.
   * Timeout: 60 seconds
   *
   * @param images Original page images (1 image per page). Skip correction if empty.
   */
  suspend fun correct(model: Model, ocrText: String, images: List<Bitmap>): String
}
```

**Implementation Approach:**
- `llmHelper.resetConversation(supportImage = true, systemInstruction = ...)` initializes image input session
- `llmHelper.runInference(images = images)` passes Gemma 4 page images + OCR text
- Output format: `CORRECT: <wrong>|<right>` lines (1 per line) or `(none)`
- Parse failure/timeout caught and return `ocrText` as-is (no retry)
- Not called for PDF input (`DocumentInput.PdfUri`)

### 7.4 FieldExtractor (MF-02)

```kotlin
class FieldExtractor(private val llmHelper: LlmModelHelper) {

  /**
   * Extract structured information from document text.
   * Call Gemma 4 with JSON structured output.
   *
   * @param onProgress Progress callback for inference (streaming tokens)
   * @return ReviewResult (throws FieldExtractionError on failure)
   */
  suspend fun extract(
    model: Model,
    text: String,
    onProgress: (String) -> Unit,
  ): ReviewResult
}

sealed class FieldExtractionError : Exception() {
  object JsonParseError : FieldExtractionError()
  object ModelNotInitialized : FieldExtractionError()
  data class InferenceError(override val message: String?) : FieldExtractionError()
}
```

**Implementation Approach:**
- Call `llmHelper.runInference()`
- Receive output in stream, parse JSON after completion
- Retry maximum 2 times on parse error (append error info to prompt and retry)

### 7.5 Translator (MF-03)

```kotlin
class Translator(private val llmHelper: LlmModelHelper) {

  /**
   * Translate Japanese fields of ReviewResult to targetLanguage,
   * return ReviewResult with translation field filled.
   */
  suspend fun translate(
    model: Model,
    reviewResult: ReviewResult,
    targetLanguage: String,
  ): ReviewResult
}
```

**Translation Target Fields:**
`summary_ja`, `action_items[].description_ja`, `required_items[].name_ja`,
`required_items[].note_ja`, `warning.description_ja`, `deadline.note_ja`

**Fields Not Translated:**
PiiSpan (span positions change), `id` fields

### 7.6 PiiMasker (MF-05)

```kotlin
object PiiMasker {

  /**
   * Mask PII spans in text. No LLM used.
   * Search original text with span_text, replace matched locations with PiiSpan.maskToken().
   */
  fun mask(text: String, spans: List<PiiSpan>): MaskResult

  /**
   * After user changes span userOverride, re-mask.
   */
  fun remask(text: String, spans: List<PiiSpan>): MaskResult
}
```

**Mask Token:** `PiiSpan.maskToken()` returns labeled token based on `sourceField` (e.g., `[Applicant name]`, `[Issuer address]`). Fallback to `[■■■]` if `sourceField` is null. Allows context understanding from masked text (e.g., `Recipient [Applicant name]`).

**Masking Priority:**
1. `userOverride == true` → Force mask
2. `userOverride == false` → Force exclude
3. `userOverride == null && maskRecommended == true` → Mask (Default)
4. `userOverride == null && maskRecommended == false` → Exclude

**Span Matching:**
- Remove all whitespace from `spanText`, escape each character with `Regex.escape()`, join with `[\s　]*` to generate regex for search (absorb PDF text extraction space variance, full-width spaces. Regex metacharacters like `.`, `(`, `)`, `+` treated as literals)
- `spanText` becomes empty after whitespace removal (whitespace-only span) → `buildMatchRegex()` returns `null`, caller records in `unmatchedSpans`
- Replace all matches with `span.maskToken()` token
- Record unmatched spans in `unmatchedSpans` to notify user in UI

### 7.7 InquiryContextBuilder (MF-06)

```kotlin
class InquiryContextBuilder(private val llmHelper: LlmModelHelper) {

  /**
   * Suggest inquiry purpose candidates from document content (Inference #3).
   * Return empty list on failure (user switches to free text input).
   */
  suspend fun suggestPurposes(
    model: Model,
    reviewResult: ReviewResult,
    targetLanguage: String,
  ): List<String>

  /**
   * Assemble information collected by wizard into InquiryContext (no LLM calls).
   * Generate context output for toContextText() from ReviewResult + wizard input.
   */
  fun buildContext(
    reviewResult: ReviewResult,
    purpose: String,
    recipient: InquiryRecipient,
    maskedPiiSpans: List<PiiSpan>,  // User-selected masked spans from S-04
    allPiiSpans: List<PiiSpan>,     // All PII spans (compute unmasked spans for sender info)
    targetLanguage: String,
    sourceText: String,             // For PiiMasker.mask() call (internally generate maskedSourceText)
  ): InquiryContext
}
```

### 7.8 DocumentChatSession (MF-07)

```kotlin
class DocumentChatSession(private val llmHelper: LlmModelHelper) {

  /**
   * Set ReviewResult structured data as system context and start chat session.
   * System prompt excludes PII raw text (ReviewResult extracted fields only).
   */
  suspend fun initialize(
    model: Model,
    reviewResult: ReviewResult,
    targetLanguage: String,
  )

  /**
   * Send user message and receive streaming tokens.
   * Maintain chat history internally, include in each request.
   *
   * @param onToken Callback for streaming tokens
   * @return Completed assistant ChatMessage
   */
  suspend fun sendMessage(
    model: Model,
    userMessage: String,
    onToken: (String) -> Unit,
  ): ChatMessage

  /** Return chat history (for passing S-04 Q&A context, etc.) */
  fun getChatHistory(): List<ChatMessage>

  /** Reset session (when loading new document) */
  fun clear()
}
```

**Implementation Approach:**
- Reuse existing chat features of `LlmChatModelHelper`, replace system prompt only
- Disable input field and send button while message sending (`chatIsGenerating = true`)
- Chat history master is managed inside `DocumentChatSession`. ViewModel appends `ChatMessage` returned by `sendMessage()` to `Review.chatMessages` each time for UI update (UI-only display).

### 7.9 LanguageIdentifier (MF-01 Post)

```kotlin
object LanguageIdentifier {

  /**
   * Identify language from first 500 characters of text using ML Kit Language Identification.
   * Return "und" if confidence low.
   *
   * @return BCP-47 Language Code (e.g., "ja", "en", "zh") or "und"
   */
  suspend fun identify(text: String): String
}
```

**Implementation Approach:**
- Generate client with `LanguageIdentification.getClient()`, execute `identifyLanguage()`
- Bridge callback to coroutine with `suspendCancellableCoroutine`
- Manage client lifecycle with `try/finally`, ensure `finally { identifier.close() }` release
- Identification failure, exception → fallback to `"und"` (prevent stopping MF-02)
- Call outside LLM Mutex after MF-01 completion (ML Kit identification uses no LLM resources)

---

## 8. Local Storage

### 8.1 Storage Location

Save in private storage under `context.filesDir`. Do not use external storage.

```
{filesDir}/documents/
└── doc_{yyyyMMdd_HHmmss}_{shortUuid}/
    ├── meta.json           ← ReviewResult + PiiSpan Info (Contains PII)
    ├── source.txt          ← Extracted Text (Contains PII)
    ├── escalation.json     ← EscalationPackage (Masked · User-Executed MF-06 Only)
    └── inquiry.json        ← InquiryContext (May Contain PII User Explicitly Included)
```

### 8.2 Security Approach

- Do not transmit `source.txt` and `meta.json` outside device
- `escalation.json` (EscalationPackage) contains masked data only. App does not auto-send; user chooses destination via share sheet
- `inquiry.json` (InquiryContext) may contain PII user explicitly included in S-04. App does not auto-send; user chooses destination via share sheet
- MVP: No encryption (Phase 2 considers Keystore)

### 8.3 DocumentRepository

```kotlin
class DocumentRepository(private val filesDir: File) {
  suspend fun save(
    docId: String,
    reviewResult: ReviewResult,
    sourceText: String,
  ): Boolean

  suspend fun saveEscalation(docId: String, pkg: EscalationPackage): Boolean
  suspend fun saveInquiry(docId: String, context: InquiryContext): Boolean
  suspend fun list(): List<DocumentMeta>
  suspend fun load(docId: String): DocumentBundle?
  suspend fun delete(docId: String): Boolean
}

data class DocumentMeta(
  val docId: String,
  val docName: String,           // Document Name (ReviewResult.docName)
  val importanceLevel: String,   // "high" | "medium" | "low"
  val createdAt: Long,           // Unix Milliseconds
  val hasEscalation: Boolean,    // escalation.json Exists
  val hasInquiry: Boolean = false,
)

data class DocumentBundle(
  val reviewResult: ReviewResult,
  val sourceText: String,
  val escalationPackage: EscalationPackage?,
  val inquiryContext: InquiryContext?,
)
```

---

## 9. Non-Functional Requirements

### 9.1 Target Device

| Item | Value |
|------|-------|
| Device | Google Pixel 9 |
| RAM | 12 GB |
| OS | Android 15 |
| minSdk | 35 (Android 15 PdfRenderer Text Extraction API Requirement) |

### 9.2 Model Selection

| Model | Size | Description |
|-------|------|-------------|
| Gemma 4 E2B | 2.58 GB | Recommended (Smaller Download) |
| Gemma 4 E4B | 3.65 GB | High-Accuracy Option (Manual Selection in Model Manager) |

### 9.3 Inference Time Estimates (Pixel 9 Expected)

| Step | Estimate |
|------|----------|
| MF-01a Text Extraction (PDF) | < 1 sec |
| MF-01b OCR (ML Kit, Image) | < 5 sec |
| MF-02 Field Extraction (Gemma 4) | 60–150 sec |
| MF-03 Translation (Gemma 4) | 15–60 sec |
| MF-06a Purpose Candidate Generation (Gemma 4) | 5–15 sec |
| MF-06b Q&A Question List Generation (Gemma 4) | 5–15 sec |
| MF-06 Context Text Generation (No LLM) | < 1 sec |
| MF-07 Chat Response (Gemma 4, 1 Turn) | 3–10 sec (Streaming) |

Inference times to be measured on actual device and adjusted.

### 9.4 Character Limits

| Item | Limit | Reason |
|------|-------|--------|
| MF-01 Extracted Text | 8,000 chars | Trim before passing to later stages |
| MF-02 Input Text | 8,000 chars | Gemma 4 actual context limit 32,000 tokens; 8,000 chars ≈ 5,333 tokens + system prompt ~850 tokens = ~6,200 tokens with headroom |
| MF-06c Document Generation Input | 4,000 chars (including Q&A results) | Balance Gemma 4 context length and generation quality |
| MF-07 Chat History | 20 Turns (10 Q&A exchanges) or Cumulative 4,000 chars, whichever first | Prevent exceeding total context length. Display "Chat history limit reached" and disable new input when reached |

### 9.5 Out of Scope for MVP (P2 onwards)

- Web search for policy information complement (OF-10)
- Escalation to Cloud LLM (OF-09)
- Multi-document Cross-Reference Agent RAG (Phase 2)
- Legal advice, policy interpretation
- Encrypted storage
- Multi-user accounts