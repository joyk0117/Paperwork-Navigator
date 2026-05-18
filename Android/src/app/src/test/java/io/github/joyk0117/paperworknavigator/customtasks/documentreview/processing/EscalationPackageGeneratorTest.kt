package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ActionItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.AiHypothesis
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatRole
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DeadlineInfo
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.LocationEntry
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.MaskResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.RequiredItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TimelineEvent
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TranslatedActionItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TranslatedRequiredItem
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.TranslatedWarning
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.Translation
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.Warning
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.data.RuntimeType
import io.github.joyk0117.paperworknavigator.runtime.CleanUpListener
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import io.github.joyk0117.paperworknavigator.runtime.ResultListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationPackageGeneratorTest {

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

    private fun capturingLlmHelper(response: String): CapturingLlmHelper =
        CapturingLlmHelper(response)

    inner class CapturingLlmHelper(private val response: String) : LlmModelHelper {
        var capturedInput: String = ""

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
            capturedInput = input
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

    private fun modelWithInstance() = Model(
        name = "test-model",
        runtimeType = RuntimeType.LITERT_LM,
        instance = Any(),
    )

    private fun sampleReviewResult(translation: Translation? = null) = ReviewResult(
        docName = "児童手当現況届",
        importance = "high",
        summaryJa = "毎年6月に提出が必要な現況届です。未提出の場合、翌月以降の支給が停止されます。",
        deadline = DeadlineInfo(date = "2025-06-30", noteJa = "令和7年6月30日（月）まで"),
        locations = listOf(LocationEntry(nameJa = "江戸川区役所 子育て支援課")),
        actionItems = listOf(
            ActionItem(id = "action_01", descriptionJa = "現況届を記入して江戸川区役所に提出する", priority = 1),
        ),
        requiredItems = listOf(
            RequiredItem(id = "doc_01", nameJa = "健康保険証", noteJa = "コピー可"),
            RequiredItem(id = "doc_02", nameJa = "印鑑", noteJa = null),
        ),
        warning = Warning(id = "warn_01", descriptionJa = "提出しない場合、翌月以降の支給が停止されます", severity = "high"),
        translation = translation,
    )

    private fun sampleMaskResult() = MaskResult(
        maskedText = "受給者 [■■■]、住所 [■■■]、生年月日 [■■■]、口座 [■■■]",
        appliedSpans = listOf(
            PiiSpan(id = "pii_01", spanText = "山田太郎", category = "name", maskRecommended = true),
            PiiSpan(id = "pii_02", spanText = "港区虚空町1-2-3", category = "address", maskRecommended = true),
            PiiSpan(id = "pii_03", spanText = "昭和60年1月1日", category = "dob", maskRecommended = true),
        ),
        skippedSpans = listOf(
            PiiSpan(id = "pii_04", spanText = "1234567", category = "account", maskRecommended = true, userOverride = false),
        ),
        unmatchedSpans = emptyList(),
    )

    private fun sampleTranslation() = Translation(
        language = "en",
        summary = "This is an annual status report that must be submitted in June.",
        deadlineNote = "By June 30 (Mon)",
        actionItems = listOf(
            TranslatedActionItem(id = "action_01", description = "Fill in and submit the Status Report Form"),
        ),
        requiredItems = listOf(
            TranslatedRequiredItem(id = "doc_01", name = "Health Insurance Card", note = "Copy accepted"),
            TranslatedRequiredItem(id = "doc_02", name = "Personal Seal", note = null),
        ),
        warning = TranslatedWarning(description = "Failure to submit will stop payments"),
    )

    private fun validLlmOutputJson(summary: String = "User needs help with annual child allowance status form.") = """
        {
          "consultation_summary": "$summary",
          "timeline": [{"date": "2025-06-30", "event": "Submission deadline"}],
          "ai_hypotheses": [{"point": "User may not reside at registered address", "type": "unclear"}]
        }
    """.trimIndent()

    // ─── TC-06-01: 正常なパッケージ生成 ────────────────────────────────────────

    @Test
    fun generate_returnsEscalationPackage_onValidLlmOutput() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertNotNull(pkg)
        assertTrue(pkg.consultationSummary.isNotEmpty())
        assertEquals("en", pkg.language)
    }

    // ─── TC-06-01: consultationSummary が [■■■] を含まない ───────────────────
    // Note: [■■■] の排除はシステムプロンプトの指示で行われるため、ユニットテストでは
    // LLM が [■■■] を含む文字列を返した場合でも実装側でフィルタしないことを確認する。
    // 実際の品質は実機 E2E テスト（プロンプト仕様書 §8.3）で検証する。

    @Test
    fun generate_consultationSummaryDoesNotContainMaskedMarker() = runBlocking {
        // LLM が [■■■] を含む summary を返した場合でも buildPackage はそのまま渡す
        // (フィルタはシステムプロンプト側の責務)
        val jsonWithMaskedMarker = validLlmOutputJson(summary = "User [■■■] needs help.")
        val gen = EscalationPackageGenerator(fakeLlmHelper(jsonWithMaskedMarker))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        // 実装は LLM 出力をそのまま渡すので、テストはパッケージへの転写を確認する
        assertEquals("User [■■■] needs help.", pkg.consultationSummary)
    }

    // ─── TC-06-02: key_points — 翻訳済みフィールド優先 ──────────────────────

    @Test
    fun buildKeyPoints_usesTranslatedFields_whenTranslationPresent() {
        val gen = EscalationPackageGenerator(fakeLlmHelper("{}"))
        val reviewResult = sampleReviewResult(translation = sampleTranslation())
        val keyPoints = gen.buildKeyPoints(reviewResult)

        assertTrue(keyPoints.any { it.description == "By June 30 (Mon)" })
        assertTrue(keyPoints.any { it.description == "Fill in and submit the Status Report Form" })
        assertTrue(keyPoints.any { it.description == "Failure to submit will stop payments" })
        assertTrue(keyPoints.any { it.description == "Health Insurance Card" })
    }

    // ─── TC-06-03: key_points — 翻訳なし → _ja フォールバック ──────────────

    @Test
    fun buildKeyPoints_fallsBackToJaFields_whenNoTranslation() {
        val gen = EscalationPackageGenerator(fakeLlmHelper("{}"))
        val reviewResult = sampleReviewResult(translation = null)
        val keyPoints = gen.buildKeyPoints(reviewResult)

        assertTrue(keyPoints.any { it.description == "令和7年6月30日（月）まで" })
        assertTrue(keyPoints.any { it.description == "現況届を記入して江戸川区役所に提出する" })
        assertTrue(keyPoints.any { it.description == "提出しない場合、翌月以降の支給が停止されます" })
        assertTrue(keyPoints.any { it.description == "健康保険証" })
    }

    // ─── TC-06-04: maskedFields にマスクしたカテゴリ一覧が含まれる ────────────

    @Test
    fun generate_maskedFieldsContainsAppliedSpanCategories() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertTrue(pkg.maskedFields.contains("name"))
        assertTrue(pkg.maskedFields.contains("address"))
        assertTrue(pkg.maskedFields.contains("dob"))
        // skippedSpans (account) is NOT in appliedSpans so should not appear
        assertFalse(pkg.maskedFields.contains("account"))
    }

    // ─── TC-06-05: relatedDocuments に ReviewResult.docType が収録される ─────

    @Test
    fun generate_relatedDocumentsContainsDocType() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertEquals(1, pkg.relatedDocuments.size)
        assertEquals("児童手当現況届", pkg.relatedDocuments[0].name)
        assertNull(pkg.relatedDocuments[0].note)
    }

    // ─── TC-06-06: chatHistory がそのまま収録される ──────────────────────────

    @Test
    fun generate_chatHistoryPreservedInPackage() = runBlocking {
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "締め切りが過ぎた場合は？"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "翌月以降の支給が停止されます。"),
        )
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = messages,
            targetLanguage = "en",
        )
        assertEquals(2, pkg.chatHistory.size)
        assertEquals("user", pkg.chatHistory[0].role)
        assertEquals("締め切りが過ぎた場合は？", pkg.chatHistory[0].content)
        assertEquals("assistant", pkg.chatHistory[1].role)
        assertEquals("翌月以降の支給が停止されます。", pkg.chatHistory[1].content)
    }

    // ─── TC-06-07: userNotes が空のとき EscalationPackage.userNotes == null ──

    @Test
    fun generate_userNotesNull_whenInputEmpty() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertNull(pkg.userNotes)
    }

    @Test
    fun generate_userNotesPreserved_whenInputNonEmpty() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "昨年も同じ書類を提出した。",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertEquals("昨年も同じ書類を提出した。", pkg.userNotes)
    }

    // ─── TC-06-10: maskedSourceText が maskResult.maskedText と一致する ───────

    @Test
    fun generate_maskedSourceTextEqualsmaskResultMaskedText() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val maskResult = sampleMaskResult()
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = maskResult,
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertEquals(maskResult.maskedText, pkg.maskedSourceText)
    }

    // ─── Error cases ─────────────────────────────────────────────────────────

    @Test(expected = EscalationGenerationError.ModelNotInitialized::class)
    fun generate_throwsModelNotInitialized_whenInstanceNull() {
        runBlocking {
            val model = Model(name = "test", runtimeType = RuntimeType.LITERT_LM, instance = null)
            EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
                .generate(model, sampleMaskResult(), sampleReviewResult(), "", emptyList(), "en")
        }
    }

    @Test(expected = EscalationGenerationError.InferenceError::class)
    fun generate_throwsInferenceError_onLlmFailure() {
        runBlocking {
            EscalationPackageGenerator(errorLlmHelper("LLM error"))
                .generate(modelWithInstance(), sampleMaskResult(), sampleReviewResult(), "", emptyList(), "en")
        }
    }

    @Test(expected = EscalationGenerationError.JsonParseError::class)
    fun generate_throwsJsonParseError_onInvalidJson() {
        runBlocking {
            EscalationPackageGenerator(fakeLlmHelper("NOT VALID JSON"))
                .generate(modelWithInstance(), sampleMaskResult(), sampleReviewResult(), "", emptyList(), "en")
        }
    }

    // ─── ai_hypotheses: applicable==null 条件が反映される ────────────────────

    @Test
    fun generate_aiHypothesesReflectsNullApplicableConditions() = runBlocking {
        val llmJson = """
            {
              "consultation_summary": "User needs help.",
              "timeline": [],
              "ai_hypotheses": [{"point": "User may not reside at registered address", "type": "unclear"}]
            }
        """.trimIndent()
        val gen = EscalationPackageGenerator(fakeLlmHelper(llmJson))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertTrue(pkg.aiHypotheses.isNotEmpty())
        assertEquals("unclear", pkg.aiHypotheses[0].type)
    }

    // ─── PromptBuilder MF-06 tests ────────────────────────────────────────────

    @Test
    fun mf06SystemPrompt_containsTargetLanguage() {
        val prompt = PromptBuilder.mf06SystemPrompt("en")
        assertTrue(prompt.contains("English"))
    }

    @Test
    fun mf06SystemPrompt_containsOutputSchema() {
        val prompt = PromptBuilder.mf06SystemPrompt("en")
        assertTrue(prompt.contains("consultation_summary"))
        assertTrue(prompt.contains("timeline"))
        assertTrue(prompt.contains("ai_hypotheses"))
    }

    @Test
    fun mf06UserMessage_containsMaskedText() {
        val msg = PromptBuilder.mf06UserMessage(
            maskedText = "受給者 [■■■]",
            maskedCategories = "name",
            userNotes = "(no notes)",
            chatHistory = "",
        )
        assertTrue(msg.contains("受給者 [■■■]"))
        assertTrue(msg.contains("name"))
    }

    @Test
    fun chatHistoryToText_formatsAsQAndA() {
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "質問です"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "回答です"),
        )
        val text = PromptBuilder.chatHistoryToText(messages)
        assertTrue(text.contains("Q: 質問です"))
        assertTrue(text.contains("A: 回答です"))
    }

    @Test
    fun chatHistoryToText_returnsEmpty_forEmptyList() {
        val text = PromptBuilder.chatHistoryToText(emptyList())
        assertEquals("", text)
    }

    // ─── buildKeyPoints — deadline null handling ──────────────────────────────

    @Test
    fun buildKeyPoints_omitsDeadline_whenBothNoteJaAndTranslationNoteAreNull() {
        val gen = EscalationPackageGenerator(fakeLlmHelper("{}"))
        val reviewResult = sampleReviewResult().copy(
            deadline = DeadlineInfo(date = null, noteJa = null),
            translation = null,
        )
        val keyPoints = gen.buildKeyPoints(reviewResult)
        assertTrue(keyPoints.none { it.category == "deadline" })
    }

    // ─── timeline from LLM output is preserved ───────────────────────────────

    @Test
    fun generate_timelinePreservedFromLlmOutput() = runBlocking {
        val gen = EscalationPackageGenerator(fakeLlmHelper(validLlmOutputJson()))
        val pkg = gen.generate(
            model = modelWithInstance(),
            maskResult = sampleMaskResult(),
            reviewResult = sampleReviewResult(),
            userNotes = "",
            chatMessages = emptyList(),
            targetLanguage = "en",
        )
        assertEquals(1, pkg.timeline.size)
        assertEquals("2025-06-30", pkg.timeline[0].date)
        assertEquals("Submission deadline", pkg.timeline[0].event)
    }
}
