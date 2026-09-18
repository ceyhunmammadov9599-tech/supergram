package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Opens a chat (TDLib stream optimization; required before ViewMessages). */
class OpenChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: Long): Result<Unit> = repository.openChat(chatId)
}
