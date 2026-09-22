package com.supergram.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class UnreadBoundaryTest {
    private fun msg(id: Long, outgoing: Boolean = false) =
        Message(id, 1, null, "m$id", outgoing, id, null)

    @Test fun firstIncomingAboveCursor() {
        assertEquals(12L, unreadBoundary(listOf(msg(8), msg(11, true), msg(12), msg(15)), 8))
    }

    @Test fun noDividerWithoutUnreadSnapshotOrMessages() {
        assertEquals(8L, unreadBoundary(listOf(msg(8), msg(9)), 0))
        assertNull(unreadBoundary(listOf(msg(8), msg(9)), -1))
        assertNull(unreadBoundary(emptyList(), 8))
        assertNull(unreadBoundary(listOf(msg(8), msg(9)), 9))
    }

    @Test fun outgoingMessagesDoNotStartUnreadRegion() {
        assertEquals(14L, unreadBoundary(listOf(msg(10), msg(11, true), msg(14)), 10))
    }

    @Test fun firstUnreadInPagedStreamMovesToTrueBoundaryWhenOlderPageArrives() {
        val initial = listOf(msg(12), msg(15))
        assertEquals(12L, unreadBoundary(initial, 8))
        assertEquals(10L, unreadBoundary(listOf(msg(8), msg(10), msg(12), msg(15)), 8))
    }
}
