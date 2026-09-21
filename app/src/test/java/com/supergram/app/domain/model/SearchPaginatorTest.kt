package com.supergram.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the search pagination state accumulator: cursor transitions
 * (global nextOffset / in-chat nextFromMessageId), de-duplication, and the
 * hasMore / loadingMore guards.
 */
class SearchPaginatorTest {

    private fun result(id: Long, chatId: Long = 1) = SearchResult(
        messageId = id,
        chatId = chatId,
        chatTitle = "Chat",
        senderName = null,
        snippet = "snippet $id",
        date = id,
    )

    // ------------------------------------------------------ start / reset

    @Test
    fun `start resets results and places both cursors at the beginning`() {
        val state = SearchPaginator.start("query")

        assertTrue(state.results.isEmpty())
        assertEquals("", state.nextOffset)
        assertEquals(0L, state.nextFromMessageId)
        assertTrue(state.hasMore)
        assertFalse(state.loadingMore)
        assertEquals("query", state.query)
    }

    // --------------------------------------------------- global paging

    @Test
    fun `global page appends results and adopts the offset cursor`() {
        val state = SearchPaginator.start("q")
            .beginLoadingMore()
            .appendGlobalPage(SearchPage(listOf(result(1), result(2)), nextOffset = "cursor-1"))

        assertEquals(listOf(1L, 2L), state.results.map { it.messageId })
        assertEquals("cursor-1", state.nextOffset)
        assertTrue(state.hasMore)
        assertFalse(state.loadingMore)
    }

    @Test
    fun `global pagination ends on a null cursor`() {
        val state = SearchPaginator.start("q")
            .appendGlobalPage(SearchPage(listOf(result(1)), nextOffset = null))

        assertFalse(state.hasMore)
    }

    @Test
    fun `global pagination ends on a blank cursor`() {
        val state = SearchPaginator.start("q")
            .appendGlobalPage(SearchPage(listOf(result(1)), nextOffset = " "))

        assertFalse(state.hasMore)
    }

    @Test
    fun `global pagination ends on an empty page`() {
        val state = SearchPaginator.start("q")
            .appendGlobalPage(SearchPage(emptyList(), nextOffset = "still-cursor"))

        assertFalse(state.hasMore)
    }

    // ----------------------------------------------------- chat paging

    @Test
    fun `chat page appends results and adopts the fromMessageId cursor`() {
        val state = SearchPaginator.start("q")
            .beginLoadingMore()
            .appendChatPage(SearchPage(listOf(result(10)), nextFromMessageId = 4L))

        assertEquals(listOf(10L), state.results.map { it.messageId })
        assertEquals(4L, state.nextFromMessageId)
        assertTrue(state.hasMore)
        assertFalse(state.loadingMore)
    }

    @Test
    fun `chat pagination ends on a zero cursor`() {
        val state = SearchPaginator.start("q")
            .appendChatPage(SearchPage(listOf(result(10)), nextFromMessageId = 0L))

        assertFalse(state.hasMore)
    }

    // ------------------------------------------------------ shared

    @Test
    fun `duplicate message ids are accumulated only once`() {
        val page1 = SearchPage(listOf(result(1), result(2)), nextOffset = "c")
        val page2 = SearchPage(listOf(result(2), result(3)), nextOffset = "c2")
        val state = SearchPaginator.start("q").appendGlobalPage(page1).appendGlobalPage(page2)

        assertEquals(listOf(1L, 2L, 3L), state.results.map { it.messageId })
        assertEquals("c2", state.nextOffset)
    }

    @Test
    fun `beginLoadingMore sets the in-flight guard`() {
        val state = SearchPaginator.start("q").beginLoadingMore()

        assertTrue(state.loadingMore)
        assertTrue(state.hasMore)
    }

    @Test
    fun `loadFailed keeps accumulated results and clears the guard`() {
        val state = SearchPaginator.start("q")
            .appendGlobalPage(SearchPage(listOf(result(1)), nextOffset = "c"))
            .beginLoadingMore()
            .loadFailed()

        assertEquals(listOf(1L), state.results.map { it.messageId })
        assertEquals("c", state.nextOffset)
        assertTrue(state.hasMore)
        assertFalse(state.loadingMore)
    }

    @Test
    fun `start fully replaces a previously accumulated state`() {
        val accumulated = SearchPaginator.start("old")
            .appendGlobalPage(SearchPage(listOf(result(1)), nextOffset = "c"))
        val restarted = SearchPaginator.start("new")

        assertTrue(restarted.results.isEmpty())
        assertEquals("", restarted.nextOffset)
        assertEquals(0L, restarted.nextFromMessageId)
        assertTrue(restarted.hasMore)
        assertEquals("new", restarted.query)
    }
}
