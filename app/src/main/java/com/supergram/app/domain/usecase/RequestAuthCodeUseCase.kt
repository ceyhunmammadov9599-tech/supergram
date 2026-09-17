package com.supergram.app.domain.usecase

import com.supergram.app.domain.repository.TelegramAuthRepository

/** Requests Telegram to send a login code to [phoneNumber]. */
class RequestAuthCodeUseCase(private val repository: TelegramAuthRepository) {
    suspend operator fun invoke(phoneNumber: String): Result<Unit> =
        repository.requestAuthCode(phoneNumber)
}
