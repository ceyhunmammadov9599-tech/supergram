package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository

/** Marks messages as viewed — clears unread counters locally and remotely. */
class ViewMessagesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: Long, messageIds: List<Long>): Result<Unit> =
        repository.viewMessages(chatId, messageIds)
}
