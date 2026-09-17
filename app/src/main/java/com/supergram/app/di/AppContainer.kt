package com.supergram.app.di

import com.supergram.app.data.repository.TelegramAuthRepositoryImpl
import com.supergram.app.domain.repository.TelegramAuthRepository
import com.supergram.app.domain.usecase.ObserveAuthStateUseCase
import com.supergram.app.domain.usecase.RequestAuthCodeUseCase
import com.supergram.app.domain.usecase.SubmitAuthCodeUseCase
import com.supergram.app.domain.usecase.SubmitPasswordUseCase
import com.supergram.app.core.telegram.TelegramClientManager

/**
 * Lightweight manual dependency container (no DI framework — keeps the CLI
 * project small and deterministic).
 */
object AppContainer {

    val telegramClientManager: TelegramClientManager
        get() = TelegramClientManager

    val authRepository: TelegramAuthRepository by lazy {
        TelegramAuthRepositoryImpl(telegramClientManager)
    }

    val observeAuthState: ObserveAuthStateUseCase by lazy {
        ObserveAuthStateUseCase(authRepository)
    }

    val requestAuthCode: RequestAuthCodeUseCase by lazy {
        RequestAuthCodeUseCase(authRepository)
    }

    val submitAuthCode: SubmitAuthCodeUseCase by lazy {
        SubmitAuthCodeUseCase(authRepository)
    }

    val submitPassword: SubmitPasswordUseCase by lazy {
        SubmitPasswordUseCase(authRepository)
    }
}
