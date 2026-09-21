package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Deletes messages (TdApi.DeleteMessages); revoke = for both sides. */
class DeleteMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: Long,
        messageIds: List<Long>,
        revoke: Boolean,
    ): Result<Unit> = repository.deleteMessages(chatId, messageIds, revoke)
}
