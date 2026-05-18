package io.github.joyk0117.paperworknavigator.customtasks.documentreview.model

/**
 * Single source of truth for all languages supported by the Document Review feature.
 *
 * Adding a new language here automatically propagates to:
 *  - Translation bar dropdown (DocumentReviewScreen)
 *  - Inquiry wizard language selector (DocumentReviewScreen)
 *  - LLM prompt labels (PromptBuilder.languageCodeToLabel)
 *
 * UI strings (categoryLabel, importanceLabelFor, toContextText) still use
 * when(lang) blocks keyed by code — add a branch there when adding a language.
 */
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val llmLabel: String,
    /** True if ML Kit Entity Extraction supports this language. ru/ar/th are excluded. */
    val supportsEntityExtraction: Boolean = true,
    /** True if ML Kit Text Recognition has a dedicated or Latin OCR model for this language. ru/ar/th are excluded. */
    val supportsOcr: Boolean = true,
) {
    EN("en", "English", "English"),
    JA("ja", "日本語", "Japanese"),
    ZH("zh", "中文", "Chinese (Simplified)"),
    KO("ko", "한국어", "Korean"),
    ES("es", "Español", "Spanish"),
    FR("fr", "Français", "French"),
    DE("de", "Deutsch", "German"),
    IT("it", "Italiano", "Italian"),
    PT("pt", "Português", "Portuguese"),
    RU("ru", "Русский", "Russian", supportsEntityExtraction = false, supportsOcr = false),
    PL("pl", "Polski", "Polish"),
    NL("nl", "Nederlands", "Dutch"),
    AR("ar", "العربية", "Arabic", supportsEntityExtraction = false, supportsOcr = false),
    TH("th", "ภาษาไทย", "Thai", supportsEntityExtraction = false, supportsOcr = false),
    TR("tr", "Türkçe", "Turkish");

    companion object {
        fun fromCode(code: String): SupportedLanguage? = entries.find { it.code == code }
    }
}
