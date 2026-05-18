package io.github.joyk0117.paperworknavigator.customtasks.documentreview.model

import android.net.Uri
import androidx.annotation.StringRes
import io.github.joyk0117.paperworknavigator.R
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── DocumentInput ────────────────────────────────────────────────────────────

sealed class DocumentInput {
    data class PdfUri(val uri: Uri) : DocumentInput()
    data class RawText(val text: String) : DocumentInput()
}

// ─── Supporting data classes for ReviewResult (MF-02) ────────────────────────

@Serializable
data class DeadlineInfo(
    val date: String? = null,
    @SerialName("note_ja") val noteJa: String? = null,
)

@Serializable
data class LocationEntry(
    @SerialName("name_ja") val nameJa: String? = null,
    @SerialName("address_ja") val addressJa: String? = null,
)

@Serializable
data class EventDate(
    val date: String? = null,
    @SerialName("description_ja") val descriptionJa: String = "",
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
    val severity: String = "medium", // "high" | "medium" | "low"
)

@Serializable
data class EntityMetadata(
    val timestampMillis: Long? = null,
    val granularity: String? = null,
    val currency: String? = null,
    val integerPart: Long? = null,
    val ibanCountryCode: String? = null,
    val cardNetwork: String? = null,
    val carrier: String? = null,
    val airlineCode: String? = null,
)

@Serializable
data class DetectedEntity(
    val type: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("context_label") val contextLabel: String? = null,
    @SerialName("pii_tier") val piiTier: Int? = null,
    val metadata: EntityMetadata? = null,
)

fun DetectedEntity.computePiiTier(): Int = when (type) {
    "IBAN", "PAYMENT_CARD" -> 1
    "ADDRESS", "PHONE", "EMAIL" ->
        if (contextLabel?.startsWith("applicant") == true) 1 else 2
    "TRACKING_NUMBER" -> 2
    "DATE_TIME" -> if (contextLabel == "date_of_birth") 1 else 3
    "MONEY" -> 2
    else -> 3  // URL, ISBN, FLIGHT_NUMBER
}

@Serializable
data class PiiSpan(
    val id: String,
    @SerialName("span_text") val spanText: String = "",
    val category: String = "other", // "name" | "address" | "phone" | "account" | "dob" | "id_number" | "other"
    @SerialName("source_field") val sourceField: String? = null, // e.g. "applicant_name", "issuer_address", contextLabel
    @SerialName("mask_recommended") val maskRecommended: Boolean = true,
    @SerialName("user_override") val userOverride: Boolean? = null,
)

fun PiiSpan.categoryLabel(lang: String): String = when (category) {
    "name" -> when (lang) { "en" -> "Name"; "zh" -> "姓名"; "ko" -> "이름"; "es" -> "Nombre"; "fr" -> "Nom"; "de" -> "Name"; "it" -> "Nome"; "pt" -> "Nome"; "ru" -> "Имя"; "pl" -> "Imię"; "nl" -> "Naam"; "ar" -> "الاسم"; "th" -> "ชื่อ"; "tr" -> "İsim"; else -> "氏名" }
    "address" -> when (lang) { "en" -> "Address"; "zh" -> "地址"; "ko" -> "주소"; "es" -> "Dirección"; "fr" -> "Adresse"; "de" -> "Adresse"; "it" -> "Indirizzo"; "pt" -> "Endereço"; "ru" -> "Адрес"; "pl" -> "Adres"; "nl" -> "Adres"; "ar" -> "العنوان"; "th" -> "ที่อยู่"; "tr" -> "Adres"; else -> "住所" }
    "phone" -> when (lang) { "en" -> "Phone"; "zh" -> "电话"; "ko" -> "전화"; "es" -> "Teléfono"; "fr" -> "Téléphone"; "de" -> "Telefon"; "it" -> "Telefono"; "pt" -> "Telefone"; "ru" -> "Телефон"; "pl" -> "Telefon"; "nl" -> "Telefoon"; "ar" -> "الهاتف"; "th" -> "โทรศัพท์"; "tr" -> "Telefon"; else -> "電話番号" }
    "account" -> when (lang) { "en" -> "Account"; "zh" -> "账号"; "ko" -> "계좌"; "es" -> "Cuenta"; "fr" -> "Compte"; "de" -> "Konto"; "it" -> "Conto"; "pt" -> "Conta"; "ru" -> "Счёт"; "pl" -> "Konto"; "nl" -> "Rekening"; "ar" -> "الحساب"; "th" -> "บัญชี"; "tr" -> "Hesap"; else -> "口座番号" }
    "dob" -> when (lang) { "en" -> "Date of Birth"; "zh" -> "出生日期"; "ko" -> "생년월일"; "es" -> "Fecha de nacimiento"; "fr" -> "Date de naissance"; "de" -> "Geburtsdatum"; "it" -> "Data di nascita"; "pt" -> "Data de nascimento"; "ru" -> "Дата рождения"; "pl" -> "Data urodzenia"; "nl" -> "Geboortedatum"; "ar" -> "تاريخ الميلاد"; "th" -> "วันเกิด"; "tr" -> "Doğum tarihi"; else -> "生年月日" }
    "id_number" -> when (lang) { "en" -> "ID Number"; "zh" -> "证件号码"; "ko" -> "증명번호"; "es" -> "Número de ID"; "fr" -> "Numéro d'identification"; "de" -> "Ausweisnummer"; "it" -> "Numero ID"; "pt" -> "Número de ID"; "ru" -> "Номер ID"; "pl" -> "Numer ID"; "nl" -> "ID-nummer"; "ar" -> "رقم الهوية"; "th" -> "หมายเลข ID"; "tr" -> "Kimlik numarası"; else -> "番号" }
    else -> when (lang) { "en" -> "Personal Information"; "zh" -> "个人信息"; "ko" -> "개인정보"; "es" -> "Información personal"; "fr" -> "Informations personnelles"; "de" -> "Persönliche Daten"; "it" -> "Informazioni personali"; "pt" -> "Informações pessoais"; "ru" -> "Личные данные"; "pl" -> "Dane osobowe"; "nl" -> "Persoonlijke informatie"; "ar" -> "المعلومات الشخصية"; "th" -> "ข้อมูลส่วนบุคคล"; "tr" -> "Kişisel bilgi"; else -> "個人情報" }
}

