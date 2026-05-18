package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.CleanUpListener
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import io.github.joyk0117.paperworknavigator.runtime.ResultListener
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldExtractorTest {

    // parseLineFormat() は LlmModelHelper を呼ばないが、FieldExtractor のコンストラクタに必要
    private val noOpHelper: LlmModelHelper = object : LlmModelHelper {
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
        ) {}
        override fun stopResponse(model: Model) {}
    }

    private val extractor = FieldExtractor(noOpHelper)

    // ── DOC_NAME ────────────────────────────────────────────────────────────────

    @Test
    fun `DOC_NAME が正しく取れる`() {
        val raw = buildRaw(docName = "児童手当現況届")
        val result = extractor.parseLineFormat(raw)
        assertEquals("児童手当現況届", result.docName)
    }

    // ── ISSUER_NAME / APPLICANT_NAME / OTHER_NAME ────────────────────────────

    @Test
    fun `APPLICANT_NAME が正しく取れる`() {
        val raw = buildRaw(applicantName = "山田太郎")
        val result = extractor.parseLineFormat(raw)
        assertEquals("山田太郎", result.applicantName)
    }

    @Test
    fun `ISSUER_NAME が none のとき null になる`() {
        val raw = buildRaw(issuerName = "(none)")
        val result = extractor.parseLineFormat(raw)
        assertNull(result.issuerName)
    }

    @Test
    fun `APPLICANT_NAME が none のとき null になる`() {
        val raw = buildRaw(applicantName = "(none)")
        val result = extractor.parseLineFormat(raw)
        assertNull(result.applicantName)
    }

    @Test
    fun `OTHER_NAME が none のとき null になる`() {
        val raw = buildRaw(otherName = "(none)")
        val result = extractor.parseLineFormat(raw)
        assertNull(result.otherName)
    }

    @Test
    fun `ISSUER_NAME が正しく取れる`() {
        val raw = buildRaw(issuerName = "江戸川区役所")
        val result = extractor.parseLineFormat(raw)
        assertEquals("江戸川区役所", result.issuerName)
    }

    // ── IMPORTANCE ────────────────────────────────────────────────────────────

    @Test
    fun `IMPORTANCE が正しく取れる`() {
        val raw = buildRaw(importance = "high")
        val result = extractor.parseLineFormat(raw)
        assertEquals("high", result.importance)
    }

    @Test
    fun `IMPORTANCE が none のとき medium にフォールバック`() {
        val raw = buildRaw(importance = "(none)")
        val result = extractor.parseLineFormat(raw)
        assertEquals("medium", result.importance)
    }

    // ── SUMMARY ───────────────────────────────────────────────────────────────

    @Test
    fun `SUMMARY が正しく取れる`() {
        val raw = buildRaw(summary = "毎年6月に提出が必要な現況届です。")
        val result = extractor.parseLineFormat(raw)
        assertEquals("毎年6月に提出が必要な現況届です。", result.summaryJa)
    }

    // ── ACTION_ITEMS ──────────────────────────────────────────────────────────

    @Test
    fun `ACTION_ITEMS が複数件パースされる`() {
        val raw = buildRaw(actionItems = "書類を記入する|||区役所に提出する")
        val result = extractor.parseLineFormat(raw)
        assertEquals(2, result.actionItems.size)
        assertEquals("書類を記入する", result.actionItems[0].descriptionJa)
        assertEquals("区役所に提出する", result.actionItems[1].descriptionJa)
    }

    @Test
    fun `ACTION_ITEMS が none のとき空リスト`() {
        val raw = buildRaw(actionItems = "(none)")
        val result = extractor.parseLineFormat(raw)
        assertTrue(result.actionItems.isEmpty())
    }

    @Test
    fun `ACTION_ITEMS の ID が action_01 始まりの連番`() {
        val raw = buildRaw(actionItems = "アクション1|||アクション2")
        val result = extractor.parseLineFormat(raw)
        assertEquals("action_01", result.actionItems[0].id)
        assertEquals("action_02", result.actionItems[1].id)
    }

    // ── REQUIRED_ITEMS ────────────────────────────────────────────────────────

    @Test
    fun `REQUIRED_ITEMS が name と note に分離される`() {
        val raw = buildRaw(requiredItems = "健康保険証|コピー可")
        val result = extractor.parseLineFormat(raw)
        assertEquals(1, result.requiredItems.size)
        assertEquals("健康保険証", result.requiredItems[0].nameJa)
        assertEquals("コピー可", result.requiredItems[0].noteJa)
    }

    @Test
    fun `REQUIRED_ITEMS の note が none のとき null になる`() {
        val raw = buildRaw(requiredItems = "印鑑|(none)")
        val result = extractor.parseLineFormat(raw)
        assertNull(result.requiredItems[0].noteJa)
    }

    @Test
    fun `REQUIRED_ITEMS が複数件パースされる`() {
        val raw = buildRaw(requiredItems = "健康保険証|コピー可|||印鑑|(none)")
        val result = extractor.parseLineFormat(raw)
        assertEquals(2, result.requiredItems.size)
    }

    // ── WARNING ───────────────────────────────────────────────────────────────

    @Test
    fun `WARNING が severity と description に分離される`() {
        val raw = buildRaw(warning = "high|未提出の場合、支給が停止されます")
        val result = extractor.parseLineFormat(raw)
        assertNotNull(result.warning)
        assertEquals("high", result.warning?.severity)
        assertEquals("未提出の場合、支給が停止されます", result.warning?.descriptionJa)
    }

    @Test
    fun `WARNING が none のとき null になる`() {
        val raw = buildRaw(warning = "(none)")
        val result = extractor.parseLineFormat(raw)
        assertNull(result.warning)
    }

    // ── sourceLanguage ────────────────────────────────────────────────────────

    @Test
    fun `sourceLanguage が引数として渡される`() {
        val raw = buildRaw()
        val result = extractor.parseLineFormat(raw, sourceLanguage = "ja")
        assertEquals("ja", result.sourceLanguage)
    }

    // ── ヘルパー ─────────────────────────────────────────────────────────────

    private fun buildRaw(
        docName: String = "テスト書類",
        issuerName: String = "(none)",
        applicantName: String = "(none)",
        otherName: String = "(none)",
        importance: String = "medium",
        summary: String = "テスト用書類です。",
        actionItems: String = "(none)",
        requiredItems: String = "(none)",
        warning: String = "(none)",
    ): String = """
DOC_NAME: $docName
ISSUER_NAME: $issuerName
APPLICANT_NAME: $applicantName
OTHER_NAME: $otherName
IMPORTANCE: $importance
SUMMARY: $summary
ACTION_ITEMS: $actionItems
REQUIRED_ITEMS: $requiredItems
WARNING: $warning
    """.trimIndent()
}
