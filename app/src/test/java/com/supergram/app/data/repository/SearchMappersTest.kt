package com.supergram.app.data.repository

import com.supergram.app.domain.model.matchRanges
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the search result mapping (TdApi.Message -> SearchResult)
 * and the pure query-highlight range helper.
 */
class SearchMappersTest {

    private fun message(
        id: Long,
        chatId: Long,
        date: Int,
        text: String,
    ): TdApi.Message = TdApi.Message().apply {
        this.id = id
        this.chatId = chatId
        this.date = date
        this.isOutgoing = false
        this.content = TdApi.MessageText().apply {
            this.text = TdApi.FormattedText().apply { this.text = text }
        }
    }

    @Test
    fun `text message maps into SearchResult with all fields`() {
        val result = message(id = 42, chatId = 7, date = 1000, text = "Hello world")
            .toSearchResult(chatTitle = "Team Chat", senderName = "Ali Aliyev")

        assertEquals(42, result.messageId)
        assertEquals(7, result.chatId)
        assertEquals("Team Chat", result.chatTitle)
        assertEquals("Ali Aliyev", result.senderName)
        assertEquals("Hello world", result.snippet)
        assertEquals(1000, result.date)
    }

    @Test
    fun `null sender name is preserved for non-user senders`() {
        val result = message(id = 42, chatId = 7, date = 1000, text = "Hi")
            .toSearchResult(chatTitle = "Chat", senderName = null)

        assertNull(result.senderName)
    }

    @Test
    fun `non-text content falls back to the snippet representation`() {
        val media = TdApi.Message().apply {
            id = 5
            chatId = 1
            date = 10
            content = TdApi.MessagePhoto()
        }
        val result = media.toSearchResult(chatTitle = "Chat", senderName = null)

        assertTrue(result.snippet.isNotEmpty())
    }

    // ------------------------------------------------------- matchRanges

    @Test
    fun `matchRanges finds all case-insensitive occurrences`() {
        val ranges = matchRanges("Hello hello HELLO", "hello")

        assertEquals(3, ranges.size)
        assertEquals(listOf(0..4, 6..10, 12..16), ranges)
    }

    @Test
    fun `matchRanges is empty for blank queries`() {
        assertTrue(matchRanges("some text", "").isEmpty())
        assertTrue(matchRanges("some text", "   ").isEmpty())
    }

    @Test
    fun `matchRanges returns empty when no match`() {
        assertTrue(matchRanges("abc", "xyz").isEmpty())
    }

    @Test
    fun `matchRanges handles adjacent and repeated matches`() {
        val ranges = matchRanges("abab", "ab")

        assertEquals(listOf(0..1, 2..3), ranges)
    }
}
