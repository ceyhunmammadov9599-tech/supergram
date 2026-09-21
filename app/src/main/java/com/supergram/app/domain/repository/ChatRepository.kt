package com.supergram.app.domain.repository

import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.Message
import com.supergram.app.domain.model.SearchFilter
import com.supergram.app.domain.model.SearchPage
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

    /**
     * Loads a page of chat history. Returns messages newest-first.
     *
     * @param fromMessageId Fetch messages older than this id (0 = newest).
     * @param offset TDLib pagination offset: 0 = from exactly [fromMessageId]
     * (older), a negative value (>= -99) additionally returns newer messages.
     */
    suspend fun loadHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
        offset: Int = 0,
    ): Result<List<Message>>

    /**
     * Sends a plain-text message, optionally replying to a message
     * (TdApi.InputMessageReplyToMessage).
     */
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyToMessageId: Long? = null,
    ): Result<Unit>

    /** Forwards messages from one chat to another (TdApi.ForwardMessages). */
    suspend fun forwardMessages(
        fromChatId: Long,
        toChatId: Long,
        messageIds: List<Long>,
    ): Result<Unit>

    /** Deletes messages (TdApi.DeleteMessages); revoke = for both sides. */
    suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<Long>,
        revoke: Boolean,
    ): Result<Unit>

    /** Opens a chat (TDLib stream optimization; required before ViewMessages). */
    suspend fun openChat(chatId: Long): Result<Unit>

    /** Closes a chat when leaving its screen. */
    suspend fun closeChat(chatId: Long): Result<Unit>

    /** Marks messages as viewed — clears unread counters locally and remotely. */
    suspend fun viewMessages(chatId: Long, messageIds: List<Long>): Result<Unit>

    /** Starts (or resumes) downloading a file; progress arrives via [fileUpdates]. */
    suspend fun downloadFile(fileId: Int): Result<Unit>

    /**
     * Searches messages inside a chat (TdApi.SearchChatMessages).
     * Pages via the [SearchPage.nextFromMessageId] cursor (FoundChatMessages).
     */
    suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int = 20,
        fromMessageId: Long = 0,
        filter: SearchFilter = SearchFilter.ALL,
    ): Result<SearchPage>

    /**
     * Global cross-chat message search (TdApi.SearchMessages).
     * Pages via the [SearchPage.nextOffset] string cursor (FoundMessages).
     */
    suspend fun searchMessages(
        query: String,
        limit: Int = 20,
        offset: String = "",
        filter: SearchFilter = SearchFilter.ALL,
    ): Result<SearchPage>
}
