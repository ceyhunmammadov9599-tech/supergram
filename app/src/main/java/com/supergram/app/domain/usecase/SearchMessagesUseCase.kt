package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.SearchResult
import com.supergram.app.domain.repository.ChatRepository

/** Global cross-chat message search (TdApi.SearchMessages). */
class SearchMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        query: String,
        limit: Int = 20,
    ): Result<List<SearchResult>> = repository.searchMessages(query, limit)
}
