package com.supergram.app.domain.repository

import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.Message
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for Telegram chat and messaging operations.
 */
interface ChatRepository {
    /** Hot, ordered list of the user's chats (kept fresh by TDLib updates). */
    val chats: StateFlow<List<Chat>>

    /** Hot stream of messages as they arrive in real time. */
    val newMessages: SharedFlow<Message>

    /** Loads the chat list from TDLib and publishes it into [chats]. */
    suspend fun loadChats(limit: Int): Result<Unit>

    /** Loads a page of chat history. Returns messages newest-first. */
    suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int): Result<List<Message>>

    /** Sends a plain-text message. */
    suspend fun sendMessage(chatId: Long, text: String): Result<Unit>
}
