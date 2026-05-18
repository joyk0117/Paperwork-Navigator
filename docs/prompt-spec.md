# Paperwork Navigator Prompt Specification

> Version: 0.3.2
> Created: 2026-05-07
> Last Updated: 2026-05-19 (MF-01c §2.1 relaxed image-strict constraint to permit high-confidence language-based corrections excluding proper nouns; §2.4 added boundary examples)
> Target: MVP (MF-02 / EntityAnnotator / MF-03 / MF-06 / MF-07)
> Based on: Implementation Specification v0.2.6 / Extraction Architecture Specification v1.0.0

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [MF-01c: OCR Correction Prompt](#2-mf-01c-ocr-correction-prompt)
3. [MF-02: Field Extraction Prompt (9 Fields)](#3-mf-02-field-extraction-prompt-9-fields)
4. [EntityAnnotator: Entity Annotation Prompt](#4-entityannotator-entity-annotation-prompt)
5. [MF-03: Translation Prompt](#5-mf-03-translation-prompt)
6. [MF-06: Inquiry Document Prompt](#6-mf-06-inquiry-document-prompt)
7. [MF-07: Chat System Prompt](#7-mf-07-chat-system-prompt)
8. [Few-shot Examples (MF-02 / EntityAnnotator / MF-03)](#8-few-shot-examplemf-02--entityannotator--mf-03)
9. [Prompt Variable Reference](#9-prompt-variable-reference)
10. [Evaluation Criteria](#10-evaluation-criteria)

---

## 1. Design Principles

- System prompts are written in English (to leverage the LLM's multilingual capabilities and maintain uniform output quality across documents in any language)
- Document text (in any language) is passed in user messages, and output format is selected based on the model's capabilities (see below)
- Stability of accuracy is improved by including few-shot examples
- Context length limit: input text is trimmed to 16,000 characters or less (applied by ViewModel, Specification §9.4)

### 1.1 MF-02 Output Format Selection Rationale

Initially, the plan was to use **function calling (structured output)** of LiteRT-LM to reliably obtain JSON, but on-device verification revealed that **the Kotlin API (LiteRT-LM 0.10.0) does not have JSON Schema / Regex / Grammar-based Constrained Decoding**.

> **Note**: The C++ API of LiteRT-LM implements Grammar-based Constrained Decoding, but as of version 0.10.0, the only publicly available Kotlin API feature is `ExperimentalFlags.enableConversationConstrainedDecoding` (a binary flag), and schema specification is impossible.

Further on-device testing revealed that with long input text (approximately 8,000 characters) combined with complex nested JSON schemas, Gemma 4 E2B exhibits **key-as-value collapse** (e.g., outputting `"importance": "summary_ja": "..."` where a value should appear, with a key name instead).

In response, the MF-02 output format was changed from JSON to **line format (Key-Value lines)**.

| Policy | Content |
|--------|---------|
| Output format | `KEY: value` line format (16 fields fixed) |
| Array delimiter | `\|\|\|` (triple pipe) |
| Subfield delimiter | `\|` (single pipe) |
| JSON assembly | Performed by Kotlin-side `parseLineFormat()`, so the LLM does not need to be aware of structure |
| Fallback | MF-03 also adopts line format for the same reason. MF-06a (inquiry purpose candidates) and MF-06 (escalation) maintain JSON output because the lower number of output fields poses lower collapse risk |

---

## 2. MF-01c: OCR Correction Prompt

A step that corrects ML Kit OCR text using Gemma 4 multimodal inference after camera or gallery image input.
PDF text extraction (MF-01a) skips this step because the original image is not available.

### 2.1 System Prompt

```
You are a precise OCR correction assistant.

The user will provide an image of a document and the OCR text extracted from it.
Compare the image with the OCR text and identify transcription errors.
Output corrections only—no explanation, no markdown fences.

Format:
CORRECT: <wrong_text>|<corrected_text>

Rules:
- Output one CORRECT line per error.
- <wrong_text> must be copied verbatim from the OCR text.
- <corrected_text> should match what appears in the image when the image is clear.
- When the image is partially unclear, you may apply your language knowledge to determine
  the most plausible correction — but only when you are highly confident.
- Do NOT apply language-based corrections to proper nouns (personal names, place names,
  organization names) — these have no single correct form derivable from language rules alone.
- Do not make corrections based solely on stylistic preference or ambiguous context.
- If no errors are found, output: (none)
- Do not correct spacing or line-break differences unless they change meaning.
```

### 2.2 User Message

```
Here is the OCR text extracted from the document image above:

{ocr_text}

List any transcription errors found.
```

### 2.3 Variables

| Variable | Description |
|----------|-------------|
| `{ocr_text}` | Text output by ML Kit OCR (trimmed by `LLM_INPUT_MAX_CHARS`) |

### 2.4 Output Format and Kotlin-side Parsing

Gemma 4 uses one of the following two formats (both are accepted by `OcrCorrector.parseCorrections()`).

**Pipe format (recommended):**
```
CORRECT: 山回太郎|山田太郎
CORRECT: 令利7年|令和7年
(none)
```

**Bracket format (naturally output by the model):**
```
CORRECT:山回太郎<山田太郎>
CORRECT:令利7年<令和7年>
```

- Spaces after `CORRECT:` are optional
- Identity corrections where wrong == right are excluded
- When there are no errors, output `(none)` only
- `OcrCorrector.parseCorrections()` reads the lines and converts them to `List<Pair<String, String>>`
- `OcrCorrector.applyCorrections()` applies `String.replace()` sequentially
- On parse failure, `(none)` output, or timeout, use ocrText as-is (no retry)

**Boundary examples — permitted corrections (non-existent vocabulary / obvious spelling mistakes):**
```
CORRECT: 令利7年|令和7年
CORRECT: administraion|administration
```

**Boundary examples — suppressed corrections (proper nouns / ambiguous cases):**
```
(none)
```

> `令利7年` → `令和7年`: "令利" is not a valid Japanese era name, so correction is highly confident.
> `administraion` → `administration`: clearly wrong spelling with a single unambiguous fix.
> `山回太郎` is NOT corrected to `山田太郎`: "山回" could be a valid family name. Proper nouns must not be corrected by language knowledge alone.

---

## 3. MF-02: Field Extraction Prompt (9 Fields)

Since dates, addresses, phones, emails, and amounts are processed by ML Kit + EntityAnnotator, MF-02 extracts only names, importance, summary, actions, and warnings—9 fields total.

### 3.1 System Prompt

```
You are a precise assistant that extracts structured information from documents.

The user will provide text from a document in any language.
Output lines in the exact format below. Output only the lines—no explanation, no markdown fences.

Format:
DOC_NAME: <exact document title/name as written on the document>
ISSUER_NAME: <name of the organization or person who issued the document or (none)>
APPLICANT_NAME: <name of the specific person the document is addressed to or (none)>
OTHER_NAME: <name of any other person or organization mentioned in the document or (none)>
IMPORTANCE: <high | medium | low>
SUMMARY: <1-2 sentence summary in the document's original language>
ACTION_ITEMS: <description1>|||<description2>|||... or (none)
REQUIRED_ITEMS: <name1>|<note1>|||<name2>|<note2>|||... or (none)
WARNING: <severity>|<description> or (none)

Rules:
- DOC_NAME: extract the exact title or heading as it appears on the document.
- ISSUER_NAME: the organization or person who issued or published the document.
- APPLICANT_NAME: use the person's actual name if stated, not a generic description (e.g. "山田太郎" not "受給者").
- OTHER_NAME: any other named person or organization who is neither the issuer nor the applicant. Use (none) if not found.
- IMPORTANCE is "high" when failure to act causes significant consequences (e.g. suspension of benefits, penalty, legal obligation).
- WARNING: the single most important warning only. severity must be: high | medium | low. Use (none) if no warning exists.
- Use (none) when a field has no value.
- Use ||| to separate multiple items in array fields.
- Use | to separate sub-fields within an item.

Example output:
{few_shot_example}
```

### 3.2 User Message

```
Analyze the following document text:

{document_text}
```

### 3.3 Variables

| Variable | Description | Reference |
|----------|-------------|-----------|
| `{few_shot_example}` | Few-shot example from §8 (line format) | [Few-shot Examples](#8-few-shot-examplemf-02--entityannotator--mf-03) |
| `{document_text}` | Text extracted by TextExtractor (trimmed to 16,000 characters or less) | — |

### 3.4 Retry Policy

On parse failure, retry up to 2 times (3 total attempts). On retry, append error information to the user message.

```
Analyze the following document text:

{document_text}

The previous output caused a parse error: {parse_error_message}
Please output the correct line format again.
```

> Since `LlmChatModelHelper` maintains session context in chat format, invalid output from the previous attempt also accumulates in the context on retry. If the third attempt fails, throw `FieldExtractionError.ParseError` and stop retrying.

### 3.5 Field List (9 Fields)

| Field | Type | Description |
|-------|------|-------------|
| `DOC_NAME` | String | Document title (original as written) |
| `ISSUER_NAME` | String / (none) | Issuer |
| `APPLICANT_NAME` | String / (none) | Recipient person (extract actual name; generic descriptions not allowed) |
| `OTHER_NAME` | String / (none) | Any person or organization other than issuer or applicant |
| `IMPORTANCE` | `high` / `medium` / `low` | Importance level |
| `SUMMARY` | String | 1–2 sentence summary in the document's original language |
| `ACTION_ITEMS` | Description strings × `\|\|\|` / (none) | Required actions |
| `REQUIRED_ITEMS` | `<name>\|<note>` × `\|\|\|` / (none) | Documents/items to bring |
| `WARNING` | `<severity>\|<description>` / (none) | Most critical warning (single) |

> Removed fields (old 16 fields → 9 fields): `DEADLINE_DATE` / `DEADLINE_NOTE` / `DOC_DATE` / `ISSUER_ADDRESS` / `LOCATIONS` / `EVENT_DATES` are derived from `context_label`-annotated entities extracted by ML Kit + EntityAnnotator via `mergeEntities()`. `CONTACT` is derived from `issuer_phone` / `issuer_email` entities via extension functions. `EXTRA_PII` is replaced with static Tier rules (`computePiiTier()`).

---

## 4. EntityAnnotator: Entity Annotation Prompt

A Gemma 4 step that assigns `context_label` to DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY entities extracted by ML Kit based on document context. Executed immediately after FieldExtractor (after `issuer_name` / `applicant_name` / `other_name` are confirmed).

### 4.1 System Prompt

```
You are a precise assistant that labels extracted entities from a document.

The user will provide:
1. Names identified from the document (issuer, applicant, other)
2. A numbered list of entities extracted from the document

Assign a context label to each entity based on its role in the document.
Output one line per entity in the exact format: {index}: {label}
Output only the lines—no explanation, no markdown fences.

Allowed labels per entity type:
- DATE_TIME: deadline | document_date | event_date | date_of_birth | unknown
- ADDRESS:   issuer_address | applicant_address | other_address | unknown
- PHONE:     issuer_phone | applicant_phone | other_phone | unknown
- EMAIL:     issuer_email | applicant_email | other_email | unknown
- MONEY:     benefit_amount | fee | penalty | other_amount | unknown

Rules:
- Use the provided names as anchors to determine ownership.
  Addresses, phones, and emails belonging to issuer_name → issuer_*
  Addresses, phones, and emails belonging to applicant_name → applicant_*
  Addresses, phones, and emails not belonging to either → other_*
- other_* labels may be used even when other_name is (none).
- deadline: the main submission or response deadline for the document.
- document_date: the date the document was issued or created.
- event_date: any other date that is neither a deadline nor a date of birth.
- date_of_birth: a birth date, typically associated with the applicant.
- benefit_amount: money the recipient will receive or is eligible for.
- fee: a required payment or charge.
- penalty: a fine or consequence for non-compliance.
- other_amount: any monetary value that does not fit the above.
- Use unknown when the role cannot be determined from context.

Example:
{few_shot_example}
```

### 4.2 User Message

```
issuer_name: {issuer_name}
applicant_name: {applicant_name}
other_name: {other_name}

{numbered_entities}

Label each entity.
```

### 4.3 Variables

| Variable | Description |
|----------|-------------|
| `{few_shot_example}` | EntityAnnotator few-shot example from §8 (2 examples separated by `===`) |
| `{issuer_name}` | `reviewResult.issuerName ?: "(none)"` |
| `{applicant_name}` | `reviewResult.applicantName ?: "(none)"` |
| `{other_name}` | `reviewResult.otherName ?: "(none)"` |
| `{numbered_entities}` | `"${i+1}. ${entity.type}: ${entity.rawText}"` joined by newlines (DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY only) |

### 4.4 Parse and Fallback Policy

- Output format: `{index}: {label}` per line (1 per line)
- Allowed labels are defined per type in the `ALLOWED_LABELS` map (see §4.1 System Prompt)
- Entities returning `unknown` → `contextLabel = null`
- On parse failure or timeout (60 seconds) → all entities fall back with `contextLabel = null` (no retry)

---

## 5. MF-03: Translation Prompt

### 5.1 System Prompt

```
You are a precise translator.

Translate the provided {source_language} document fields into {target_language}.
Output lines in the exact format below. Output only the lines—no explanation, no markdown fences.

Format:
SUMMARY: <translated summary>
DEADLINE_NOTE: <translated deadline note or (none)>
ACTION_ITEMS: <id1>|<description1>|||<id2>|<description2>|||... or (none)
REQUIRED_ITEMS: <id1>|<name1>|<note1>|||<id2>|<name2>|(none)|||... or (none)
WARNING: <translated description or (none)>

Rules:
- Use plain, clear language that non-native speakers can understand.
- Keep document-specific terms accurate (e.g. 現況届 → "Status Report Form").
- For DEADLINE_NOTE, include the date in a human-readable format.
- Do not translate id fields.
- WARNING: translate the single warning description only—no severity prefix.
- Use ||| to separate multiple items in array fields.
- Use | to separate sub-fields within an item.
- Use (none) when a field has no value.
- Strings enclosed in square brackets such as [Applicant name] or [■■■] are privacy placeholders. Copy them verbatim—do not translate or modify them.

Example output:
{few_shot_example}
```

### 5.2 User Message

```
Translate the following fields from a {source_language} document into {target_language}:

{fields_text}
```

On retry, append error information at the end.

```
Translate the following fields from a {source_language} document into {target_language}:

{fields_text}

The previous output caused a parse error: {parse_error_message}
Please output the correct line format again.
```

### 5.3 Variables

| Variable | Description |
|----------|-------------|
| `{source_language}` | English name of the document's original language (converted from language code identified by `LanguageIdentifier`) |
| `{target_language}` | English name of the target language (see table below) |
| `{fields_text}` | Text of fields to translate (SUMMARY / DEADLINE_NOTE / ACTION_ITEMS / REQUIRED_ITEMS / WARNING enumerated in original language) |
| `{few_shot_example}` | MF-03 few-shot example from §8 |

### 5.5 Language Code to LLM Prompt Text Mapping

| UI Display | Language Code | LLM Prompt Text |
|------------|---------------|-----------------|
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

> The app internally always uses language codes and converts them using the table above when generating prompts.

### 5.6 Fields to Translate and Exclude

**Translation targets (5 fields):**
`summaryJa`, `deadline.noteJa`, `actionItems[].descriptionJa`, `requiredItems[].nameJa`, `requiredItems[].noteJa`, `warning.descriptionJa`

> MF-03 output `WARNING` does not include severity prefix (translated description string only). Severity is retained from the original MF-02 output.

**Do not translate:**
- PiiSpan (span positions would change)
- `id` fields (e.g. `action_01`)
- `warning.severity` (`high`/`medium`/`low` do not require multilingual conversion)

> **Pre-masking**: Before passing the translation target fields to the LLM, apply `PiiMasker.mask()` to replace any PII span text with mask tokens (e.g. `[Applicant name]`). The LLM treats mask tokens as opaque strings and preserves them in the translated output, so translated fields are automatically masked.

---

## 6. MF-06: Inquiry Document Prompt

The S-04 wizard consists of two prompts: MF-06a (inquiry purpose suggestions) and MF-06 (escalation package generation). Context text generation (`InquiryContext.toContextText()`) is performed by the app without using the LLM.

### 6.1 MF-06a: Inquiry Purpose Suggestion

#### System Prompt

```
You are a helpful assistant analyzing a document.
Suggest 3-5 concise inquiry purposes a recipient might have for this document.

If prior conversation with the user is provided, infer what aspects the user is most concerned about
from the conversation. Prioritize purposes that address or extend those concerns.

Output a JSON array of short strings only—no explanation, no markdown fences.
Each string should be 5-15 words in {target_language}.

Example output:
["Confirm the submission deadline", "Clarify required documents", "Ask about payment status"]
```

#### User Message

```
Document: {doc_name}
Summary: {summary}
Required actions: {action_items}

Original document text:
{source_text}

Prior conversation with the user — use this to understand their concerns and suggest purposes aligned with them:
{chat_history}

Suggest inquiry purposes in {target_language}.
```

When chat history is empty (no user messages), omit the "Prior conversation" section entirely. The section is omitted when `chatHistoryToText()` returns an empty string, which occurs when there are no user-role messages in the history (i.e., only the initial greeting exists).

#### Variables

| Variable | Description | Reference |
|----------|-------------|-----------|
| `{target_language}` | English name of target language (use table from §5.5) | — |
| `{doc_name}` | `reviewResult.docName` | — |
| `{summary}` | `reviewResult.translation.summary` or `reviewResult.summaryJa` | — |
| `{action_items}` | Numbered list format (prioritize translated fields) | — |
| `{source_text}` | `DocumentReviewViewModel.sourceText` (raw text extracted by TextExtractor, trimmed to 16,000 characters or less). Passing raw text is acceptable for on-device inference (same as MF-02) | — |
| `{chat_history}` | `DocumentChatSession.getChatHistory()` converted to `Q:` / `A:` format. Omit the section entirely if empty | — |

#### Error Handling

On JSON parse error, fall back to empty list immediately (no retry). Same on timeout (15 seconds).

---

---

> **Regarding MF-06b (missing information question generation) and MF-06c (inquiry document generation)**: MF-06b was removed because on-device verification found it not useful. MF-06c is deferred to P2 due to concerns about document quality in on-device inference (style, honorifics, language mismatch) (Issue #57). The current MF-06 consists of three components: inquiry purpose suggestion (MF-06a), escalation package generation (MF-06), and context text assembly (no LLM required).

### 6.2 MF-06: Escalation Package Generation

Executed when the "Create Handoff File" button is pressed in S-02. Generates consultation summary, timeline, and AI hypotheses based on masked text.

#### System Prompt

```
You are a precise assistant helping prepare a privacy-safe escalation document.
The document has been masked—personal information replaced with descriptive labels
like [Applicant name], [Issuer address], [Date of birth], etc.

Output raw JSON only—no explanation, no markdown fences.

Output schema:
{
  "consultation_summary": "string (2-3 sentences explaining what the user needs help with)",
  "timeline": [{"date": "YYYY-MM-DD or descriptive string", "event": "string"}],
  "ai_hypotheses": [{"point": "string", "type": "hypothesis | unclear"}]
}

Rules:
- consultation_summary: explain the document type, what action is needed, and why
  the user is seeking expert help. Do not include any masked label values (e.g. [Applicant name]).
- timeline: extract all dates and associated events from the document.
- ai_hypotheses: list conditions that could not be determined from the document alone
  (type "unclear"), and any interpretations that might be wrong (type "hypothesis").
- Write all generated text fields (consultation_summary, timeline[].event, ai_hypotheses[].point)
  in {target_language}.
```

#### User Message

```
Masked document text:
{masked_text}

Masked PII categories: {masked_categories}

User notes: {user_notes}

Prior Q&A with the user (for context only—do not copy verbatim):
{chat_history}
```

#### Variables

| Variable | Description |
|----------|-------------|
| `{target_language}` | English name of target language (use table from §5.5) |
| `{masked_text}` | `MaskResult.maskedText` (PII replaced with labeled tokens like `[Applicant name]`) |
| `{masked_categories}` | Comma-separated list of masked PII categories |
| `{user_notes}` | Notes entered by the user in S-04 (use `"(none)"` if empty) |
| `{chat_history}` | `DocumentChatSession.getChatHistory()` converted to `Q:` / `A:` format |

---

## 7. MF-07: Chat System Prompt

### 7.1 System Prompt

```
You are a helpful assistant for a user who has received a document.
The document has been analyzed and key information has been extracted.
Answer questions using both the structured summary and the original document text provided below.

Document name: {doc_name}
Summary: {summary}
Deadline: {deadline_note}
Required actions: {action_items}
Required items: {required_items}
Warnings: {warnings}

Original document text:
{source_text}

Help the user:
1. Understand the document and its requirements
2. Identify what they need to do and by when
3. Clarify any points they want to share with an expert or an AI assistant

Respond in {target_language}. Be concise. Limit your reply to 1–2 sentences.
```

> **Regarding maxOutputTokens**: The Kotlin API of LiteRT-LM 0.11.0 (`SamplerConfig`, `ConversationConfig`) has no parameters for per-call token limits. The `maxOutputTokens` safety net is substituted by prompt constraints (`Limit your reply to 1–2 sentences.`).

### 7.2 Variables

| Variable | Source | Format |
|----------|--------|--------|
| `{doc_name}` | `reviewResult.docName` | String |
| `{summary}` | `reviewResult.translation.summary` (with translation) or `reviewResult.summaryJa` | String |
| `{deadline_note}` | `reviewResult.translation.deadlineNote` or `reviewResult.deadline.noteJa` | String |
| `{action_items}` | Numbered list (prioritize translated fields) | Plain text |
| `{required_items}` | Numbered list (prioritize translated fields) | Plain text |
| `{warnings}` | Numbered list (prioritize translated fields) | Plain text |
| `{source_text}` | `DocumentReviewViewModel.sourceText` (raw text extracted by TextExtractor, trimmed to 16,000 characters or less). Passing raw text is acceptable for on-device inference (same as MF-02) | String |
| `{target_language}` | `reviewResult.translation?.language ?: reviewResult.sourceLanguage` → English text (use table from §5.5). If translated, use that language; if not translated, use the document's original language (`sourceLanguage`). When translation completes, reinitialize to ensure the correct language is always passed | String |

> If `{deadline_note}` is null (document with no deadline), embed `"None"`.

> `{action_items}`, `{required_items}`, and `{warnings}` are converted to plain text numbered list format and embedded (not JSON, but human-readable text).

### 7.3 Initial Assistant Message (Inserted by System)

A fixed message inserted by the system after `initialize()` is called. `{doc_name}` is filled with `reviewResult.docName`.

| target_language | Initial Message |
|----------------|-----------------|
| `ja` | こんにちは。「{doc_name}」について何でも聞いてください。 |
| `en` | Hello! Feel free to ask me anything about the {doc_name}. |
| `zh` | 您好！请随时向我询问有关「{doc_name}」的任何问题。 |
| `ko` | 안녕하세요. 「{doc_name}」에 대해 무엇이든 질문해 주세요. |
| `es` | ¡Hola! No dudes en preguntarme cualquier cosa sobre el documento «{doc_name}». |
| `fr` | Bonjour ! N'hésitez pas à me poser toutes vos questions sur le document «{doc_name}». |
| `de` | Hallo! Stellen Sie mir gerne Fragen zum Dokument „{doc_name}". |
| `it` | Ciao! Sentiti libero di chiedermi qualsiasi cosa riguardo al documento «{doc_name}». |
| `pt` | Olá! Sinta-se à vontade para me perguntar qualquer coisa sobre o documento «{doc_name}». |
| `ru` | Здравствуйте! Не стесняйтесь задавать любые вопросы о документе «{doc_name}». |
| `pl` | Cześć! Śmiało pytaj mnie o wszystko dotyczące dokumentu „{doc_name}". |
| `nl` | Hallo! Stel gerust vragen over het document «{doc_name}». |
| `ar` | مرحباً! لا تتردد في سؤالي عن أي شيء يتعلق بالوثيقة «{doc_name}». |
| `th` | สวัสดี! อย่าลังเลที่จะถามฉันเกี่ยวกับเอกสาร «{doc_name}» |
| `tr` | Merhaba! «{doc_name}» belgesi hakkında bana istediğiniz her şeyi sorabilirsiniz. |
| Others | Hello! Feel free to ask me anything about the {doc_name}. |

### 7.4 Notes on PII

- `piiSpans[].spanText` is not included in the system prompt or chat history
- `sourceText` (document body) is included in the system prompt, but on-device inference (LiteRT-LM) means no external transmission occurs (same treatment as MF-02)

---

### 8.1 Field Extraction Few-shot Example (MF-02, 9 Fields)

Line format samples (2 examples) to be embedded in `{few_shot_example}`. Separated by `===`.

```
DOC_NAME: 令和7年度 児童手当現況届
ISSUER_NAME: 江戸川区役所
APPLICANT_NAME: 山田太郎
OTHER_NAME: (none)
IMPORTANCE: high
SUMMARY: 毎年6月に提出が必要な現況届です。未提出の場合、翌月以降の支給が停止されます。
ACTION_ITEMS: 現況届を記入して江戸川区役所に提出する
REQUIRED_ITEMS: 健康保険証|コピー可|||印鑑|(none)
WARNING: high|2年間未提出の場合、受給資格が消滅します

===

DOC_NAME: Surgical Procedure Consent Form
ISSUER_NAME: City General Hospital
APPLICANT_NAME: John Smith
OTHER_NAME: (none)
IMPORTANCE: medium
SUMMARY: A consent form for a scheduled surgical procedure. The patient must sign and return the form before the procedure date.
ACTION_ITEMS: Review and sign the consent form|||Return the signed form to the admissions desk before the procedure
REQUIRED_ITEMS: Government-issued photo ID|Required at check-in|||Insurance card|(none)
WARNING: medium|Failure to return the signed form may result in postponement of the procedure
```

### 8.2 EntityAnnotator Few-shot Example (2 examples, separated by `===`)

Sample (from Issue #134 §EntityAnnotator prompt specification) to be embedded in `{few_shot_example}`.

```
issuer_name: 江戸川区役所
applicant_name: 山田太郎
other_name: (none)

1. DATE_TIME: 令和7年6月30日
2. DATE_TIME: 令和7年度
3. ADDRESS: 江戸川区中央1-2-3
4. PHONE: 03-1234-5678
5. EMAIL: kosodate@city.edogawa.tokyo.jp
6. ADDRESS: 港区虚空町1-2-3
7. DATE_TIME: 昭和60年1月1日
8. MONEY: 15,000円

---

1: deadline
2: document_date
3: issuer_address
4: issuer_phone
5: issuer_email
6: applicant_address
7: date_of_birth
8: benefit_amount

===

issuer_name: City General Hospital
applicant_name: John Smith
other_name: (none)

1. DATE_TIME: March 15, 2025
2. DATE_TIME: March 20, 2025
3. DATE_TIME: February 3, 1985
4. ADDRESS: 123 Medical Center Drive, Springfield
5. PHONE: (555) 234-5678
6. EMAIL: consent@citygeneral.org
7. ADDRESS: 456 Oak Street, Springfield
8. PHONE: (555) 987-6543
9. MONEY: $2,500.00

---

1: document_date
2: event_date
3: date_of_birth
4: issuer_address
5: issuer_phone
6: issuer_email
7: applicant_address
8: other_phone
9: fee
```

### 8.3 Translation Few-shot Example (MF-03)

Line format sample to be embedded in `{few_shot_example}`. English translation of the same document (Status Report Form) as a standard example.

```
SUMMARY: This is an annual status report that must be submitted in June. Failure to submit will stop benefit payments.
DEADLINE_NOTE: By June 30, 2025 (Monday)
ACTION_ITEMS: action_01|Fill in and submit the Status Report Form to the ward office
REQUIRED_ITEMS: doc_01|Health Insurance Card|Copies accepted|||doc_02|Personal Seal|(none)
WARNING: Failure to submit for 2 years will result in loss of eligibility
```

---

## 9. Prompt Variable Reference

Comprehensive list of variables to be embedded in each prompt during implementation.

| Variable | Used in Prompt | Generated by Class |
|----------|----------------|-------------------|
| `{few_shot_example}` | MF-02 | `PromptBuilder.FEW_SHOT_MF02` (hardcoded, 2 examples with 9 fields) |
| `{few_shot_example}` | EntityAnnotator | `PromptBuilder.FEW_SHOT_ENTITY_ANNOTATOR` (hardcoded, 2 examples) |
| `{few_shot_example}` | MF-03 | `PromptBuilder.FEW_SHOT_MF03` (hardcoded, 5 fields) |
| `{issuer_name}` | EntityAnnotator | `reviewResult.issuerName ?: "(none)"` |
| `{applicant_name}` | EntityAnnotator | `reviewResult.applicantName ?: "(none)"` |
| `{other_name}` | EntityAnnotator | `reviewResult.otherName ?: "(none)"` |
| `{numbered_entities}` | EntityAnnotator | DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY only, numbered list |
| `{document_text}` | MF-02 | `DocumentReviewViewModel` (TextExtractor output trimmed) |
| `{parse_error_message}` | MF-02 / MF-03 retry | `FieldExtractor` / `Translator` (parse error message) |
| `{source_language}` | MF-03 | Language code identified by `LanguageIdentifier` converted by `PromptBuilder.languageCodeToLabel()` |
| `{target_language}` | MF-03 | `Review.selectedLanguage` (selected language from S-02 translation bar) |
| `{target_language}` | MF-06a / MF-06 | `InquiryWizard.targetLanguage` (default to that language if translated) |
| `{target_language}` | MF-07 | `reviewResult.translation?.language ?: reviewResult.sourceLanguage` |
| `{fields_text}` | MF-03 | `Translator` (ReviewResult fields converted to text format) |
| `{doc_name}` | MF-06a / MF-07 | `reviewResult.docName` |
| `{summary}` | MF-06a / MF-07 | Translated fields prioritized (`translation.summary` → `summaryJa`) |
| `{action_items}` | MF-06a / MF-07 | Numbered list format (translated prioritized) |
| `{deadline_note}` | MF-07 | `DocumentChatSession` (translated prioritized, null → `"None"`) |
| `{required_items}` | MF-07 | `DocumentChatSession` (numbered list format) |
| `{warnings}` | MF-07 | `DocumentChatSession` (numbered list format) |
| `{source_text}` | MF-07 / MF-06a | `DocumentReviewViewModel.sourceText` (raw text extracted by TextExtractor, trimmed to 16,000 characters or less) |
| `{masked_text}` | MF-06 | `MaskResult.maskedText` |
| `{masked_categories}` | MF-06 | Category list from `MaskResult.appliedSpans` (comma-separated) |
| `{user_notes}` | MF-06 | S-04 user input notes (null → `"(none)"`) |
| `{chat_history}` | MF-06 / MF-06a | `DocumentChatSession.getChatHistory()` converted to `Q:` / `A:` format. In MF-06a, omit the section entirely if empty |

---

## 10. Evaluation Criteria

### 10.1 MF-02 Field Extraction

| Evaluation Item | Pass Criteria | Test Method |
|-----------------|---------------|------------|
| Line format parse success rate | ≥80% on first attempt without retry | Run 10 times on `edogawa_R7_genkyo_kinyuurei.pdf` |
| `DOC_NAME` extraction | Document title extracted verbatim from original | Manual verification |
| `ISSUER_NAME` / `APPLICANT_NAME` extraction | Issuer and recipient names correctly separated and extracted | Manual verification |
| `IMPORTANCE` determination | Documents with deadline violation penalties marked as `"high"` | Manual verification |
| Inference timeout | Within 150 seconds | TC-PERF-02 |

### 10.2 MF-03 Translation

| Evaluation Item | Pass Criteria | Test Method |
|-----------------|---------------|------------|
| `translation.language` | Matches requested language code | Automated (TC-03-01) |
| `action_items` count | Matches source data | Automated (TC-03-01) |
| Translation accuracy (English) | All fields (summary, action_items, deadline_note, warnings) translated without omission; document-specific terms translated appropriately (e.g. 現況届 → `"Status Report Form"`, 子育て支援課 → `"Child Welfare Division"`) | Manual verification |
| Inference timeout | Within 60 seconds | TC-PERF-03 |

### 10.3 MF-06 Inquiry Context Generation

| Evaluation Item | Pass Criteria | Test Method |
|-----------------|---------------|------------|
| MF-06a: 3–5 inquiry purpose candidates generated | true (fallback to empty list on failure) | Manual verification |
| Context text: No original PII included | `piiSpans[].spanText` not present in `toContextText()` output (`includedPiiSpans` excluded) | Automated (TC-06-ctx-01) |
| Context text: Section omission logic | deadline/required_docs/warnings sections omitted when empty | Automated (TC-06-ctx-02) |

### 10.4 MF-07 Chat

| Evaluation Item | Pass Criteria | Test Method |
|-----------------|---------------|------------|
| Initial message language | Matches `target_language` | Automated (TC-07-02) |
| No original PII in responses | `spanText` strings not present in response | Manual verification |
| Time to first token | Within 3 seconds | TC-PERF-05 |
| Response constraint in system prompt | `mf07SystemPrompt()` output contains `"Limit your reply to 1–2 sentences."` | Automated (TC-07-10) |
| Responses to typical questions in 1–2 sentences | Questions like `"When is the deadline?"` or `"What should I bring?"` answered in 1–2 sentences | Manual verification (TC-07-11) |

### 10.5 EntityAnnotator Evaluation

| Evaluation Item | Pass Criteria | Test Method |
|-----------------|---------------|------------|
| `context_label` coverage | All DATE_TIME / ADDRESS / PHONE / EMAIL / MONEY entities labeled (excluding `"unknown"`) | Run 5 times on `edogawa_R7_genkyo_kinyuurei.pdf` and verify manually |
| Fallback on timeout | Entities return with contextLabel = null on 60-second timeout, no crash | Automated (mock virtual time advanceTimeBy) |
| Fallback on parse failure | Entities return unchanged on parse failure, no crash | Automated (mock invalid response) |
| PiiSpan count | 3 or more generated from Tier-1/2 entities + applicantName / otherName | Manual verification |
| applicant_* entity classification | `computePiiTier()` returns 1 for applicant_address / applicant_phone / applicant_email | Automated (TC-ENT-tier-01) |
| Unwanted PII in inquiry document | `spanText` of PII not included in `includedPiiSpans` does not appear in `InquiryContext` | Manual verification (TC-08-02) |

### 10.6 Gemma 4 E2B vs E4B Selection Guidelines

Both E2B and E4B can be manually selected by the user in Model Manager. No automatic switching.

| Scenario | Recommendation |
|----------|---|
| Normal use | E2B (smaller download size, faster startup) |
| MF-02 parse success rate < 50% (10 trials) | Consider switching to E4B |
| 3 consecutive MF-02 timeouts (150 seconds) | Consider switching to E4B |

---

*This specification is based on Implementation Specification v0.2.6. When revising prompts, update this document along with on-device verification results.*
*v0.1.1: Changed MF-02 output format from JSON to line format (Key-Value lines). Addresses LiteRT-LM 0.10.0 Kotlin API's lack of Constrained Decoding support and Gemma 4 E2B's key-as-value collapse issue.*
*v0.1.2: Expanded target documents from administrative documents to documents in general. Added DOC_NAME and DOC_DATE fields to MF-02. Renamed REQUIRED_DOCS to REQUIRED_ITEMS. Removed IMPORTANCE and CONDITIONS (14 fields).*
*v0.1.3: Added ISSUER and RECIPIENT fields to MF-02 (16 fields).*
*v0.1.4: Removed DOC_TYPE. Consolidated LOCATION_NAME + LOCATION_ADDRESS → LOCATION, CONTACT_NAME + CONTACT_PHONE → CONTACT. Restored IMPORTANCE. Renamed {doc_type} to {doc_name} (14 fields).*
*v0.1.5: Separated PII extraction from MF-02 into independent step MF-08 (prevent PII leakage on escalation). MF-02 reduced to 13 fields. Added §6, renumbered §6-§8 to §7-§9.*
*v0.1.6: Renamed RECIPIENT to TARGET_PERSON. Changed MF-08 to continuous session format (no need to resend text).*
*v0.1.7: Completely revised MF-06 from escalation package generation to inquiry document generation wizard (3 steps: MF-06a purpose candidates, MF-06b bulk question list, MF-06c document generation). Changed MF-06b from interactive single-question to bulk question list generation (reduce LLM calls). Added email subfield to CONTACT (3-subfield structure).*
*v0.1.9: Changed translation (MF-03) to manual user execution. Removed language selection from S-01, moved to translation bar in S-02. Fixed chat (MF-07) initial language to Japanese; reinitialize session when translation completes. Explicitly separated `{target_language}` derivation rules for MF-03/MF-06/MF-07.*
*v0.1.8: Deferred MF-06c (inquiry document generation) to P2. Concerns about document quality in on-device inference (style, honorifics, language mismatch). Changed MF-06 output from "inquiry document" to "context text". Users can paste it to external AI assistant to request document generation. Removed §4.3, updated variable reference and evaluation criteria.*
*v0.2.0: Changed MF-02 and MF-08 user message from Japanese to English (fixed issue where Japanese instructions fixed LLM output language to Japanese). Added SOURCE_LANGUAGE field to MF-02 for multilingual document support.*
*v0.2.1: Removed MF-06b (missing information question list generation). On-device verification found it not useful. Simplified S-04 to single step. Updated variable reference and evaluation criteria.*
*v0.2.2: Removed document and language restrictions. Removed "Japanese document" from system prompts for MF-02/03/06/07/08 for multilingual document support. Added SOURCE_LANGUAGE field to MF-02 formally (14 fields). Changed chat initial language from fixed Japanese to document's original language (sourceLanguage).*
*v0.2.3: Added `Keep each response under 3 sentences.` to MF-07 system prompt (suppress long responses, reduce 20-second timeout likelihood). LiteRT-LM 0.10.0 Kotlin API lacks per-call maxOutputTokens, so use prompt constraints only. Added note to §5.1.*
*v0.2.4: Updated MF-02 to 16 fields (LOCATION → LOCATIONS for multiple, WARNINGS → WARNING single, added ISSUER_ADDRESS, EVENT_DATES, EXTRA_PII). Merged old MF-08 into EXTRA_PII, discontinued independent LLM step. Changed MF-03 output from JSON to line format (5 fields). Added MF-06 (escalation) subsection. Updated §7 MF-08 deprecation note, §8 few-shot examples, §9 variable reference, §10 evaluation criteria.*
*v0.3.0: Reduced MF-02 from 16 to 9 fields (DOC_NAME, ISSUER_NAME, APPLICANT_NAME, OTHER_NAME, IMPORTANCE, SUMMARY, ACTION_ITEMS, REQUIRED_ITEMS, WARNING). Transferred extraction responsibility for dates, addresses, phones, emails, amounts, URLs to ML Kit EntityExtractor + EntityAnnotator. Added §4 EntityAnnotator section (system prompt, user message, ALLOWED_LABELS, parse spec). Added EntityAnnotator samples to §8 few-shot examples. Updated §10.1 evaluation criteria to 9-field MF-02 (removed DEADLINE_DATE row). Completely revised §10.5 from EXTRA_PII (old MF-08) to EntityAnnotator evaluation criteria.*
*v0.3.1: Extended MF-07 chat timeout from 20 seconds to 60 seconds (aligned with Translator/OcrCorrector). Strengthened response constraint from `Keep each response under 3 sentences.` to `Limit your reply to 1–2 sentences.` (3 sentences in Japanese can exceed 150 chars). Updated §7.1 system prompt, §7.1 maxOutputTokens note, §10.4 TC-07-10/TC-07-11.*
*v0.3.2: Relaxed MF-01c §2.1 image-strict constraint. Replaced `<corrected_text> must match exactly what appears in the image` and `Do not guess corrections for text that is unclear or obscured in the image` with four rules that permit high-confidence language-based corrections while prohibiting corrections to proper nouns and ambiguous cases. Added boundary examples to §2.4.*