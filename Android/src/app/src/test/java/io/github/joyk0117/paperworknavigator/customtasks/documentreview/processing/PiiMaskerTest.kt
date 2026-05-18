package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiMaskerTest {

    private val mask = PiiMasker.MASK_TOKEN

    private fun span(
        id: String = "pii_01",
        spanText: String,
        maskRecommended: Boolean = true,
        userOverride: Boolean? = null,
        category: String = "name",
        sourceField: String? = null,
    ) = PiiSpan(
        id = id,
        spanText = spanText,
        category = category,
        maskRecommended = maskRecommended,
        userOverride = userOverride,
        sourceField = sourceField,
    )

    // ── TC-05-01: デフォルトマスク（maskRecommended=true, userOverride=null） ────

    @Test
    fun mask_replacesSpanText_whenMaskRecommendedAndNoOverride() {
        val text = "受給者氏名: 山田太郎"
        val result = PiiMasker.mask(text, listOf(span(spanText = "山田太郎")))
        assertTrue(result.maskedText.contains(mask))
        assertFalse(result.maskedText.contains("山田太郎"))
        assertEquals(1, result.appliedSpans.size)
    }

    // ── TC-05-02: userOverride=true（強制マスク） ──────────────────────────────

    @Test
    fun mask_forcesApply_whenUserOverrideTrue() {
        val text = "受給者氏名: 山田太郎"
        val s = span(spanText = "山田太郎", maskRecommended = false, userOverride = true)
        val result = PiiMasker.mask(text, listOf(s))
        assertTrue(result.maskedText.contains(mask))
        assertEquals(1, result.appliedSpans.size)
        assertTrue(result.skippedSpans.isEmpty())
    }

    // ── TC-05-03: userOverride=false（強制除外） ───────────────────────────────

    @Test
    fun mask_skipsSpan_whenUserOverrideFalse() {
        val text = "受給者氏名: 山田太郎"
        val s = span(spanText = "山田太郎", maskRecommended = true, userOverride = false)
        val result = PiiMasker.mask(text, listOf(s))
        assertFalse(result.maskedText.contains(mask))
        assertTrue(result.maskedText.contains("山田太郎"))
        assertTrue(result.appliedSpans.isEmpty())
        assertEquals(1, result.skippedSpans.size)
    }

    // ── TC-05-04: maskRecommended=false かつ userOverride=null（デフォルト除外） ─

    @Test
    fun mask_excludesSpan_whenMaskNotRecommendedAndNoOverride() {
        val text = "受給者氏名: 山田太郎"
        val s = span(spanText = "山田太郎", maskRecommended = false, userOverride = null)
        val result = PiiMasker.mask(text, listOf(s))
        assertFalse(result.maskedText.contains(mask))
        assertTrue(result.maskedText.contains("山田太郎"))
        assertTrue(result.appliedSpans.isEmpty())
        assertEquals(1, result.skippedSpans.size)
    }

    // ── TC-05-05: 同一テキストが複数箇所に出現する場合はすべてマスク ─────────────

    @Test
    fun mask_replacesAllOccurrences_whenSpanAppearsMultipleTimes() {
        val text = "山田太郎と山田太郎は同一人物"
        val result = PiiMasker.mask(text, listOf(span(spanText = "山田太郎")))
        assertEquals("${mask}と${mask}は同一人物", result.maskedText)
        assertEquals(1, result.appliedSpans.size)
    }

    // ── TC-05-06: スパンが原文に存在しない場合は unmatchedSpans に記録 ─────────

    @Test
    fun mask_recordsInUnmatched_whenSpanTextNotFound() {
        val text = "受給者氏名: 山田太郎"
        val s = span(spanText = "存在しない文字列")
        val result = PiiMasker.mask(text, listOf(s))
        assertFalse(result.maskedText.contains(mask))
        assertTrue(result.appliedSpans.isEmpty())
        assertEquals(1, result.unmatchedSpans.size)
    }

    // ── TC-05-07: 正規化後マッチ（空白・全角スペースの差異） ────────────────────

    @Test
    fun mask_matchesAfterNormalization_whenWhitespaceDiffers() {
        // Text has full-width space; span uses regular space
        val text = "受給者氏名:　山田　太郎"
        val s = span(spanText = "山田 太郎")
        val result = PiiMasker.mask(text, listOf(s))
        assertTrue(result.maskedText.contains(mask))
        assertEquals(1, result.appliedSpans.size)
    }

    @Test
    fun mask_matchesAfterNormalization_whenNewlineDiffers() {
        val text = "住所: 港区\n虚空町1-2-3"
        val s = span(spanText = "港区 虚空町1-2-3")
        val result = PiiMasker.mask(text, listOf(s))
        assertTrue(result.maskedText.contains(mask))
        assertEquals(1, result.appliedSpans.size)
    }

    // ── TC-05-08: 複数スパンのマスク ─────────────────────────────────────────

    @Test
    fun mask_appliesAllSpans_whenMultipleSpansProvided() {
        val text = "氏名: 山田太郎 住所: 港区虚空町1-2-3 生年月日: 昭和60年1月1日"
        val spans = listOf(
            span(id = "pii_01", spanText = "山田太郎", category = "name"),
            span(id = "pii_02", spanText = "港区虚空町1-2-3", category = "address"),
            span(id = "pii_03", spanText = "昭和60年1月1日", category = "dob"),
        )
        val result = PiiMasker.mask(text, spans)
        assertEquals(3, result.appliedSpans.size)
        assertFalse(result.maskedText.contains("山田太郎"))
        assertFalse(result.maskedText.contains("港区虚空町1-2-3"))
        assertFalse(result.maskedText.contains("昭和60年1月1日"))
    }

    // ── TC-05-09: ユーザー操作後の再マスク（mask() を再呼び出し） ─────────────

    @Test
    fun mask_reflectsUpdatedOverride() {
        val originalText = "氏名: 山田太郎 口座: 1234567"
        val s1 = span(id = "pii_01", spanText = "山田太郎", maskRecommended = true)
        val s2 = span(id = "pii_02", spanText = "1234567", maskRecommended = true, category = "account")

        // Initially mask both
        val first = PiiMasker.mask(originalText, listOf(s1, s2))
        assertEquals(2, first.appliedSpans.size)

        // User toggles off 口座
        val s2Excluded = s2.copy(userOverride = false)
        val second = PiiMasker.mask(originalText, listOf(s1, s2Excluded))
        assertEquals(1, second.appliedSpans.size)
        assertEquals("pii_01", second.appliedSpans[0].id)
        assertTrue(second.maskedText.contains("1234567"))
        assertFalse(second.maskedText.contains("山田太郎"))
    }

    // ── TC-05-10: マスク文字が [■■■] 固定 ──────────────────────────────────

    @Test
    fun mask_usesBracketMaskToken() {
        val text = "山田太郎"
        val result = PiiMasker.mask(text, listOf(span(spanText = "山田太郎")))
        assertEquals("[■■■]", result.maskedText)
    }

    // ── buildMatchRegex unit tests ────────────────────────────────────────────

    @Test
    fun buildMatchRegex_returnsNull_forEmptyString() {
        assertNull(PiiMasker.buildMatchRegex(""))
    }

    @Test
    fun buildMatchRegex_returnsNonNull_forNormalString() {
        assertNotNull(PiiMasker.buildMatchRegex("山田太郎"))
    }

    @Test
    fun buildMatchRegex_matchesExactString() {
        val regex = PiiMasker.buildMatchRegex("山田太郎")!!
        assertTrue(regex.containsMatchIn("氏名: 山田太郎"))
    }

    @Test
    fun buildMatchRegex_matchesWithWhitespaceDifference() {
        val regex = PiiMasker.buildMatchRegex("山田 太郎")!!
        assertTrue(regex.containsMatchIn("山田　太郎"))  // full-width space
        assertTrue(regex.containsMatchIn("山田\n太郎")) // newline
    }

    @Test
    fun buildMatchRegex_matchesWhenSpanHasNoSpaceButTextDoes() {
        // Reverse direction: LLM returns "山田太郎" (no space), PDF has "山田 太郎"
        val regex = PiiMasker.buildMatchRegex("山田太郎")!!
        assertTrue(regex.containsMatchIn("山田 太郎"))
        assertTrue(regex.containsMatchIn("山田　太郎"))
        assertTrue(regex.containsMatchIn("山田\n太郎"))
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun mask_emptyText_returnsEmptyMaskedText() {
        val result = PiiMasker.mask("", listOf(span(spanText = "山田太郎")))
        assertTrue(result.maskedText.isEmpty())
        assertTrue(result.unmatchedSpans.isNotEmpty())
    }

    @Test
    fun mask_emptySpanList_returnsOriginalText() {
        val text = "受給者氏名: 山田太郎"
        val result = PiiMasker.mask(text, emptyList())
        assertEquals(text, result.maskedText)
        assertTrue(result.appliedSpans.isEmpty())
        assertTrue(result.skippedSpans.isEmpty())
        assertTrue(result.unmatchedSpans.isEmpty())
    }

    @Test
    fun mask_priorityOrder_userOverrideTrueBeforesMaskRecommendedFalse() {
        val text = "1234567"
        val s = span(spanText = "1234567", maskRecommended = false, userOverride = true)
        val result = PiiMasker.mask(text, listOf(s))
        assertTrue(result.maskedText.contains(mask))
        assertEquals(1, result.appliedSpans.size)
    }

    // ── TC-05-11: ピリオド末尾の住所でスパン直後のスペースが保持される ────────────
    // Regression: "." was treated as a regex wildcard, consuming the trailing space.

    @Test
    fun mask_preservesSpaceAfterMasked_whenSpanEndsWithPeriod() {
        val text = "123 Main St. Springfield, IL"
        val s = span(spanText = "123 Main St.", category = "address", sourceField = "issuer_address")
        val result = PiiMasker.mask(text, listOf(s))
        assertEquals("[Issuer address] Springfield, IL", result.maskedText)
    }

    // ── TC-05-12: ピリオドを含む住所が正しくマスクされる ─────────────────────────

    @Test
    fun mask_correctlyMasks_whenSpanContainsPeriod() {
        val text = "Address: 123 Main St. in the city"
        val s = span(spanText = "123 Main St.", category = "address", sourceField = "issuer_address")
        val result = PiiMasker.mask(text, listOf(s))
        assertTrue(result.maskedText.contains("[Issuer address]"))
        assertFalse(result.maskedText.contains("123 Main St."))
        assertEquals(1, result.appliedSpans.size)
    }

    // ── TC-05-13: 括弧を含む spanText が正しくマスクされる ────────────────────────

    @Test
    fun mask_correctlyMasks_whenSpanContainsParentheses() {
        val text = "Location: 123 Main St. (4F) next to the bank"
        val s = span(spanText = "123 Main St. (4F)", category = "address", sourceField = "issuer_address")
        val result = PiiMasker.mask(text, listOf(s))
        assertTrue(result.maskedText.contains("[Issuer address]"))
        assertFalse(result.maskedText.contains("123 Main St. (4F)"))
        assertEquals(1, result.appliedSpans.size)
    }

    // ── TC-05-14: 複数スパンが並ぶ英語テキストでスペースが保持される ───────────────

    @Test
    fun mask_preservesSpaces_withMultipleEnglishSpans() {
        val text = "John Smith at 123 Main St. Chicago"
        val spans = listOf(
            span(id = "pii_01", spanText = "John Smith", category = "name", sourceField = "applicant_name"),
            span(id = "pii_02", spanText = "123 Main St.", category = "address", sourceField = "applicant_address"),
        )
        val result = PiiMasker.mask(text, spans)
        assertEquals("[Applicant name] at [Applicant address] Chicago", result.maskedText)
    }

    // ── TC-05-15: 空白のみの spanText が unmatchedSpans に記録される ───────────────

    @Test
    fun buildMatchRegex_returnsNull_forWhitespaceOnlyString() {
        assertNull(PiiMasker.buildMatchRegex("  "))
        assertNull(PiiMasker.buildMatchRegex("　"))
        assertNull(PiiMasker.buildMatchRegex(" \t\n"))
    }

    @Test
    fun mask_recordsInUnmatched_whenSpanTextIsWhitespaceOnly() {
        val text = "受給者氏名: 山田太郎"
        val s = span(spanText = "  ")
        val result = PiiMasker.mask(text, listOf(s))
        assertFalse(result.maskedText.contains(mask))
        assertTrue(result.appliedSpans.isEmpty())
        assertEquals(1, result.unmatchedSpans.size)
    }
}
