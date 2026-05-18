package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatRole
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.SupportedLanguage

object PromptBuilder {

    // ─── MF-02 ────────────────────────────────────────────────────────────────

    fun mf02SystemPrompt(): String {
        val fewShotExample = FEW_SHOT_MF02.trimIndent()
        return """
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
$fewShotExample
        """.trimIndent()
    }

    fun mf02UserMessage(documentText: String, parseError: String? = null): String {
        val base = """
Analyze the following document text:

$documentText
        """.trimIndent()
        return if (parseError != null) {
            """
$base

The previous output caused a parse error: $parseError
Please output the correct line format again.
            """.trimIndent()
        } else {
            base
        }
    }

    // ─── MF-01c: OCR correction ───────────────────────────────────────────────

    fun mf01cSystemPrompt(): String = """
You are a precise OCR correction assistant.

The user will provide an image of a document and the OCR text extracted from it.
Compare the image with the OCR text and identify transcription errors.
Output corrections only—no explanation, no markdown fences.

Format:
CORRECT: <wrong_text>|<corrected_text>

Rules:
- Output one CORRECT line per error.
- <wrong_text> must be copied verbatim from the OCR text.
- <corrected_text> must match exactly what appears in the image.
- If no errors are found, output: (none)
- Do not correct spacing or line-break differences unless they change meaning.
- Do not guess corrections for text that is unclear or obscured in the image.
    """.trimIndent()

    fun mf01cUserMessage(ocrText: String): String = """
Here is the OCR text extracted from the document image above:

$ocrText

List any transcription errors found.
    """.trimIndent()

    // ─── MF-03 ────────────────────────────────────────────────────────────────

    fun mf03SystemPrompt(sourceLanguage: String, targetLanguage: String): String {
        val sourceLangLabel = languageCodeToLabel(sourceLanguage)
        val targetLangLabel = languageCodeToLabel(targetLanguage)
        val fewShotExample = FEW_SHOT_MF03.trimIndent()
        return """
You are a precise translator.

Translate the provided $sourceLangLabel document fields into $targetLangLabel.
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

Example output:
$fewShotExample
        """.trimIndent()
    }

    fun mf03UserMessage(
        fieldsText: String,
        sourceLanguage: String,
        targetLanguage: String,
        parseError: String? = null,
    ): String {
        val sourceLangLabel = languageCodeToLabel(sourceLanguage)
        val targetLangLabel = languageCodeToLabel(targetLanguage)
        val base = """
Translate the following fields from a $sourceLangLabel document into $targetLangLabel:

$fieldsText
        """.trimIndent()
        return if (parseError != null) {
            """
$base

The previous output caused a parse error: $parseError
Please output the correct line format again.
            """.trimIndent()
        } else {
            base
        }
    }

    // ─── MF-06a: Purpose suggestions ─────────────────────────────────────────

    fun mf06aSystemPrompt(targetLanguage: String): String {
        val langLabel = languageCodeToLabel(targetLanguage)
        return """
You are a helpful assistant analyzing a document.
Suggest 3-5 concise inquiry purposes a recipient might have for this document.

Output a JSON array of short strings only—no explanation, no markdown fences.
Each string should be 5-15 words in $langLabel.

Example output:
["Confirm the submission deadline", "Clarify required documents", "Ask about payment status"]
        """.trimIndent()
    }

    fun mf06aUserMessage(
        docName: String,
        summary: String,
        actionItems: String,
        targetLanguage: String,
        sourceText: String,
        chatHistory: String = "",
    ): String {
        val langLabel = languageCodeToLabel(targetLanguage)
        val priorQaSection = if (chatHistory.isNotBlank()) {
            "\nPrior Q&A with the user (for context only — reflect topics raised, do not copy verbatim):\n$chatHistory\n"
        } else {
            ""
        }
        return """
Document: $docName
Summary: $summary
Required actions: $actionItems

Original document text:
$sourceText
$priorQaSection
Suggest inquiry purposes in $langLabel.
        """.trimIndent()
    }

    // ─── MF-06 (legacy escalation) ────────────────────────────────────────────

