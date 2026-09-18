package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.StateFlow
import com.supergram.app.domain.model.Chat

/** Observes the live, ordered chat list. */
class ObserveChatListUseCase(private val repository: ChatRepository) {
    operator fun invoke(): StateFlow<List<Chat>> = repository.chats
}
