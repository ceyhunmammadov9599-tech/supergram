package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository
import com.supergram.app.domain.model.Message

class GetPinnedMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: Long): Result<Message?> = repository.getPinnedMessage(chatId)
}
