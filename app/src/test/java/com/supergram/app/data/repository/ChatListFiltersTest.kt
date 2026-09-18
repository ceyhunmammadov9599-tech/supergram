package com.supergram.app.data.repository

import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.ChatCategory
import com.supergram.app.domain.model.filterByCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the chat list "folder splitting" (category filtering) logic and
 * the TDLib waveform decoder.
 */
class ChatListFiltersTest {

    private fun chat(id: Long, category: ChatCategory) = Chat(
        id = id,
        title = "chat $id",
        category = category,
        lastMessageSnippet = null,
        lastMessageDate = null,
        unreadCount = 0,
        order = id,
    )

    @Test
    fun `null category returns all chats unchanged`() {
        val chats = listOf(
            chat(1, ChatCategory.DIRECT),
            chat(2, ChatCategory.BOT),
            chat(3, ChatCategory.CHANNEL),
        )

        assertEquals(chats, chats.filterByCategory(null))
    }

    @Test
    fun `direct filter splits direct chats out of the list`() {
        val chats = listOf(
            chat(1, ChatCategory.DIRECT),
            chat(2, ChatCategory.BOT),
            chat(3, ChatCategory.GROUP),
            chat(4, ChatCategory.DIRECT),
        )

        val result = chats.filterByCategory(ChatCategory.DIRECT)

        assertEquals(listOf(1L, 4L), result.map { it.id })
    }

    @Test
    fun `bots end up only in the bots folder`() {
        val chats = listOf(
            chat(1, ChatCategory.BOT),
            chat(2, ChatCategory.DIRECT),
            chat(3, ChatCategory.BOT),
        )

        val bots = chats.filterByCategory(ChatCategory.BOT).map { it.id }
        val direct = chats.filterByCategory(ChatCategory.DIRECT).map { it.id }

        assertEquals(listOf(1L, 3L), bots)
        assertEquals(listOf(2L), direct)
    }

    @Test
    fun `empty folder yields empty list`() {
        val chats = listOf(chat(1, ChatCategory.DIRECT))

        assertTrue(chats.filterByCategory(ChatCategory.CHANNEL).isEmpty())
    }

    @Test
    fun `every chat lands in exactly one non-all folder`() {
        val chats = listOf(
            chat(1, ChatCategory.DIRECT),
            chat(2, ChatCategory.GROUP),
            chat(3, ChatCategory.CHANNEL),
            chat(4, ChatCategory.BOT),
        )
        val folderSizes = ChatCategory.entries.associateWith { c ->
            chats.filterByCategory(c).size
        }

        assertEquals(4, folderSizes.values.sum())
        assertEquals(1, folderSizes[ChatCategory.GROUP])
        assertEquals(1, folderSizes[ChatCategory.CHANNEL])
    }

    // ------------------------------------------------------ waveform decoding

    @Test
    fun `waveform decoder returns null for empty or null payloads`() {
        assertNull(decodeWaveform(null))
        assertNull(decodeWaveform(ByteArray(0)))
    }

    @Test
    fun `waveform decoder unpacks 5-bit samples and normalizes`() {
        // 0xFF byte -> all low bits set -> first samples are max (31) -> 1f bars.
        val bars = decodeWaveform(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))

        assertEquals(true, bars != null)
        assertTrue(bars!!.all { it > 0.9f })
        assertTrue(bars.size <= 64)
    }

    @Test
    fun `waveform decoder buckets long payloads to max samples`() {
        // 100 bytes -> 160 samples -> bucketed down to at most 64 bars.
        val bars = decodeWaveform(ByteArray(100) { 0xFF.toByte() }, maxSamples = 64)

        assertTrue(bars!!.isNotEmpty())
        assertTrue(bars.size <= 64)
    }
}
