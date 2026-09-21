package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.Message
import com.supergram.app.domain.repository.ChatRepository

/** Loads a page of chat history (newest first). */
class LoadChatHistoryUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<Message>> = repository.loadHistory(chatId, fromMessageId, limit, offset)
}
