package com.supergram.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure history-window helpers backing bidirectional
 * chat paging: merge ordering + de-duplication and the newest/oldest
 * cursor extraction used as TDLib fromMessageId values.
 */
class HistoryPagingTest {

    private fun message(id: Long, date: Long = id) = Message(
        id = id,
        chatId = 1,
        senderUserId = null,
        text = "m$id",
        isOutgoing = false,
        date = date,
        media = null,
    )

    @Test
    fun `merge dedupes by id and orders newest first`() {
        val existing = listOf(message(3), message(1))
        val page = listOf(message(2), message(1), message(5))

        val merged = mergeMessages(existing, page)

        assertEquals(listOf(5L, 3L, 2L, 1L), merged.map { it.id })
    }

    @Test
    fun `merge is stable for equal dates by falling back to id order`() {
        val merged = mergeMessages(
            listOf(message(2, date = 100)),
            listOf(message(7, date = 100), message(9, date = 100)),
        )

        assertEquals(listOf(9L, 7L, 2L), merged.map { it.id })
    }

    @Test
    fun `newest and oldest ids ignore optimistic local echoes`() {
        val list = listOf(
            message(-2),   // optimistic echo
            message(10),
            message(4),
            message(-1),   // optimistic echo
            message(7),
        )

        assertEquals(10L, list.newestMessageId())
        assertEquals(4L, list.oldestMessageId())
    }

    @Test
    fun `newest and oldest are null for empty or echo-only lists`() {
        assertNull(emptyList<Message>().newestMessageId())
        assertNull(listOf(message(-5)).newestMessageId())
        assertNull(listOf(message(-5)).oldestMessageId())
    }
}