    fun mf06SystemPrompt(targetLanguage: String): String {
        val langLabel = languageCodeToLabel(targetLanguage)
        return """
You are a precise assistant helping prepare a privacy-safe escalation document.
The document has been masked—personal information replaced with [■■■].

Output raw JSON only—no explanation, no markdown fences.

Output schema:
{
  "consultation_summary": "string (2-3 sentences explaining what the user needs help with)",
  "timeline": [{"date": "YYYY-MM-DD or descriptive string", "event": "string"}],
  "ai_hypotheses": [{"point": "string", "type": "hypothesis | unclear"}]
}

Rules:
- consultation_summary: explain the document type, what action is needed, and why
  the user is seeking expert help. Do not include any [■■■] values.
- timeline: extract all dates and associated events from the document.
- ai_hypotheses: list conditions that could not be determined from the document alone
  (type "unclear"), and any interpretations that might be wrong (type "hypothesis").
- Write all generated text fields (consultation_summary, timeline[].event, ai_hypotheses[].point)
  in $langLabel.
        """.trimIndent()
    }

    fun mf06UserMessage(
        maskedText: String,
        maskedCategories: String,
        userNotes: String,
        chatHistory: String,
    ): String = """
Masked document text:
$maskedText

Masked PII categories: $maskedCategories

User notes: $userNotes

Prior Q&A with the user (for context only—do not copy verbatim):
$chatHistory
    """.trimIndent()

    // ─── MF-07 ────────────────────────────────────────────────────────────────

    fun mf07SystemPrompt(reviewResult: ReviewResult, targetLanguage: String, sourceText: String): String {
        val langLabel = languageCodeToLabel(targetLanguage)
        val t = reviewResult.translation

        val summary = t?.summary ?: reviewResult.summaryJa
        val deadlineNote = t?.deadlineNote ?: reviewResult.deadline.noteJa ?: "None"

        val actionItems = if (t != null) {
            t.actionItems.mapIndexed { i, item -> "${i + 1}. ${item.description}" }
                .joinToString("\n")
        } else {
            reviewResult.actionItems.mapIndexed { i, item -> "${i + 1}. ${item.descriptionJa}" }
                .joinToString("\n")
        }.ifEmpty { "None" }

        val requiredItems = if (t != null) {
            t.requiredItems.mapIndexed { i, item ->
                val note = if (item.note != null) " (${item.note})" else ""
                "${i + 1}. ${item.name}$note"
            }.joinToString("\n")
        } else {
            reviewResult.requiredItems.mapIndexed { i, item ->
                val note = if (item.noteJa != null) " (${item.noteJa})" else ""
                "${i + 1}. ${item.nameJa}$note"
            }.joinToString("\n")
        }.ifEmpty { "None" }

        val warnings = if (t != null) {
            t.warning?.let { "1. ${it.description}" } ?: ""
        } else {
            reviewResult.warning?.let { "1. ${it.descriptionJa}" } ?: ""
        }.ifEmpty { "None" }

        return """
You are a helpful assistant for a user who has received a document.
The document has been analyzed and key information has been extracted.
Answer questions using both the structured summary and the original document text provided below.

Document name: ${reviewResult.docName}
Summary: $summary
Deadline: $deadlineNote
Required actions: $actionItems
Required items: $requiredItems
Warnings: $warnings

Original document text:
$sourceText

Help the user:
1. Understand the document and its requirements
2. Identify what they need to do and by when
3. Clarify any points they want to share with an expert or an AI assistant

Respond in $langLabel. Be concise and clear. Keep each response under 3 sentences.
        """.trimIndent()
    }