/** Returns the mask placeholder token for this span, e.g. "[Applicant name]" or "[■■■]". */
fun PiiSpan.maskToken(): String {
    val label = when (sourceField) {
        "issuer_name"       -> "Issuer name"
        "applicant_name"    -> "Applicant name"
        "other_name"        -> "Other name"
        "issuer_address"    -> "Issuer address"
        "applicant_address" -> "Applicant address"
        "other_address"     -> "Other address"
        "issuer_phone"      -> "Issuer phone"
        "applicant_phone"   -> "Applicant phone"
        "other_phone"       -> "Other phone"
        "issuer_email"      -> "Issuer email"
        "applicant_email"   -> "Applicant email"
        "other_email"       -> "Other email"
        "date_of_birth"     -> "Date of birth"
        "benefit_amount"    -> "Benefit amount"
        "fee"               -> "Fee"
        "penalty"           -> "Penalty"
        "other_amount"      -> "Other amount"
        "iban"              -> "IBAN"
        "payment_card"      -> "Payment card"
        "tracking_number"   -> "Tracking number"
        else                -> null
    }
    return if (label != null) "[$label]" else "[■■■]"
}

// ─── MF-03: Translation ───────────────────────────────────────────────────────

@Serializable
data class TranslatedActionItem(
    val id: String,
    val description: String,
)

@Serializable
data class TranslatedRequiredItem(
    val id: String,
    val name: String,
    val note: String? = null,
)

@Serializable
data class TranslatedWarning(
    val description: String,
)

@Serializable
data class Translation(
    val language: String,
    val summary: String,
    @SerialName("deadline_note") val deadlineNote: String? = null,
    @SerialName("action_items") val actionItems: List<TranslatedActionItem> = emptyList(),
    @SerialName("required_items") val requiredItems: List<TranslatedRequiredItem> = emptyList(),
    val warning: TranslatedWarning? = null,
)

// ─── ReviewResult ─────────────────────────────────────────────────────────────

@Serializable
data class ReviewResult(
    @SerialName("doc_name") val docName: String,
    @SerialName("doc_date") val docDate: String? = null,
    @SerialName("issuer_name") val issuerName: String? = null,
    @SerialName("applicant_name") val applicantName: String? = null,
    @SerialName("other_name") val otherName: String? = null,
    val importance: String, // "high" | "medium" | "low"
    @SerialName("summary_ja") val summaryJa: String,
    val deadline: DeadlineInfo,
    @SerialName("issuer_address") val issuerAddress: String? = null,
    val locations: List<LocationEntry> = emptyList(),
    @SerialName("action_items") val actionItems: List<ActionItem> = emptyList(),
    @SerialName("required_items") val requiredItems: List<RequiredItem> = emptyList(),
    val warning: Warning? = null,
    @SerialName("event_dates") val eventDates: List<EventDate> = emptyList(),
    val translation: Translation? = null,
    @SerialName("source_language") val sourceLanguage: String = "ja",
    @SerialName("detected_entities") val detectedEntities: List<DetectedEntity> = emptyList(),
)

fun ReviewResult.issuerPhone(): String? =
    detectedEntities.firstOrNull { it.contextLabel == "issuer_phone" }?.rawText

