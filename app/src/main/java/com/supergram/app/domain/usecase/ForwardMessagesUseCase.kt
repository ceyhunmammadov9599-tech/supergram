package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Forwards messages from one chat to another (TdApi.ForwardMessages). */
class ForwardMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        fromChatId: Long,
        toChatId: Long,
        messageIds: List<Long>,
    ): Result<Unit> = repository.forwardMessages(fromChatId, toChatId, messageIds)
}
