package com.supergram.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure reply-state encapsulation (ReplyDraft).
 */
class ReplyDraftTest {

    private fun textMessage(id: Long, text: String, outgoing: Boolean) = Message(
        id = id,
        chatId = 1,
        senderUserId = null,
        text = text,
        isOutgoing = outgoing,
        date = id,
        media = null,
    )

    @Test
    fun `draft captures message id, snippet and outgoing flag`() {
        val draft = ReplyDraft.of(textMessage(7, "hello there", outgoing = false))

        assertEquals(7L, draft.messageId)
        assertEquals("hello there", draft.snippet)
        assertFalse(draft.isOutgoing)
        assertEquals("Reply", draft.title)
    }

    @Test
    fun `title reflects the own-message case`() {
        val draft = ReplyDraft.of(textMessage(9, "general kenobi", outgoing = true))

        assertTrue(draft.isOutgoing)
        assertEquals("You", draft.title)
    }

    @Test
    fun `long snippets are trimmed to 64 chars`() {
        val long = "x".repeat(100)
        val draft = ReplyDraft.of(textMessage(1, long, outgoing = false))

        assertEquals(64, draft.snippet.length)
    }

    @Test
    fun `blank text falls back to the media kind`() {
        val voice = Message(
            id = 3,
            chatId = 1,
            senderUserId = null,
            text = "",
            isOutgoing = true,
            date = 3,
            media = MediaFile(
                kind = MediaKind.VOICE,
                fileId = 11,
                fileName = null,
                expectedSize = 0L,
                downloadedSize = 0L,
                isDownloadingActive = false,
                isDownloaded = false,
                localPath = null,
                durationSeconds = 4,
                waveform = null,
            ),
        )
        val draft = ReplyDraft.of(voice)

        assertEquals("Voice message", draft.snippet)
    }

    @Test
    fun `empty content keeps an empty snippet for the placeholder`() {
        val draft = ReplyDraft.of(textMessage(5, "   ", outgoing = false))
        assertEquals("", draft.snippet)
    }
}
