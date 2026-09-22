package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository


class GetUnreadCursorUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: Long): Result<Long?> = repository.getUnreadCursor(chatId)
}
