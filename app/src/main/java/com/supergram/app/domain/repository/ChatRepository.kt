package com.supergram.app.domain.repository

import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.Message
import com.supergram.app.domain.model.SearchResult
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

    /** Hot stream of file download state changes (photos, documents). */
    val fileUpdates: SharedFlow<MediaFile>

    /** Loads the chat list from TDLib and publishes it into [chats]. */
    suspend fun loadChats(limit: Int): Result<Unit>

    /** Loads a page of chat history. Returns messages newest-first. */
    suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int): Result<List<Message>>

    /** Sends a plain-text message. */
    suspend fun sendMessage(chatId: Long, text: String): Result<Unit>

    /** Opens a chat (TDLib stream optimization; required before ViewMessages). */
    suspend fun openChat(chatId: Long): Result<Unit>

    /** Closes a chat when leaving its screen. */
    suspend fun closeChat(chatId: Long): Result<Unit>

    /** Marks messages as viewed — clears unread counters locally and remotely. */
    suspend fun viewMessages(chatId: Long, messageIds: List<Long>): Result<Unit>

    /** Starts (or resumes) downloading a file; progress arrives via [fileUpdates]. */
    suspend fun downloadFile(fileId: Int): Result<Unit>

    /** Searches messages inside a chat (TdApi.SearchChatMessages). */
    suspend fun searchChatMessages(chatId: Long, query: String, limit: Int = 20): Result<List<SearchResult>>

    /** Global cross-chat message search (TdApi.SearchMessages). */
    suspend fun searchMessages(query: String, limit: Int = 20): Result<List<SearchResult>>
}
