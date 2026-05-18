package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ActionItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DeadlineInfo
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.LocationEntry
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.RequiredItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.Warning
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.data.RuntimeType
import io.github.joyk0117.paperworknavigator.runtime.CleanUpListener
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import io.github.joyk0117.paperworknavigator.runtime.ResultListener
import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {

    // ─── Fakes ──────────────────────────────────────────────────────────────────

    private fun fakeLlmHelper(response: String): LlmModelHelper = object : LlmModelHelper {
        override fun initialize(
            context: Context, model: Model, taskId: String,
            supportImage: Boolean, supportAudio: Boolean, onDone: (String) -> Unit,
            systemInstruction: Contents?, tools: List<ToolProvider>,
            enableConversationConstrainedDecoding: Boolean, coroutineScope: CoroutineScope?,
        ) {}

        override fun resetConversation(
            model: Model, supportImage: Boolean, supportAudio: Boolean,
            systemInstruction: Contents?, tools: List<ToolProvider>,
            enableConversationConstrainedDecoding: Boolean,
        ) {}

        override fun cleanUp(model: Model, onDone: () -> Unit) {}

        override fun runInference(
            model: Model, input: String, resultListener: ResultListener,
            cleanUpListener: CleanUpListener, onError: (String) -> Unit,
            images: List<Bitmap>, audioClips: List<ByteArray>,
            coroutineScope: CoroutineScope?, extraContext: Map<String, String>?,
        ) {
            resultListener(response, true, null)
        }

        override fun stopResponse(model: Model) {}
    }

    private fun errorLlmHelper(errorMessage: String): LlmModelHelper = object : LlmModelHelper {
        override fun initialize(
            context: Context, model: Model, taskId: String,
            supportImage: Boolean, supportAudio: Boolean, onDone: (String) -> Unit,
            systemInstruction: Contents?, tools: List<ToolProvider>,
            enableConversationConstrainedDecoding: Boolean, coroutineScope: CoroutineScope?,
        ) {}

        override fun resetConversation(
            model: Model, supportImage: Boolean, supportAudio: Boolean,
            systemInstruction: Contents?, tools: List<ToolProvider>,
            enableConversationConstrainedDecoding: Boolean,
        ) {}

        override fun cleanUp(model: Model, onDone: () -> Unit) {}

        override fun runInference(
            model: Model, input: String, resultListener: ResultListener,
            cleanUpListener: CleanUpListener, onError: (String) -> Unit,
            images: List<Bitmap>, audioClips: List<ByteArray>,
            coroutineScope: CoroutineScope?, extraContext: Map<String, String>?,
        ) {
            onError(errorMessage)
        }

        override fun stopResponse(model: Model) {}
    }

    /** Returns responses from [responses] in order; repeats the last entry when exhausted. */
    private fun sequentialLlmHelper(vararg responses: String): LlmModelHelper {
        val queue = ArrayDeque(responses.toList())
        return object : LlmModelHelper {
            override fun initialize(
                context: Context, model: Model, taskId: String,
                supportImage: Boolean, supportAudio: Boolean, onDone: (String) -> Unit,
                systemInstruction: Contents?, tools: List<ToolProvider>,
                enableConversationConstrainedDecoding: Boolean, coroutineScope: CoroutineScope?,
            ) {}

            override fun resetConversation(
                model: Model, supportImage: Boolean, supportAudio: Boolean,
                systemInstruction: Contents?, tools: List<ToolProvider>,
                enableConversationConstrainedDecoding: Boolean,
            ) {}

            override fun cleanUp(model: Model, onDone: () -> Unit) {}

            override fun runInference(
                model: Model, input: String, resultListener: ResultListener,
                cleanUpListener: CleanUpListener, onError: (String) -> Unit,
                images: List<Bitmap>, audioClips: List<ByteArray>,
                coroutineScope: CoroutineScope?, extraContext: Map<String, String>?,
            ) {
                val response = if (queue.isNotEmpty()) queue.removeFirst() else responses.last()
                resultListener(response, true, null)
            }

            override fun stopResponse(model: Model) {}
        }
    }

    private fun modelWithInstance() = Model(
        name = "test-model",
        runtimeType = RuntimeType.LITERT_LM,
        instance = Any(),
    )

    private fun sampleReviewResult() = ReviewResult(
        docName = "児童手当現況届",
        importance = "high",
        summaryJa = "毎年6月に提出が必要な現況届です。",
        deadline = DeadlineInfo(date = "2025-06-30", noteJa = "令和7年6月30日（月）まで"),
        locations = listOf(LocationEntry(nameJa = "江戸川区役所 子育て支援課")),
        actionItems = listOf(
            ActionItem(id = "action_01", descriptionJa = "現況届を記入して提出する", priority = 1),
        ),
        requiredItems = listOf(
            RequiredItem(id = "doc_01", nameJa = "健康保険証", noteJa = "コピー可"),
            RequiredItem(id = "doc_02", nameJa = "印鑑", noteJa = null),
        ),
        warning = Warning(id = "warn_01", descriptionJa = "未提出の場合、支給が停止されます", severity = "high"),
        translation = null,
    )

    private fun sampleTranslationLines() = """
SUMMARY: This is an annual status report that must be submitted in June.
DEADLINE_NOTE: By June 30 (Mon)
ACTION_ITEMS: action_01|Fill in and submit the Status Report Form
REQUIRED_ITEMS: doc_01|Health Insurance Card|Copy accepted|||doc_02|Personal Seal|(none)
WARNING: Failure to submit will stop payments
    """.trimIndent()

    // ─── buildFieldsText tests ────────────────────────────────────────────────

    @Test
    fun buildFieldsText_includesSummaryJa() {
        val translator = Translator(fakeLlmHelper(""))
        val fieldsText = translator.buildFieldsText(sampleReviewResult())
        assertTrue(fieldsText.contains("毎年6月に提出が必要な現況届です"))
    }

    @Test
    fun buildFieldsText_includesDeadlineNoteJa() {
        val translator = Translator(fakeLlmHelper(""))
        val fieldsText = translator.buildFieldsText(sampleReviewResult())
        assertTrue(fieldsText.contains("令和7年6月30日"))
    }

    @Test
    fun buildFieldsText_includesActionItemsWithId() {
        val translator = Translator(fakeLlmHelper(""))
        val fieldsText = translator.buildFieldsText(sampleReviewResult())
        assertTrue(fieldsText.contains("action_01|現況届を記入して提出する"))
    }

    @Test
    fun buildFieldsText_includesRequiredItemsWithNullNote() {
        val translator = Translator(fakeLlmHelper(""))
        val fieldsText = translator.buildFieldsText(sampleReviewResult())
        // doc_02 has noteJa = null, so it should be encoded as (none)
        assertTrue(fieldsText.contains("doc_02|印鑑|(none)"))
    }

    // TC-03-04: pii_candidates の spanText が翻訳対象に含まれない
    @Test
    fun buildFieldsText_doesNotContainPiiSpanText() {
        val translator = Translator(fakeLlmHelper(""))
        val fieldsText = translator.buildFieldsText(sampleReviewResult())
        assertFalse(
            "PII spanText '山田太郎' must not appear in fields text",
            fieldsText.contains("山田太郎"),
        )
    }

    @Test
    fun buildFieldsText_doesNotContainConditions() {
        val translator = Translator(fakeLlmHelper(""))
        val fieldsText = translator.buildFieldsText(sampleReviewResult())
        assertFalse(fieldsText.contains("受給者が住所地に居住していること"))
    }

    @Test
    fun buildFieldsText_nullDeadlineNote_encodedAsNone() {
        val translator = Translator(fakeLlmHelper(""))
        val result = sampleReviewResult().copy(
            deadline = DeadlineInfo(date = null, noteJa = null),
        )
        val fieldsText = translator.buildFieldsText(result)
        assertTrue(fieldsText.contains("DEADLINE_NOTE: (none)"))
    }

    @Test
    fun buildFieldsText_emptyActionItems_encodedAsNone() {
        val translator = Translator(fakeLlmHelper(""))
        val result = sampleReviewResult().copy(actionItems = emptyList())
        val fieldsText = translator.buildFieldsText(result)
        assertTrue(fieldsText.contains("ACTION_ITEMS: (none)"))
    }

    // summaryJa に改行が含まれる場合、改行がスペースに置換されて1行に収まること
    @Test
    fun buildFieldsText_summaryWithNewline_replacedWithSpace() {
        val translator = Translator(fakeLlmHelper(""))
        val result = sampleReviewResult().copy(summaryJa = "1行目の説明。\n2行目の説明。")
        val fieldsText = translator.buildFieldsText(result)
        // 改行がスペースになり SUMMARY が1行に収まる
        assertTrue(fieldsText.contains("SUMMARY: 1行目の説明。 2行目の説明。"))
        // 改行が残っていないこと（SUMMARY 行が分断されない）
        val summaryLine = fieldsText.lines().first { it.startsWith("SUMMARY:") }
        assertEquals("SUMMARY: 1行目の説明。 2行目の説明。", summaryLine)
    }

    // summaryJa の改行が SUMMARY フィールドとして正しくパースされること
    @Test
    fun parseTranslationLines_summaryWithNewlineInSource_parsedCorrectly() {
        val translator = Translator(fakeLlmHelper(""))
        // buildFieldsText で改行がスペース変換された後、LLM が翻訳して返す想定の出力
        val raw = """
SUMMARY: First sentence. Second sentence.
DEADLINE_NOTE: (none)
ACTION_ITEMS: (none)
REQUIRED_ITEMS: (none)
WARNING: (none)
        """.trimIndent()
        val translation = translator.parseTranslationLines(raw, "en")
        assertEquals("First sentence. Second sentence.", translation.summary)
    }

    // ─── parseTranslationLines tests ─────────────────────────────────────────

    @Test
    fun parseTranslationLines_parsesAllFields() {
        val translator = Translator(fakeLlmHelper(""))
        val translation = translator.parseTranslationLines(sampleTranslationLines(), "en")
        assertEquals("en", translation.language)
        assertEquals("This is an annual status report that must be submitted in June.", translation.summary)
        assertEquals("By June 30 (Mon)", translation.deadlineNote)
        assertEquals(1, translation.actionItems.size)
        assertEquals("action_01", translation.actionItems[0].id)
        assertEquals("Fill in and submit the Status Report Form", translation.actionItems[0].description)
        assertEquals(2, translation.requiredItems.size)
        assertEquals("doc_01", translation.requiredItems[0].id)
        assertEquals("Health Insurance Card", translation.requiredItems[0].name)
        assertEquals("Copy accepted", translation.requiredItems[0].note)
        assertNull(translation.requiredItems[1].note)
        assertNotNull(translation.warning)
        assertEquals("Failure to submit will stop payments", translation.warning?.description)
    }

    @Test
    fun parseTranslationLines_noneDeadlineNote_yieldsNull() {
        val translator = Translator(fakeLlmHelper(""))
        val raw = """
SUMMARY: A summary.
DEADLINE_NOTE: (none)
ACTION_ITEMS: (none)
REQUIRED_ITEMS: (none)
WARNING: (none)
        """.trimIndent()
        val translation = translator.parseTranslationLines(raw, "en")
        assertNull(translation.deadlineNote)
        assertTrue(translation.actionItems.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun parseTranslationLines_throwsOnMissingSummary() {
        val translator = Translator(fakeLlmHelper(""))
        translator.parseTranslationLines("DEADLINE_NOTE: (none)", "en")
    }

    @Test
    fun parseTranslationLines_summaryWithColonPreserved() {
        val translator = Translator(fakeLlmHelper(""))
        val raw = "SUMMARY: Status: Important document\nDEADLINE_NOTE: (none)\nACTION_ITEMS: (none)\nREQUIRED_ITEMS: (none)\nWARNING: (none)"
        val translation = translator.parseTranslationLines(raw, "en")
        assertEquals("Status: Important document", translation.summary)
    }

    // ─── translate() tests ────────────────────────────────────────────────────

    @Test
    fun translate_setsTranslationLanguage() = runBlocking {
        val translator = Translator(fakeLlmHelper(sampleTranslationLines()))
        val result = translator.translate(modelWithInstance(), sampleReviewResult(), "en")
        assertEquals("en", result.translation?.language)
    }

    // TC-03-01: English 翻訳で Translation オブジェクトが正常生成される
    @Test
    fun translate_returnsNonNullTranslation_forEnglish() = runBlocking {
        val translator = Translator(fakeLlmHelper(sampleTranslationLines()))
        val result = translator.translate(modelWithInstance(), sampleReviewResult(), "en")
        assertNotNull(result.translation)
        assertTrue(result.translation!!.summary.isNotEmpty())
    }

    // TC-03-02: translation.language がリクエストした言語コードと一致（各言語）
    @Test
    fun translate_translationLanguageMatchesRequest_ja() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "ja")
        assertEquals("ja", result.translation?.language)
    }

    @Test
    fun translate_translationLanguageMatchesRequest_en() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "en")
        assertEquals("en", result.translation?.language)
    }

    @Test
    fun translate_translationLanguageMatchesRequest_zh() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "zh")
        assertEquals("zh", result.translation?.language)
    }

    @Test
    fun translate_translationLanguageMatchesRequest_ko() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "ko")
        assertEquals("ko", result.translation?.language)
    }

    @Test
    fun translate_translationLanguageMatchesRequest_es() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "es")
        assertEquals("es", result.translation?.language)
    }

    @Test
    fun translate_translationLanguageMatchesRequest_hi() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "hi")
        assertEquals("hi", result.translation?.language)
    }

    @Test
    fun translate_translationLanguageMatchesRequest_fr() = runBlocking {
        val result = Translator(fakeLlmHelper(sampleTranslationLines()))
            .translate(modelWithInstance(), sampleReviewResult(), "fr")
        assertEquals("fr", result.translation?.language)
    }

    // TC-03-03: action_items の件数が元データと一致
    @Test
    fun translate_actionItemCountMatchesOriginal() = runBlocking {
        val translator = Translator(fakeLlmHelper(sampleTranslationLines()))
        val reviewResult = sampleReviewResult()
        val result = translator.translate(modelWithInstance(), reviewResult, "en")
        assertEquals(reviewResult.actionItems.size, result.translation?.actionItems?.size)
    }

    // TC-03-04: 翻訳対象外フィールドが変更されない (warning の severity)
    @Test
    fun translate_preservesWarningSeverityUnchanged() = runBlocking {
        val translator = Translator(fakeLlmHelper(sampleTranslationLines()))
        val reviewResult = sampleReviewResult()
        val result = translator.translate(modelWithInstance(), reviewResult, "en")
        assertEquals(reviewResult.warning?.severity, result.warning?.severity)
    }

    @Test
    fun translate_preservesActionItemIdsUnchanged() = runBlocking {
        val translator = Translator(fakeLlmHelper(sampleTranslationLines()))
        val reviewResult = sampleReviewResult()
        val result = translator.translate(modelWithInstance(), reviewResult, "en")
        assertEquals(reviewResult.actionItems.map { it.id }, result.actionItems.map { it.id })
    }

    // TC-03-03: required_items.note_ja が null のとき translation.note が null
    @Test
    fun translate_nullNoteJa_yieldsNullTranslationNote() = runBlocking {
        val translator = Translator(fakeLlmHelper(sampleTranslationLines()))
        val result = translator.translate(modelWithInstance(), sampleReviewResult(), "en")
        val item02 = result.translation?.requiredItems?.find { it.id == "doc_02" }
        assertNull(item02?.note)
    }

    // TC-03-05: 翻訳失敗時に例外がスローされる (InferenceError)
    @Test(expected = TranslationError.InferenceError::class)
    fun translate_throwsInferenceError_onLlmFailure() {
        runBlocking {
            Translator(errorLlmHelper("LLM failed"))
                .translate(modelWithInstance(), sampleReviewResult(), "en")
        }
    }

    // TC-03-05: パースエラー時に最大3回試行後 TranslationError.JsonParseError がスローされる
    @Test(expected = TranslationError.JsonParseError::class)
    fun translate_throwsJsonParseError_onInvalidLines() {
        runBlocking {
            Translator(fakeLlmHelper("NOT VALID LINES"))
                .translate(modelWithInstance(), sampleReviewResult(), "en")
        }
    }

    // リトライ：1回目失敗・2回目成功 → 正常に Translation が返る
    @Test
    fun translate_retriesOnParseFailure_andSucceedsOnSecondAttempt() = runBlocking {
        val translator = Translator(sequentialLlmHelper("NOT VALID LINES", sampleTranslationLines()))
        val result = translator.translate(modelWithInstance(), sampleReviewResult(), "en")
        assertNotNull(result.translation)
        assertEquals("en", result.translation?.language)
    }

    // リトライ：1・2回目失敗・3回目成功 → 正常に Translation が返る（最大リトライ回数の境界）
    @Test
    fun translate_retriesOnParseFailure_andSucceedsOnThirdAttempt() = runBlocking {
        val translator = Translator(
            sequentialLlmHelper("NOT VALID LINES", "NOT VALID LINES", sampleTranslationLines()),
        )
        val result = translator.translate(modelWithInstance(), sampleReviewResult(), "en")
        assertNotNull(result.translation)
        assertEquals("en", result.translation?.language)
    }

    // リトライ：常に失敗する場合、runInference がちょうど MAX_ATTEMPTS（3）回呼ばれること
    @Test
    fun translate_callsRunInferenceExactlyMaxAttempts_whenAlwaysFailing() {
        var callCount = 0
        val countingHelper = object : LlmModelHelper {
            override fun initialize(
                context: Context, model: Model, taskId: String,
                supportImage: Boolean, supportAudio: Boolean, onDone: (String) -> Unit,
                systemInstruction: Contents?, tools: List<ToolProvider>,
                enableConversationConstrainedDecoding: Boolean, coroutineScope: CoroutineScope?,
            ) {}

            override fun resetConversation(
                model: Model, supportImage: Boolean, supportAudio: Boolean,
                systemInstruction: Contents?, tools: List<ToolProvider>,
                enableConversationConstrainedDecoding: Boolean,
            ) {}

            override fun cleanUp(model: Model, onDone: () -> Unit) {}

            override fun runInference(
                model: Model, input: String, resultListener: ResultListener,
                cleanUpListener: CleanUpListener, onError: (String) -> Unit,
                images: List<Bitmap>, audioClips: List<ByteArray>,
                coroutineScope: CoroutineScope?, extraContext: Map<String, String>?,
            ) {
                callCount++
                resultListener("NOT VALID LINES", true, null)
            }

            override fun stopResponse(model: Model) {}
        }

        try {
            runBlocking {
                Translator(countingHelper).translate(modelWithInstance(), sampleReviewResult(), "en")
            }
        } catch (_: TranslationError.JsonParseError) {
            // 期待通りの例外
        }

        assertEquals("runInference should be called exactly MAX_ATTEMPTS (3) times", 3, callCount)
    }

    // TC-03-05: ModelNotInitialized — model.instance == null のとき例外がスローされる
    @Test(expected = TranslationError.ModelNotInitialized::class)
    fun translate_throwsModelNotInitialized_whenInstanceNull() {
        runBlocking {
            val model = Model(name = "test", runtimeType = RuntimeType.LITERT_LM, instance = null)
            Translator(fakeLlmHelper(sampleTranslationLines()))
                .translate(model, sampleReviewResult(), "en")
        }
    }

    // ─── PromptBuilder MF-03 tests ────────────────────────────────────────────

    @Test
    fun mf03SystemPrompt_containsTargetLanguage() {
        val prompt = PromptBuilder.mf03SystemPrompt("ja", "en")
        assertTrue(prompt.contains("English"))
    }

    @Test
    fun mf03SystemPrompt_containsLineFormatInstructions() {
        val prompt = PromptBuilder.mf03SystemPrompt("ja", "en")
        assertTrue(prompt.contains("SUMMARY"))
        assertTrue(prompt.contains("DEADLINE_NOTE"))
        assertTrue(prompt.contains("ACTION_ITEMS"))
        assertTrue(prompt.contains("REQUIRED_ITEMS"))
        assertTrue(prompt.contains("WARNING"))
    }

    @Test
    fun mf03UserMessage_containsFieldsText() {
        val fields = "SUMMARY: テスト\nDEADLINE_NOTE: (none)"
        val msg = PromptBuilder.mf03UserMessage(fields, "ja", "en")
        assertTrue(msg.contains(fields))
        assertTrue(msg.contains("English"))
    }

    @Test
    fun mf03UserMessage_withParseError_containsErrorMessage() {
        val fields = "SUMMARY: テスト\nDEADLINE_NOTE: (none)"
        val msg = PromptBuilder.mf03UserMessage(fields, "ja", "en", parseError = "SUMMARY missing")
        assertTrue(msg.contains("SUMMARY missing"))
        assertTrue(msg.contains("parse error"))
    }

    @Test
    fun mf07InitialMessage_containsDocName_forEs() {
        val msg = PromptBuilder.mf07InitialMessage("児童手当現況届", "es")
        assertTrue(msg.contains("児童手当現況届"))
        assertTrue(msg.contains("Hola"))
    }

    @Test
    fun mf07InitialMessage_containsDocName_forUnknownLanguage_fallsBackToEnglish() {
        // "hi" (Hindi) is not in the supported language list; falls back to English
        val msg = PromptBuilder.mf07InitialMessage("児童手当現況届", "hi")
        assertTrue(msg.contains("児童手当現況届"))
        assertTrue(msg.contains("Hello"))
    }

    @Test
    fun mf07InitialMessage_containsDocName_forFr() {
        val msg = PromptBuilder.mf07InitialMessage("児童手当現況届", "fr")
        assertTrue(msg.contains("児童手当現況届"))
        assertTrue(msg.contains("Bonjour"))
    }

    @Test
    fun languageCodeToLabel_mapsAllLanguages() {
        assertEquals("Japanese", PromptBuilder.languageCodeToLabel("ja"))
        assertEquals("English", PromptBuilder.languageCodeToLabel("en"))
        assertEquals("Chinese (Simplified)", PromptBuilder.languageCodeToLabel("zh"))
        assertEquals("Korean", PromptBuilder.languageCodeToLabel("ko"))
        assertEquals("Spanish", PromptBuilder.languageCodeToLabel("es"))
        assertEquals("French", PromptBuilder.languageCodeToLabel("fr"))
        // Unsupported languages return the code itself as fallback
        assertEquals("hi", PromptBuilder.languageCodeToLabel("hi"))
    }
}
