package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Closes a chat when leaving its screen (TDLib stream optimization). */
class CloseChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: Long): Result<Unit> = repository.closeChat(chatId)
}
