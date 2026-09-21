package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.SearchFilter
import com.supergram.app.domain.model.SearchPage
import com.supergram.app.domain.repository.ChatRepository

/**
 * Global cross-chat message search (TdApi.SearchMessages).
 * Paged: pass the previous page's nextOffset as [offset].
 */
class SearchMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        query: String,
        limit: Int = 20,
        offset: String = "",
        filter: SearchFilter = SearchFilter.ALL,
    ): Result<SearchPage> = repository.searchMessages(query, limit, offset, filter)
}
