package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.Message
import com.supergram.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.SharedFlow

/** Observes messages as they arrive in real time. */
class ObserveNewMessagesUseCase(private val repository: ChatRepository) {
    operator fun invoke(): SharedFlow<Message> = repository.newMessages
}
