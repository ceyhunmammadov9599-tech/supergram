package com.supergram.app.data.repository

import com.supergram.app.domain.model.ChatCategory
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Domain/data tests for chat categorization: bot detection flags and
 * chat-type -> category resolution (folder splitting backbone).
 */
class ChatCategoryResolverTest {

    @Test
    fun `private chat with regular user resolves to DIRECT`() {
        val category = ChatCategoryResolver.resolve(TdApi.ChatTypePrivate(1L), isBot = false)
        assertEquals(ChatCategory.DIRECT, category)
    }

    @Test
    fun `private chat with bot account resolves to BOT`() {
        val category = ChatCategoryResolver.resolve(TdApi.ChatTypePrivate(2L), isBot = true)
        assertEquals(ChatCategory.BOT, category)
    }

    @Test
    fun `bot flag never overrides group or channel types`() {
        assertEquals(
            ChatCategory.GROUP,
            ChatCategoryResolver.resolve(TdApi.ChatTypeBasicGroup(1L), isBot = true),
        )
        assertEquals(
            ChatCategory.CHANNEL,
            ChatCategoryResolver.resolve(TdApi.ChatTypeSupergroup(1, true), isBot = true),
        )
    }

    @Test
    fun `secret chat resolves to DIRECT`() {
        val category = ChatCategoryResolver.resolve(TdApi.ChatTypeSecret(0, 1L))
        assertEquals(ChatCategory.DIRECT, category)
    }

    @Test
    fun `basic group resolves to GROUP`() {
        val category = ChatCategoryResolver.resolve(TdApi.ChatTypeBasicGroup(42L))
        assertEquals(ChatCategory.GROUP, category)
    }

    @Test
    fun `supergroup channel resolves to CHANNEL`() {
        val category = ChatCategoryResolver.resolve(TdApi.ChatTypeSupergroup(42, true))
        assertEquals(ChatCategory.CHANNEL, category)
    }

    @Test
    fun `supergroup without channel flag resolves to GROUP`() {
        val category = ChatCategoryResolver.resolve(TdApi.ChatTypeSupergroup(42, false))
        assertEquals(ChatCategory.GROUP, category)
    }

    @Test
    fun `unknown type falls back to DIRECT`() {
        val category = ChatCategoryResolver.resolve(null)
        assertEquals(ChatCategory.DIRECT, category)
    }
}
