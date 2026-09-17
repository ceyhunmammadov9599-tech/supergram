package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.TelegramAuthRepository

/** Verifies the Telegram cloud (2FA) password. */
class SubmitPasswordUseCase(private val repository: TelegramAuthRepository) {
    suspend operator fun invoke(password: String): Result<Unit> =
        repository.submitPassword(password)
}