fun ReviewResult.issuerEmail(): String? =
    detectedEntities.firstOrNull { it.contextLabel == "issuer_email" }?.rawText

// ─── Shared i18n helpers ─────────────────────────────────────────────────────

internal fun importanceLabelFor(importance: String, lang: String?): String = when (lang) {
    "en" -> when (importance) { "high" -> "High"; "low" -> "Low"; else -> "Medium" }
    "zh" -> when (importance) { "high" -> "高"; "low" -> "低"; else -> "中" }
    "ko" -> when (importance) { "high" -> "높음"; "low" -> "낮음"; else -> "보통" }
    "es" -> when (importance) { "high" -> "Alto"; "low" -> "Bajo"; else -> "Medio" }
    "fr" -> when (importance) { "high" -> "Élevé"; "low" -> "Faible"; else -> "Moyen" }
    "de" -> when (importance) { "high" -> "Hoch"; "low" -> "Niedrig"; else -> "Mittel" }
    "it" -> when (importance) { "high" -> "Alto"; "low" -> "Basso"; else -> "Medio" }
    "pt" -> when (importance) { "high" -> "Alto"; "low" -> "Baixo"; else -> "Médio" }
    "ru" -> when (importance) { "high" -> "Высокий"; "low" -> "Низкий"; else -> "Средний" }
    "pl" -> when (importance) { "high" -> "Wysoki"; "low" -> "Niski"; else -> "Średni" }
    "nl" -> when (importance) { "high" -> "Hoog"; "low" -> "Laag"; else -> "Gemiddeld" }
    "ar" -> when (importance) { "high" -> "عالٍ"; "low" -> "منخفض"; else -> "متوسط" }
    "th" -> when (importance) { "high" -> "สูง"; "low" -> "ต่ำ"; else -> "ปานกลาง" }
    "tr" -> when (importance) { "high" -> "Yüksek"; "low" -> "Düşük"; else -> "Orta" }
    else -> when (importance) { "high" -> "高"; "low" -> "低"; else -> "中" }
}

// ─── MF-05: PII masking ───────────────────────────────────────────────────────

data class MaskResult(
    val maskedText: String,
    val appliedSpans: List<PiiSpan>,
    val skippedSpans: List<PiiSpan>,
    val unmatchedSpans: List<PiiSpan>,
)

// ─── MF-07: Document chat ─────────────────────────────────────────────────────

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

// ─── MF-06: Escalation package (kept for existing tests) ─────────────────────

@Serializable
data class KeyPoint(
    val category: String, // "deadline" | "action" | "warning" | "required_doc"
    val description: String,
)

@Serializable
data class TimelineEvent(
    val date: String,
    val event: String,
)

@Serializable
data class RelatedDocument(
    val name: String,
    val note: String? = null,
)

@Serializable
data class AiHypothesis(
    val point: String,
    val type: String, // "hypothesis" | "unclear"
)

@Serializable
data class ChatHistoryEntry(
    val role: String, // "user" | "assistant"
    val content: String,
)

@Serializable
data class EscalationPackage(
    val language: String,
    @SerialName("masked_fields") val maskedFields: List<String>,
    @SerialName("consultation_summary") val consultationSummary: String,
    @SerialName("key_points") val keyPoints: List<KeyPoint>,
    val timeline: List<TimelineEvent>,
    @SerialName("related_documents") val relatedDocuments: List<RelatedDocument>,
    @SerialName("masked_source_text") val maskedSourceText: String,
    @SerialName("ai_hypotheses") val aiHypotheses: List<AiHypothesis>,
    @SerialName("user_notes") val userNotes: String?,
    @SerialName("chat_history") val chatHistory: List<ChatHistoryEntry>,
) {
    fun toPlainText(): String {
        val parts = buildList {
            add("⚠️ Personal information is masked / masked: ${maskedFields.joinToString(", ")}")
            add(section("## 1. Consultation Summary", consultationSummary.trimEnd()))
            add(section("## 2. Key Points",
                keyPoints.joinToString("\n") { "- ${it.description}" }))
            add(section("## 3. Timeline",
                timeline.joinToString("\n") { "- ${it.date}: ${it.event}" }))
            add(section("## 4. Related Documents",
                relatedDocuments.joinToString("\n") { "- ${it.name}" }))
            add(section("## 5. Masked Source Text", maskedSourceText.trimEnd()))
            if (aiHypotheses.isNotEmpty()) {
                add(section("## 6. AI Hypotheses & Unclear Points",
                    aiHypotheses.joinToString("\n") { "- [${it.type}] ${it.point}" }))
            }
            add(section("## 7. User Notes", userNotes?.trimEnd() ?: "(no notes)"))
            add(section("## 8. Prior Q&A",
                chatHistory.joinToString("\n") { entry ->
                    if (entry.role == "user") "Q: ${entry.content}" else "A: ${entry.content}"
                }))
        }
        return parts.joinToString("\n\n")
    }
}

