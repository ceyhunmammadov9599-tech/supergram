package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Sends a plain-text message, optionally replying to a message. */
class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: Long,
        text: String,
        replyToMessageId: Long? = null,
    ): Result<Unit> = repository.sendMessage(chatId, text, replyToMessageId)
}
