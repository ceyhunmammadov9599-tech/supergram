package com.supergram.app.di

import com.supergram.app.core.audio.VoiceNotePlayer
import com.supergram.app.core.telegram.TelegramClientManager
import com.supergram.app.data.repository.ChatRepositoryImpl
import com.supergram.app.data.repository.TelegramAuthRepositoryImpl
import com.supergram.app.domain.repository.ChatRepository
import com.supergram.app.domain.repository.TelegramAuthRepository
import com.supergram.app.domain.usecase.LoadChatHistoryUseCase
import com.supergram.app.domain.usecase.LoadChatsUseCase
import com.supergram.app.domain.usecase.ObserveAuthStateUseCase
import com.supergram.app.domain.usecase.CloseChatUseCase
import com.supergram.app.domain.usecase.DownloadFileUseCase
import com.supergram.app.domain.usecase.ObserveChatListUseCase
import com.supergram.app.domain.usecase.ObserveFileUpdatesUseCase
import com.supergram.app.domain.usecase.ObserveNewMessagesUseCase
import com.supergram.app.domain.usecase.RequestAuthCodeUseCase
import com.supergram.app.domain.usecase.OpenChatUseCase
import com.supergram.app.domain.usecase.SendMessageUseCase
import com.supergram.app.domain.usecase.ViewMessagesUseCase
import com.supergram.app.domain.usecase.SubmitAuthCodeUseCase
import com.supergram.app.domain.usecase.SubmitPasswordUseCase

/**
 * Lightweight manual dependency container (no DI framework — keeps the CLI
 * project small and deterministic).
 */
object AppContainer {

    val telegramClientManager: TelegramClientManager
        get() = TelegramClientManager

    /** App-scoped voice note playback engine (native MediaPlayer). */
    val voicePlayer: VoiceNotePlayer by lazy { VoiceNotePlayer() }

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

    val openChat: OpenChatUseCase by lazy {
        OpenChatUseCase(chatRepository)
    }

    val closeChat: CloseChatUseCase by lazy {
        CloseChatUseCase(chatRepository)
    }

    val viewMessages: ViewMessagesUseCase by lazy {
        ViewMessagesUseCase(chatRepository)
    }

    val downloadFile: DownloadFileUseCase by lazy {
        DownloadFileUseCase(chatRepository)
    }
    /** In-chat message search (TdApi.SearchChatMessages). */
    val searchChatMessages: com.supergram.app.domain.usecase.SearchChatMessagesUseCase by lazy {
        com.supergram.app.domain.usecase.SearchChatMessagesUseCase(chatRepository)
    }

    /** Global cross-chat message search (TdApi.SearchMessages). */
    val searchMessages: com.supergram.app.domain.usecase.SearchMessagesUseCase by lazy {
        com.supergram.app.domain.usecase.SearchMessagesUseCase(chatRepository)
    }

    /** Forwards messages between chats (TdApi.ForwardMessages). */
    val forwardMessages: com.supergram.app.domain.usecase.ForwardMessagesUseCase by lazy {
        com.supergram.app.domain.usecase.ForwardMessagesUseCase(chatRepository)
    }

    /** Deletes messages (TdApi.DeleteMessages). */
    val deleteMessages: com.supergram.app.domain.usecase.DeleteMessagesUseCase by lazy {
        com.supergram.app.domain.usecase.DeleteMessagesUseCase(chatRepository)
    }

    val getPinnedMessage by lazy {
        com.supergram.app.domain.usecase.GetPinnedMessageUseCase(chatRepository)
    }
    val getUnreadCursor by lazy {
        com.supergram.app.domain.usecase.GetUnreadCursorUseCase(chatRepository)
    }
    val observePinnedChanges by lazy {
        com.supergram.app.domain.usecase.ObservePinnedChangesUseCase(chatRepository)
    }

    val observeFileUpdates: ObserveFileUpdatesUseCase by lazy {
        ObserveFileUpdatesUseCase(chatRepository)
    }
}
