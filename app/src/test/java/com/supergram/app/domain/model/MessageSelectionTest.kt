package com.supergram.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class MessageSelectionTest {
    private fun msg(id: Long, outgoing: Boolean = false) =
        Message(id, 1, null, "m$id", outgoing, id, null)

    @Test fun startAndToggleMultiple() {
        val first = MessageSelection().start(1)
        assertTrue(first.active)
        assertEquals(1, first.count)
        val two = first.toggle(2)
        assertEquals(setOf(1L, 2L), two.selectedIds)
        assertEquals(setOf(2L), two.toggle(1).selectedIds)
        assertFalse(two.toggle(1).toggle(2).active)
    }

    @Test fun selectAllAndClear() {
        val selected = MessageSelection().start(9).selectAll(listOf(msg(1), msg(2), msg(3)))
        assertEquals(3, selected.count)
        assertEquals(setOf(1L, 2L, 3L), selected.selectedIds)
        assertEquals(MessageSelection(), selected.clear())
        assertFalse(selected.selectAll(emptyList()).active)
    }

    @Test fun batchPayloadPreservesStreamOrderAndIgnoresStaleIds() {
        val selection = MessageSelection(setOf(5, 1, 99), active = true)
        val payload = selection.payload(listOf(msg(5), msg(2), msg(1)))
        assertEquals(listOf(5L, 1L), payload)
    }

    @Test fun emptySelectionProducesNoBatchPayload() {
        assertTrue(MessageSelection().payload(listOf(msg(1))).isEmpty())
    }
}
