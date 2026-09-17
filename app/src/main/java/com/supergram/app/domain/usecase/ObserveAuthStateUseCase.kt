package com.supergram.app.domain.usecase

import com.supergram.app.domain.model.AuthState
import com.supergram.app.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.flow.StateFlow

/** Streams the Telegram authorization state to the presentation layer. */
class ObserveAuthStateUseCase(private val repository: TelegramAuthRepository) {
    operator fun invoke(): StateFlow<AuthState> = repository.authState
}
