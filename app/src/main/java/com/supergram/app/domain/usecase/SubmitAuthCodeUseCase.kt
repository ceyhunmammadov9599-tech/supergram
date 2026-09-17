package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.TelegramAuthRepository

/** Verifies the Telegram login code the user received. */
class SubmitAuthCodeUseCase(private val repository: TelegramAuthRepository) {
    suspend operator fun invoke(code: String): Result<Unit> =
        repository.submitAuthCode(code)
}
