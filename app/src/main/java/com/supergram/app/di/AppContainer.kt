package com.supergram.app.di

import com.supergram.app.core.telegram.TelegramClientManager
import com.supergram.app.data.repository.ChatRepositoryImpl
import com.supergram.app.data.repository.TelegramAuthRepositoryImpl
import com.supergram.app.domain.repository.ChatRepository
import com.supergram.app.domain.repository.TelegramAuthRepository
import com.supergram.app.domain.usecase.LoadChatHistoryUseCase
import com.supergram.app.domain.usecase.LoadChatsUseCase
import com.supergram.app.domain.usecase.ObserveAuthStateUseCase
import com.supergram.app.domain.usecase.ObserveChatListUseCase
import com.supergram.app.domain.usecase.ObserveNewMessagesUseCase
import com.supergram.app.domain.usecase.RequestAuthCodeUseCase
import com.supergram.app.domain.usecase.SendMessageUseCase
import com.supergram.app.domain.usecase.SubmitAuthCodeUseCase
import com.supergram.app.domain.usecase.SubmitPasswordUseCase

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

    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(telegramClientManager)
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

    val observeChatList: ObserveChatListUseCase by lazy {
        ObserveChatListUseCase(chatRepository)
    }

    val loadChats: LoadChatsUseCase by lazy {
        LoadChatsUseCase(chatRepository)
    }

    val loadChatHistory: LoadChatHistoryUseCase by lazy {
        LoadChatHistoryUseCase(chatRepository)
    }

    val observeNewMessages: ObserveNewMessagesUseCase by lazy {
        ObserveNewMessagesUseCase(chatRepository)
    }

    val sendMessage: SendMessageUseCase by lazy {
        SendMessageUseCase(chatRepository)
    }
}
