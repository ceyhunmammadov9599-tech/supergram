package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.SearchResult
import com.supergram.app.domain.repository.ChatRepository

/** Searches messages inside a single chat (TdApi.SearchChatMessages). */
class SearchChatMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: Long,
        query: String,
        limit: Int = 20,
    ): Result<List<SearchResult>> = repository.searchChatMessages(chatId, query, limit)
}
