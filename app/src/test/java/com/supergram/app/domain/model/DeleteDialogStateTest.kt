package com.supergram.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure delete-dialog state flow:
 * Hidden -> Confirming -> (confirm | dismiss) with the revoke checkbox.
 */
class DeleteDialogStateTest {

    @Test
    fun `starts hidden`() {
        val state = DeleteDialogState.HIDDEN

        assertFalse(state.visible)
        assertNull(state.messageId)
        assertFalse(state.canRevoke)
        assertFalse(state.revoke)
        assertNull(state.confirmed())
    }

    @Test
    fun `showing for a message opens the dialog with revoke cleared`() {
        val state = DeleteDialogState.HIDDEN
            .shownFor(42, canRevoke = true)

        assertTrue(state.visible)
        assertEquals(42L, state.messageId)
        assertTrue(state.canRevoke)
        assertFalse(state.revoke) // checkbox starts unchecked
    }

    @Test
    fun `re-toggle flips the checkbox only while visible and allowed`() {
        var state = DeleteDialogState.HIDDEN.shownFor(42, canRevoke = true)
        state = state.revokeToggled()
        assertTrue(state.revoke)

        state = state.revokeToggled()
        assertFalse(state.revoke)

        // hidden state: toggle is a no-op
        val hidden = state.dismissed().revokeToggled()
        assertFalse(hidden.visible)
        assertFalse(hidden.revoke)
    }

    @Test
    fun `revoke toggle is a no-op when the chat does not allow revoke`() {
        val state = DeleteDialogState.HIDDEN
            .shownFor(42, canRevoke = false)
            .revokeToggled()

        assertFalse(state.revoke)
    }

    @Test
    fun `confirm produces a DeleteRequest carrying the revoke decision`() {
        val request = DeleteDialogState.HIDDEN
            .shownFor(42, canRevoke = true)
            .revokeToggled()
            .confirmed()

        assertEquals(DeleteRequest(messageId = 42, revoke = true), request)
    }

    @Test
    fun `confirm clamps revoke to false when not allowed`() {
        val request = DeleteDialogState.HIDDEN
            .shownFor(7, canRevoke = false)
            .revokeToggled() // ignored: canRevoke = false
            .confirmed()

        assertEquals(DeleteRequest(messageId = 7, revoke = false), request)
    }

    @Test
    fun `dismiss hides the dialog and a later confirm yields nothing`() {
        val state = DeleteDialogState.HIDDEN.shownFor(42, canRevoke = true).dismissed()

        assertFalse(state.visible)
        assertNull(state.confirmed())
    }

    @Test
    fun `reshowing the same message keeps the dialog state, reshowing another resets`() {
        var state = DeleteDialogState.HIDDEN.shownFor(42, canRevoke = true).revokeToggled()
        val resame = state.dismissed().shownFor(42, canRevoke = true)

        assertTrue(resame.visible)
        assertTrue(resame.revoke) // same message: checkbox preserved

        val reother = resame.dismissed().shownFor(43, canRevoke = true)
        assertTrue(reother.visible)
        assertFalse(reother.revoke) // different message: reset
    }
}
