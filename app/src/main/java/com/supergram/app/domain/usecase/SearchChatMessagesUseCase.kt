package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.SearchPage
import com.supergram.app.domain.repository.ChatRepository

/**
 * Searches messages inside a single chat (TdApi.SearchChatMessages).
 * Paged: pass the previous page's nextFromMessageId as [fromMessageId].
 */
class SearchChatMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: Long,
        query: String,
        limit: Int = 20,
        fromMessageId: Long = 0,
    ): Result<SearchPage> = repository.searchChatMessages(chatId, query, limit, fromMessageId)
}
