package io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TextExtractorTest {

    // ── readFromStream ────────────────────────────────────────────────────────

    @Test
    fun readFromStream_returnsFullText() {
        val input = "テスト文字列"
        val result = TextExtractor.readFromStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
        assertEquals(input, result)
    }

    @Test
    fun readFromStream_handlesUtf8MultibyteChars() {
        val input = "あ".repeat(100)
        val result = TextExtractor.readFromStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
        assertEquals(input, result)
    }

    @Test
    fun readFromStream_handlesEmptyStream() {
        val result = TextExtractor.readFromStream(ByteArrayInputStream(ByteArray(0)))
        assertTrue(result.isEmpty())
    }

    // ── joinPageTexts ─────────────────────────────────────────────────────────

    @Test
    fun joinPageTexts_joinsWithDoubleNewline() {
        val result = TextExtractor.joinPageTexts(listOf("ページ1", "ページ2", "ページ3"))
        assertEquals("ページ1\n\nページ2\n\nページ3", result)
    }

    @Test
    fun joinPageTexts_returnsSinglePage_asIs() {
        val result = TextExtractor.joinPageTexts(listOf("単一ページ"))
        assertEquals("単一ページ", result)
    }

    // ── isPdfBytes ────────────────────────────────────────────────────────────

    @Test
    fun isPdfBytes_returnsTrueForPdfHeader() {
        val header = "%PDF-1.4 rest of content".toByteArray(Charsets.ISO_8859_1)
        assertTrue(TextExtractor.isPdfBytes(header))
    }

    @Test
    fun isPdfBytes_returnsFalseForNonPdfBytes() {
        val header = "Hello World".toByteArray(Charsets.ISO_8859_1)
        assertFalse(TextExtractor.isPdfBytes(header))
    }

    @Test
    fun isPdfBytes_returnsFalseForShortHeader() {
        val header = "%PD".toByteArray(Charsets.ISO_8859_1)
        assertFalse(TextExtractor.isPdfBytes(header))
    }

    @Test
    fun isPdfBytes_returnsFalseForEmptyBytes() {
        assertFalse(TextExtractor.isPdfBytes(ByteArray(0)))
    }
}
