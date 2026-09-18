package com.supergram.app.presentation.chatdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * MVVM ViewModel for a single chat: message history + real-time incoming
 * messages + outgoing message dispatch + chat lifecycle (open/close) +
 * read state + media download states.
 */
class ChatDetailViewModel(
    private val chatId: Long,
    observeNewMessages: com.supergram.app.domain.usecase.ObserveNewMessagesUseCase,
    private val loadChatHistory: com.supergram.app.domain.usecase.LoadChatHistoryUseCase,
    private val sendMessage: com.supergram.app.domain.usecase.SendMessageUseCase,
    private val openChat: com.supergram.app.domain.usecase.OpenChatUseCase,
    private val closeChat: com.supergram.app.domain.usecase.CloseChatUseCase,
    private val viewMessages: com.supergram.app.domain.usecase.ViewMessagesUseCase,
    private val downloadFileUseCase: com.supergram.app.domain.usecase.DownloadFileUseCase,
    observeFileUpdates: com.supergram.app.domain.usecase.ObserveFileUpdatesUseCase,
) : ViewModel() {

    /** Newest messages first (as returned by TDLib); UI reverses for display. */
    private val history = MutableStateFlow<List<Message>>(emptyList())

    /** Real-time messages for this chat, merged into the stream below. */
    private val incoming = MutableStateFlow<List<Message>>(emptyList())

    /** Live download states per TDLib file id. */
    private val mediaStates = MutableStateFlow<Map<Int, MediaFile>>(emptyMap())

    /** All messages for this chat, newest first, with live media states applied. */
    val messages: StateFlow<List<Message>> =
        combine(history, incoming, mediaStates) { loaded, live, states ->
            (loaded + live)
                .distinctBy { it.id }
                .sortedWith(compareByDescending<Message> { it.date }.thenByDescending { it.id })
                .map { message ->
                    val media = message.media
                    if (media != null) message.copy(media = states[media.fileId] ?: media) else message
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        // Chat lifecycle: open on enter (TDLib stream optimization).
        viewModelScope.launch {
            openChat(chatId)
                .onFailure { _error.value = it.message ?: "Failed to open chat" }
        }

        // History page -> mark as viewed (clears unread counters).
        viewModelScope.launch {
            loadChatHistory(chatId)
                .onSuccess { page ->
                    history.value = page
                    viewMessages(chatId, page.map { it.id })
                }
                .onFailure { _error.value = it.message ?: "Failed to load history" }
        }

        // Real-time stream for this chat only.
        observeNewMessages()
            .filter { it.chatId == chatId }
            .onEach { message ->
                incoming.value = (incoming.value + message).distinctBy { it.id }
                // Viewing arrived messages keeps unread counters at zero while open.
                viewMessages(chatId, listOf(message.id))
            }
            .launchIn(viewModelScope)

        // Live file download progress.
        observeFileUpdates()
            .onEach { state -> mediaStates.value = mediaStates.value + (state.fileId to state) }
            .launchIn(viewModelScope)
    }

    fun send(text: String) {
        if (text.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            sendMessage(chatId, text)
                .onSuccess { incoming.value = (incoming.value + Message(
                    id = -System.nanoTime(), // optimistic local echo
                    chatId = chatId,
                    senderUserId = null,
                    text = text,
                    isOutgoing = true,
                    date = System.currentTimeMillis() / 1000,
                    media = null,
                )).distinctBy { it.id } }
                .onFailure { _error.value = it.message ?: "Failed to send message" }
            _sending.value = false
        }
    }

    fun loadMore() {
        val oldest = (history.value + incoming.value).minByOrNull { it.id }
        val fromMessageId = oldest?.id?.takeIf { it > 0 } ?: 0L
        viewModelScope.launch {
            loadChatHistory(chatId, fromMessageId)
                .onSuccess { page ->
                    if (page.isNotEmpty()) {
                        history.value = (page + history.value).distinctBy { it.id }
                        viewMessages(chatId, page.map { it.id })
                    }
                }
        }
    }

    /** Starts (or resumes) downloading an attached photo/document. */
    fun downloadFile(fileId: Int) {
        viewModelScope.launch {
            downloadFileUseCase(fileId)
                .onFailure { _error.value = it.message ?: "Failed to start download" }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Application-level scope so the CloseChat request survives onCleared(). */
    private val lifecycleScope = CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    override fun onCleared() {
        // Chat lifecycle: close on exit (viewModelScope is already cancelled).
        lifecycleScope.launch { closeChat(chatId) }
        super.onCleared()
    }

    companion object {
        fun create(chatId: Long): ChatDetailViewModel = ChatDetailViewModel(
            chatId,
            AppContainer.observeNewMessages,
            AppContainer.loadChatHistory,
            AppContainer.sendMessage,
            AppContainer.openChat,
            AppContainer.closeChat,
            AppContainer.viewMessages,
            AppContainer.downloadFile,
            AppContainer.observeFileUpdates,
        )
    }
}
