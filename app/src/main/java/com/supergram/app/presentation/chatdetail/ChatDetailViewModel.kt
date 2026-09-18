package com.supergram.app.presentation.chatdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MVVM ViewModel for a single chat: message history + real-time incoming
 * messages + outgoing message dispatch.
 */
class ChatDetailViewModel(
    private val chatId: Long,
    observeNewMessages: com.supergram.app.domain.usecase.ObserveNewMessagesUseCase,
    private val loadChatHistory: com.supergram.app.domain.usecase.LoadChatHistoryUseCase,
    private val sendMessage: com.supergram.app.domain.usecase.SendMessageUseCase,
) : ViewModel() {

    /** Newest messages first (as returned by TDLib); UI reverses for display. */
    private val history = MutableStateFlow<List<Message>>(emptyList())

    /** Real-time messages for this chat, merged into the stream below. */
    private val incoming = MutableStateFlow<List<Message>>(emptyList())

    /** All messages for this chat, newest first. */
    val messages: StateFlow<List<Message>> =
        combine(history, incoming) { loaded, live ->
                (loaded + live)
                    .distinctBy { it.id }
                    .sortedWith(compareByDescending<Message> { it.date }.thenByDescending { it.id })
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        // History page
        viewModelScope.launch {
            loadChatHistory(chatId)
                .onSuccess { history.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load history" }
        }
        // Real-time stream for this chat only
        viewModelScope.launch {
            observeNewMessages()
                .filter { it.chatId == chatId }
                .collect { message ->
                    incoming.value = (incoming.value + message).distinctBy { it.id }
                }
        }
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
                    }
                }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        fun create(chatId: Long): ChatDetailViewModel = ChatDetailViewModel(
            chatId,
            AppContainer.observeNewMessages,
            AppContainer.loadChatHistory,
            AppContainer.sendMessage,
        )
    }
}