    fun mf07InitialMessage(docName: String, targetLanguage: String): String = when (targetLanguage) {
        "ja" -> "こんにちは。「$docName」について何でも聞いてください。"
        "zh" -> "您好！请随时向我询问有关「$docName」的任何问题。"
        "ko" -> "안녕하세요. 「$docName」에 대해 무엇이든 질문해 주세요."
        "es" -> "¡Hola! No dudes en preguntarme cualquier cosa sobre el documento «$docName»."
        "fr" -> "Bonjour ! N'hésitez pas à me poser toutes vos questions sur le document «$docName»."
        "de" -> "Hallo! Stellen Sie mir gerne Fragen zum Dokument „$docName“."
        "it" -> "Ciao! Sentiti libero di chiedermi qualsiasi cosa riguardo al documento «$docName»."
        "pt" -> "Olá! Sinta-se à vontade para me perguntar qualquer coisa sobre o documento «$docName»."
        "ru" -> "Здравствуйте! Не стесняйтесь задавать любые вопросы о документе «$docName»."
        "pl" -> "Cześć! Śmiało pytaj mnie o wszystko dotyczące dokumentu „$docName“."
        "nl" -> "Hallo! Stel gerust vragen over het document «$docName»."
        "ar" -> "مرحباً! لا تتردد في سؤالي عن أي شيء يتعلق بالوثيقة «$docName»."
        "th" -> "สวัสดี! อย่าลังเลที่จะถามฉันเกี่ยวกับเอกสาร «$docName»"
        "tr" -> "Merhaba! «$docName» belgesi hakkında bana istediğiniz her şeyi sorabilirsiniz."
        else -> "Hello! Feel free to ask me anything about the $docName."
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    fun languageCodeToLabel(code: String): String =
        SupportedLanguage.fromCode(code)?.llmLabel ?: code

    fun chatHistoryToText(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""
        return messages.joinToString("\n") { msg ->
            if (msg.role == ChatRole.USER) "Q: ${msg.content}" else "A: ${msg.content}"
        }
    }

    // ─── Few-shot examples (inline) ───────────────────────────────────────────

    private val FEW_SHOT_MF03 = """
SUMMARY: This is an annual status report that must be submitted in June. Failure to submit will stop benefit payments.
DEADLINE_NOTE: By June 30, 2025 (Monday)
ACTION_ITEMS: action_01|Fill in and submit the Status Report Form to the ward office
REQUIRED_ITEMS: doc_01|Health Insurance Card|Copies accepted|||doc_02|Personal Seal|(none)
WARNING: Failure to submit for 2 years will result in loss of eligibility
    """

    // ─── EntityAnnotator prompts ──────────────────────────────────────────────

    fun entityAnnotatorSystemPrompt(): String {
        val fewShotExample = FEW_SHOT_ENTITY_ANNOTATOR.trimIndent()
        return """
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
$fewShotExample
        """.trimIndent()
    }

    fun entityAnnotatorUserMessage(
        issuerName: String?,
        applicantName: String?,
        otherName: String?,
        numberedEntities: String,
    ): String = """
issuer_name: ${issuerName ?: "(none)"}
applicant_name: ${applicantName ?: "(none)"}
other_name: ${otherName ?: "(none)"}

$numberedEntities

Label each entity.
    """.trimIndent()

    private val FEW_SHOT_ENTITY_ANNOTATOR = """
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
9. MONEY: ${"$"}2,500.00

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
    """

    private val FEW_SHOT_MF02 = """
DOC_NAME: 令和7年度 児童手当現況届
ISSUER_NAME: 江戸川区役所
APPLICANT_NAME: 山田太郎
OTHER_NAME: (none)
IMPORTANCE: high
SUMMARY: 毎年6月に提出が必要な現況届です。未提出の場合、翌月以降の支給が停止されます。
ACTION_ITEMS: 現況届を記入して江戸川区役所に提出する
REQUIRED_ITEMS: 健康保険証|コピー可|||印鑑|(none)
WARNING: high|2年間未提出の場合、受給資格が消滅します

---

DOC_NAME: Surgical Procedure Consent Form
ISSUER_NAME: City General Hospital
APPLICANT_NAME: John Smith
OTHER_NAME: (none)
IMPORTANCE: medium
SUMMARY: A consent form for a scheduled surgical procedure. The patient must sign and return the form before the procedure date.
ACTION_ITEMS: Review and sign the consent form|||Return the signed form to the admissions desk before the procedure
REQUIRED_ITEMS: Government-issued photo ID|Required at check-in|||Insurance card|(none)
WARNING: medium|Failure to return the signed form may result in postponement of the procedure
    """
}
