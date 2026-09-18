package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Sends a plain-text message to a chat. */
class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: Long, text: String): Result<Unit> =
        repository.sendMessage(chatId, text)
}