private fun section(heading: String, content: String): String =
    if (content.isEmpty()) heading else "$heading\n$content"

// ─── MF-06: Inquiry context (§5.5) ───────────────────────────────────────────

data class InquiryRecipient(
    val organizationName: String,
    val contactName: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

data class InquiryContext(
    val language: String,
    val recipient: InquiryRecipient,
    val purpose: String,
    val documentSummary: String,
    val maskedPiiSpans: List<PiiSpan>,
    val allPiiSpans: List<PiiSpan>,
    val reviewResult: ReviewResult,
    val maskedSourceText: String = "",
) {
    fun toContextText(): String = buildString {
        val t = reviewResult.translation
        // Always use InquiryContext.language for headings/labels and content selection.
        // Only use translated fields when the translation language matches the wizard selection.
        val lang = language
        val useTranslation = t != null && t.language == language

        // ── Intro (consultation-style, not command-style) ──
        appendLine(when (lang) {
            "en" -> "The following information was automatically extracted by an on-device small LLM. It may contain errors, so please cross-check with the original text at the bottom.\nAlso, please share the latest official information on the relevant procedures if available."
            "zh" -> "以下信息由设备端小型 LLM 自动提取，可能存在错误，请与末尾的原文进行核对。\n此外，如有最新官方信息，也请一并告知。"
            "ko" -> "아래 정보는 기기 내 소형 LLM이 자동으로 추출한 것으로, 오류가 포함될 수 있습니다. 하단의 원문과 함께 확인해 주세요.\n또한 관련 절차에 대한 최신 공식 정보도 알려주시면 감사하겠습니다."
            "es" -> "La siguiente información fue extraída automáticamente por un LLM pequeño en el dispositivo. Puede contener errores, por favor verifique con el texto original al final.\nAdemás, comparta la información oficial más reciente sobre los procedimientos relevantes si está disponible."
            "fr" -> "Les informations suivantes ont été extraites automatiquement par un petit LLM sur l'appareil. Elles peuvent contenir des erreurs, veuillez les vérifier avec le texte original en bas.\nMerci également de partager les informations officielles les plus récentes sur les procédures concernées si disponibles."
            "de" -> "Die folgenden Informationen wurden automatisch von einem On-Device-Sprachmodell extrahiert. Sie können Fehler enthalten. Bitte gleichen Sie sie mit dem Originaltext am Ende ab.\nTeilen Sie uns bitte auch die aktuellsten offiziellen Informationen zu den betreffenden Verfahren mit, falls verfügbar."
            "it" -> "Le seguenti informazioni sono state estratte automaticamente da un LLM compatto sul dispositivo. Potrebbero contenere errori; si prega di confrontarle con il testo originale in fondo.\nCondividete anche le informazioni ufficiali più aggiornate sulle procedure pertinenti, se disponibili."
            "pt" -> "As informações a seguir foram extraídas automaticamente por um LLM compacto no dispositivo. Podem conter erros; por favor, verifique com o texto original no final.\nCaso disponível, compartilhe também as informações oficiais mais recentes sobre os procedimentos relevantes."
            "ru" -> "Следующая информация была автоматически извлечена встроенной малой языковой моделью. Она может содержать ошибки; пожалуйста, проверьте её по оригинальному тексту внизу.\nПоделитесь также актуальной официальной информацией по соответствующим процедурам, если она доступна."
            "pl" -> "Poniższe informacje zostały automatycznie wyodrębnione przez mały model językowy na urządzeniu. Mogą zawierać błędy; proszę sprawdzić je z oryginalnym tekstem na dole.\nProszę również podzielić się najnowszymi oficjalnymi informacjami dotyczącymi odpowiednich procedur, jeśli są dostępne."
            "nl" -> "De volgende informatie is automatisch geëxtraheerd door een klein on-device taalmodel. Het kan fouten bevatten; controleer het met de originele tekst onderaan.\nDeel ook de meest recente officiële informatie over de betreffende procedures als die beschikbaar is."
            "ar" -> "تم استخراج المعلومات التالية تلقائيًا بواسطة نموذج لغوي صغير على الجهاز. قد تحتوي على أخطاء؛ يرجى مقارنتها مع النص الأصلي في الأسفل.\nيرجى أيضًا مشاركة أحدث المعلومات الرسمية حول الإجراءات ذات الصلة إن وُجدت."
            "th" -> "ข้อมูลต่อไปนี้ถูกดึงออกมาโดยอัตโนมัติจาก LLM ขนาดเล็กบนอุปกรณ์ อาจมีข้อผิดพลาด กรุณาตรวจสอบกับข้อความต้นฉบับด้านล่าง\nกรุณาแชร์ข้อมูลทางการล่าสุดเกี่ยวกับขั้นตอนที่เกี่ยวข้องหากมี"
            "tr" -> "Aşağıdaki bilgiler, cihaz üzerindeki küçük bir dil modeli tarafından otomatik olarak çıkarılmıştır. Hata içerebilir; lütfen aşağıdaki orijinal metinle karşılaştırın.\nMevcut ise ilgili prosedürler hakkındaki en güncel resmi bilgileri de paylaşın."
            else -> "以下の内容はアプリが自動で読み取ったものです。誤りが含まれる場合がありますので、末尾の原文もあわせてご確認ください。\nまた、関連する制度や手続きについて最新の情報もあわせて教えていただけますと助かります。"
        })

        // ── Divider ──
        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")

        // ── Inquiry purpose ──
        appendLine()
        appendLine(when (lang) {
            "en" -> "Inquiry purpose: $purpose"
            "zh" -> "问询目的: $purpose"
            "ko" -> "문의 목적: $purpose"
            "es" -> "Propósito de la consulta: $purpose"
            "fr" -> "Objet de la demande : $purpose"
            "de" -> "Anfragezweck: $purpose"
            "it" -> "Scopo della richiesta: $purpose"
            "pt" -> "Finalidade da consulta: $purpose"
            "ru" -> "Цель запроса: $purpose"
            "pl" -> "Cel zapytania: $purpose"
            "nl" -> "Doel van de aanvraag: $purpose"
            "ar" -> "الغرض من الاستفسار: $purpose"
            "th" -> "วัตถุประสงค์การสอบถาม: $purpose"
            "tr" -> "Sorgu amacı: $purpose"
            else -> "問い合わせの目的: $purpose"
        })
        appendLine(when (lang) {
            "en" -> "Recipient: ${recipient.organizationName}"
            "zh" -> "致: ${recipient.organizationName}"
            "ko" -> "수신: ${recipient.organizationName}"
            "es" -> "Para: ${recipient.organizationName}"
            "fr" -> "À : ${recipient.organizationName}"
            "de" -> "Empfänger: ${recipient.organizationName}"
            "it" -> "Destinatario: ${recipient.organizationName}"
            "pt" -> "Destinatário: ${recipient.organizationName}"
            "ru" -> "Получатель: ${recipient.organizationName}"
            "pl" -> "Odbiorca: ${recipient.organizationName}"
            "nl" -> "Ontvanger: ${recipient.organizationName}"
            "ar" -> "المستلم: ${recipient.organizationName}"
            "th" -> "ผู้รับ: ${recipient.organizationName}"
            "tr" -> "Alıcı: ${recipient.organizationName}"
            else -> "あて先: ${recipient.organizationName}"
        })

        // Unmasked PII — spans the user chose NOT to mask are shown as sender info
        val unmaskedSpans = allPiiSpans.filterNot { s -> maskedPiiSpans.any { it.id == s.id } }
        if (unmaskedSpans.isNotEmpty()) {
            appendLine()
            appendLine(when (lang) {
                "en" -> "Sender information (optional):"
                "zh" -> "发件人信息（可选）:"
                "ko" -> "발신자 정보（선택）:"
                "es" -> "Información del remitente (opcional):"
                "fr" -> "Informations de l'expéditeur (facultatif) :"
                "de" -> "Absenderinformationen (optional):"
                "it" -> "Informazioni mittente (facoltativo):"
                "pt" -> "Informações do remetente (opcional):"
                "ru" -> "Информация отправителя (необязательно):"
                "pl" -> "Informacje nadawcy (opcjonalnie):"
                "nl" -> "Afzenderinformatie (optioneel):"
                "ar" -> "معلومات المرسل (اختياري):"
                "th" -> "ข้อมูลผู้ส่ง (ไม่บังคับ):"
                "tr" -> "Gönderici bilgisi (isteğe bağlı):"
                else -> "送信者情報（任意）:"
            })
            unmaskedSpans.forEach { span ->
                appendLine("- ${span.categoryLabel(lang)}: ${span.spanText}")
            }
        }

        // ── Separator ──
        appendLine()
        appendLine("---")

        // ── Document header ──
        appendLine()
        val importanceLabel = importanceLabelFor(reviewResult.importance, lang)
        appendLine(when (lang) {
            "en" -> "📄 Document: ${reviewResult.docName}"
            "zh" -> "📄 文件名: ${reviewResult.docName}"
            "ko" -> "📄 서류명: ${reviewResult.docName}"
            "es" -> "📄 Documento: ${reviewResult.docName}"
            "fr" -> "📄 Document : ${reviewResult.docName}"
            "de" -> "📄 Dokument: ${reviewResult.docName}"
            "it" -> "📄 Documento: ${reviewResult.docName}"
            "pt" -> "📄 Documento: ${reviewResult.docName}"
            "ru" -> "📄 Документ: ${reviewResult.docName}"
            "pl" -> "📄 Dokument: ${reviewResult.docName}"
            "nl" -> "📄 Document: ${reviewResult.docName}"
            "ar" -> "📄 الوثيقة: ${reviewResult.docName}"
            "th" -> "📄 เอกสาร: ${reviewResult.docName}"
            "tr" -> "📄 Belge: ${reviewResult.docName}"
            else -> "📄 書類: ${reviewResult.docName}"
        })
        appendLine(when (lang) {
            "en" -> "Importance: $importanceLabel"
            "zh" -> "重要度: $importanceLabel"
            "ko" -> "중요도: $importanceLabel"
            "es" -> "Importancia: $importanceLabel"
            "fr" -> "Importance : $importanceLabel"
            "de" -> "Wichtigkeit: $importanceLabel"
            "it" -> "Importanza: $importanceLabel"
            "pt" -> "Importância: $importanceLabel"
            "ru" -> "Важность: $importanceLabel"
            "pl" -> "Ważność: $importanceLabel"
            "nl" -> "Belang: $importanceLabel"
            "ar" -> "الأهمية: $importanceLabel"
            "th" -> "ความสำคัญ: $importanceLabel"
            "tr" -> "Önem: $importanceLabel"
            else -> "重要度: $importanceLabel"
        })

        // ── Summary ──
        appendLine()
        appendLine(when (lang) {
            "en" -> "📝 Summary"; "zh" -> "📝 概要"; "ko" -> "📝 개요"
            "es" -> "📝 Resumen"; "fr" -> "📝 Résumé"
            "de" -> "📝 Zusammenfassung"; "it" -> "📝 Sommario"; "pt" -> "📝 Resumo"
            "ru" -> "📝 Краткое содержание"; "pl" -> "📝 Podsumowanie"; "nl" -> "📝 Samenvatting"
            "ar" -> "📝 ملخص"; "th" -> "📝 สรุป"; "tr" -> "📝 Özet"
            else -> "📝 概要"
        })
        appendLine(documentSummary)

        // ── Deadline (omit if absent) ──
        val deadlineNote = if (useTranslation) t!!.deadlineNote else reviewResult.deadline.noteJa
        if (!deadlineNote.isNullOrBlank()) {
            appendLine()
            appendLine(when (lang) {
                "en" -> "📅 Deadline"; "zh" -> "📅 截止日期"; "ko" -> "📅 기한"
                "es" -> "📅 Fecha límite"; "fr" -> "📅 Date limite"
                "de" -> "📅 Frist"; "it" -> "📅 Scadenza"; "pt" -> "📅 Prazo"
                "ru" -> "📅 Срок"; "pl" -> "📅 Termin"; "nl" -> "📅 Deadline"
                "ar" -> "📅 الموعد النهائي"; "th" -> "📅 กำหนดเวลา"; "tr" -> "📅 Son tarih"
                else -> "📅 期限"
            })
            appendLine(deadlineNote)
        }

        // ── Action items (omit if empty) ──
        val actions = if (useTranslation) t!!.actionItems.map { it.description } else reviewResult.actionItems.map { it.descriptionJa }
        if (actions.isNotEmpty()) {
            appendLine()
            appendLine(when (lang) {
                "en" -> "✅ Required Actions"; "zh" -> "✅ 必要行动"; "ko" -> "✅ 필요한 조치"
                "es" -> "✅ Acciones requeridas"; "fr" -> "✅ Actions requises"
                "de" -> "✅ Erforderliche Maßnahmen"; "it" -> "✅ Azioni richieste"; "pt" -> "✅ Ações necessárias"
                "ru" -> "✅ Необходимые действия"; "pl" -> "✅ Wymagane działania"; "nl" -> "✅ Vereiste acties"
                "ar" -> "✅ الإجراءات المطلوبة"; "th" -> "✅ การดำเนินการที่จำเป็น"; "tr" -> "✅ Gerekli işlemler"
                else -> "✅ 必要なアクション"
            })
            actions.forEachIndexed { i, desc -> appendLine("${i + 1}. $desc") }
        }

        // ── Required items (omit if empty) ──
        val reqs = if (useTranslation)
            t!!.requiredItems.map { item -> item.name + (item.note?.let { " ($it)" } ?: "") }
        else
            reviewResult.requiredItems.map { item -> item.nameJa + (item.noteJa?.let { " ($it)" } ?: "") }
        if (reqs.isNotEmpty()) {
            appendLine()
            appendLine(when (lang) {
                "en" -> "📎 Required Documents"; "zh" -> "📎 所需文件"; "ko" -> "📎 필요 서류"
                "es" -> "📎 Documentos requeridos"; "fr" -> "📎 Documents requis"
                "de" -> "📎 Erforderliche Dokumente"; "it" -> "📎 Documenti richiesti"; "pt" -> "📎 Documentos necessários"
                "ru" -> "📎 Необходимые документы"; "pl" -> "📎 Wymagane dokumenty"; "nl" -> "📎 Vereiste documenten"
                "ar" -> "📎 الوثائق المطلوبة"; "th" -> "📎 เอกสารที่ต้องการ"; "tr" -> "📎 Gerekli belgeler"
                else -> "📎 必要書類"
            })
            reqs.forEach { appendLine("- $it") }
        }

        // ── Warning (omit if absent) ──
        val warnText = if (useTranslation)
            t!!.warning?.let { "${it.description}（${reviewResult.warning?.severity ?: "medium"}）" }
        else
            reviewResult.warning?.let { "${it.descriptionJa}（${it.severity}）" }
        if (!warnText.isNullOrBlank()) {
            appendLine()
            appendLine(when (lang) {
                "en" -> "⚠️ Warnings"; "zh" -> "⚠️ 注意事项"; "ko" -> "⚠️ 주의사항"
                "es" -> "⚠️ Advertencias"; "fr" -> "⚠️ Avertissements"
                "de" -> "⚠️ Warnungen"; "it" -> "⚠️ Avvertenze"; "pt" -> "⚠️ Avisos"
                "ru" -> "⚠️ Предупреждения"; "pl" -> "⚠️ Ostrzeżenia"; "nl" -> "⚠️ Waarschuwingen"
                "ar" -> "⚠️ تحذيرات"; "th" -> "⚠️ คำเตือน"; "tr" -> "⚠️ Uyarılar"
                else -> "⚠️ 注意事項"
            })
            appendLine("- $warnText")
        }

        // ── Footer ──
        appendLine()
        appendLine(when (lang) {
            "en" -> "※ Personal information has been masked"
            "zh" -> "※ 个人信息已脱敏"
            "ko" -> "※ 개인정보는 마스크 처리되었습니다"
            "es" -> "※ La información personal ha sido enmascarada"
            "fr" -> "※ Les informations personnelles ont été masquées"
            "de" -> "※ Persönliche Daten wurden maskiert"
            "it" -> "※ Le informazioni personali sono state mascherate"
            "pt" -> "※ As informações pessoais foram mascaradas"
            "ru" -> "※ Персональные данные были замаскированы"
            "pl" -> "※ Dane osobowe zostały zamaskowane"
            "nl" -> "※ Persoonlijke informatie is gemaskeerd"
            "ar" -> "※ تم إخفاء المعلومات الشخصية"
            "th" -> "※ ข้อมูลส่วนบุคคลถูกปิดบัง"
            "tr" -> "※ Kişisel bilgiler maskelendi"
            else -> "※ 個人情報はマスク済みです"
        })

        // ── Original text section (omit if blank) ──
        if (maskedSourceText.isNotBlank()) {
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine(when (lang) {
                "en" -> "📄 Original Text (PII Masked)"
                "zh" -> "📄 原文（个人信息已脱敏）"
                "ko" -> "📄 원문（개인정보 마스크 처리）"
                "es" -> "📄 Texto original (información personal enmascarada)"
                "fr" -> "📄 Texte original (informations personnelles masquées)"
                "de" -> "📄 Originaltext (personenbezogene Daten maskiert)"
                "it" -> "📄 Testo originale (informazioni personali mascherate)"
                "pt" -> "📄 Texto original (informações pessoais mascaradas)"
                "ru" -> "📄 Оригинальный текст (личные данные замаскированы)"
                "pl" -> "📄 Tekst oryginalny (dane osobowe zamaskowane)"
                "nl" -> "📄 Originele tekst (persoonlijke informatie gemaskeerd)"
                "ar" -> "📄 النص الأصلي (المعلومات الشخصية مخفية)"
                "th" -> "📄 ข้อความต้นฉบับ (ข้อมูลส่วนตัวถูกปิดบัง)"
                "tr" -> "📄 Orijinal metin (kişisel bilgiler maskelendi)"
                else -> "📄 原文（個人情報マスク済み）"
            })
            appendLine(when (lang) {
                "en" -> "Please cross-check the extracted content above and correct any omissions or errors."
                "zh" -> "请与上方提取内容进行核对，补充或修正遗漏和错误。"
                "ko" -> "위에서 추출한 내용과 대조하여 누락이나 오류를 수정해 주세요."
                "es" -> "Por favor, compare con el contenido extraído arriba y corrija omisiones o errores."
                "fr" -> "Veuillez comparer avec le contenu extrait ci-dessus et corriger les omissions ou erreurs."
                "de" -> "Bitte gleichen Sie den extrahierten Inhalt oben ab und korrigieren Sie fehlende oder fehlerhafte Angaben."
                "it" -> "Si prega di verificare il contenuto estratto sopra e correggere eventuali omissioni o errori."
                "pt" -> "Por favor, compare com o conteúdo extraído acima e corrija omissões ou erros."
                "ru" -> "Пожалуйста, сверьте с извлечённым содержимым выше и исправьте любые пропуски или ошибки."
                "pl" -> "Proszę porównać z wyodrębnioną treścią powyżej i poprawić wszelkie pominięcia lub błędy."
                "nl" -> "Vergelijk dit met de bovenstaande geëxtraheerde inhoud en corrigeer eventuele omissies of fouten."
                "ar" -> "يرجى مقارنة المحتوى المستخرج أعلاه وتصحيح أي إغفالات أو أخطاء."
                "th" -> "กรุณาตรวจสอบกับเนื้อหาที่ดึงออกมาด้านบนและแก้ไขสิ่งที่ขาดหายหรือผิดพลาด"
                "tr" -> "Lütfen yukarıdaki çıkarılan içerikle karşılaştırın ve eksiklikleri veya hataları düzeltin."
                else -> "上記の抽出内容と照合し、不足・誤りがあれば補正してください。"
            })
            appendLine()
            appendLine(maskedSourceText.trimEnd())
        }
    }
}

// ─── ViewModel UI state ───────────────────────────────────────────────────────

enum class ProcessingStep(@StringRes val labelRes: Int) {
    EXTRACTING_TEXT(R.string.doc_review_step_extracting_text),
    EXTRACTING_FIELDS(R.string.doc_review_step_extracting_fields),
}

sealed class DocumentReviewUiState {
    data object Idle : DocumentReviewUiState()

    data class Processing(
        val step: ProcessingStep,
        val progress: Float,
        val partialFields: List<Pair<String, String>> = emptyList(),
    ) : DocumentReviewUiState()

    data class Error(@StringRes val messageRes: Int) : DocumentReviewUiState()

    data class Review(
        val reviewResult: ReviewResult,
        val piiSpans: List<PiiSpan>,
        val sourceText: String = "",
        val selectedLanguage: String = "en",
        val isTranslating: Boolean = false,
        val translationError: Boolean = false,
        val chatMessages: List<ChatMessage> = emptyList(),
        val chatIsGenerating: Boolean = false,
        val partialChatResponse: String? = null,
        @StringRes val chatErrorRes: Int? = null,
        val chatLimitReached: Boolean = false,
        val chatAvailable: Boolean = true,
    ) : DocumentReviewUiState()

    data class GeneratingEscalation(
        val piiSpans: List<PiiSpan>,
        val reviewResult: ReviewResult,
        val sourceText: String,
        val userNotes: String,
        val chatMessages: List<ChatMessage>,
    ) : DocumentReviewUiState()

    data class OutputPreview(val pkg: EscalationPackage) : DocumentReviewUiState()

    data class InquiryWizard(
        val reviewResult: ReviewResult,
        val piiSpans: List<PiiSpan>,
        val purposeSuggestions: List<String> = emptyList(),
        val purposeSuggestionsLoading: Boolean = false,
        val userPurpose: String = "",
        val recipient: InquiryRecipient,
        val targetLanguage: String,
        val maskedPiiSpans: List<PiiSpan> = emptyList(),
    ) : DocumentReviewUiState()

    data class InquiryPreview(
        val contextText: String,
    ) : DocumentReviewUiState()
}

// ─── Repository data classes (§8) ────────────────────────────────────────────

data class DocumentMeta(
    val docId: String,
    val docName: String,
    val importanceLevel: String, // "high" | "medium" | "low"
    val createdAt: Long,         // Unix ms
    val hasEscalation: Boolean,
    val hasInquiry: Boolean = false,
)

data class DocumentBundle(
    val reviewResult: ReviewResult,
    val sourceText: String,
    val escalationPackage: EscalationPackage?,
    val inquiryContext: InquiryContext? = null,
)

// ─── Error types ──────────────────────────────────────────────────────────────

sealed class ExtractionError : Exception() {
    data object NoPdfTextLayer : ExtractionError()
    data object UnsupportedFormat : ExtractionError()
    data class IoError(override val cause: Throwable?) : ExtractionError()
    data object OcrFailed : ExtractionError()
}

sealed class FieldExtractionError : Exception() {
    data object JsonParseError : FieldExtractionError()
    data object ModelNotInitialized : FieldExtractionError()
    data class InferenceError(override val message: String?) : FieldExtractionError()
}
