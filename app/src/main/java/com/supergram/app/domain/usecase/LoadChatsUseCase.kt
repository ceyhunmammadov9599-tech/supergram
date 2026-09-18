package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Loads the chat list from TDLib. */
class LoadChatsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(limit: Int = 100): Result<Unit> =
        repository.loadChats(limit)
}
