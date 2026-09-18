package com.supergram.app.presentation.chatdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.Message
import com.supergram.app.domain.model.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.supergram.app.domain.usecase.searchDebounce

/**
 * MVVM ViewModel for a single chat: message history + real-time incoming
 * messages + outgoing message dispatch + chat lifecycle (open/close) +
 * read state + media download states + in-chat search with jump-to-message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
    private val voicePlayer: com.supergram.app.core.audio.VoiceNotePlayer,
    private val searchChatMessages: com.supergram.app.domain.usecase.SearchChatMessagesUseCase,
    private val targetMessageId: Long? = null,
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

    /** Live voice note playback state (active file, progress). */
    val voiceState: StateFlow<com.supergram.app.core.audio.VoiceNotePlayer.PlaybackState> =
        voicePlayer.state

    /** Decides play vs download and auto-plays once a pending download completes. */
    private val autoPlay = com.supergram.app.domain.usecase.VoiceAutoPlayController()

    // ------------------------- In-chat search -------------------------

    /** Current search query (debounced 300 ms before hitting TDLib). */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    /** In-chat search results, refreshed reactively from the debounced query. */
    val searchResults: StateFlow<List<SearchResult>> =
        _searchQuery
            .searchDebounce()
            .flatMapLatest { query ->
                flow {
                    searchChatMessages(chatId, query)
                        .onSuccess { emit(it) }
                        .onFailure { emit(emptyList()) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Message id the UI should scroll to (jump-to-result), or null. */
    private val _scrollTarget = MutableStateFlow<Long?>(null)
    val scrollTarget: StateFlow<Long?> = _scrollTarget.asStateFlow()

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
            // Deep link from global search: load a window around the target.
            targetMessageId?.let { jumpToMessageById(it) }
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
            .onEach { state ->
                mediaStates.value = mediaStates.value + (state.fileId to state)
                // Auto-play a voice note once its download completes.
                autoPlay.onFileUpdated(state)?.let { path ->
                    voicePlayer.play(state.fileId, path)
                }
            }
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

    /**
     * Play/pause toggle for a voice note. If the file is not downloaded yet,
     * the download starts and playback begins automatically once complete.
     */
    fun toggleVoice(media: MediaFile) {
        when (val action = autoPlay.onToggle(media)) {
            is com.supergram.app.domain.usecase.VoiceAutoPlayController.Action.Play ->
                voicePlayer.toggle(media.fileId, action.path)
            is com.supergram.app.domain.usecase.VoiceAutoPlayController.Action.Download ->
                downloadFile(action.fileId)
        }
    }

    // ------------------------- Search actions -------------------------

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        _searchActive.value = !_searchActive.value
        if (!_searchActive.value) {
            _searchQuery.value = ""
        }
    }

    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
    }

    fun clearScrollTarget() {
        _scrollTarget.value = null
    }

    /** Jump to a search result inside this chat. */
    fun jumpToMessage(result: SearchResult) {
        jumpToMessageById(result.messageId)
    }

    /**
     * Scrolls to a message id when possible. If the message is outside the
     * loaded window, loads the surrounding pages from TDLib, merges them
     * into the stream, and then scrolls.
     */
    fun jumpToMessageById(messageId: Long) {
        if (messages.value.any { it.id == messageId }) {
            _scrollTarget.value = messageId
            return
        }
        viewModelScope.launch {
            val after = loadChatHistory(chatId, fromMessageId = messageId + 1, limit = 25)
                .getOrDefault(emptyList())
            val before = loadChatHistory(chatId, fromMessageId = messageId, limit = 25)
                .getOrDefault(emptyList())
            val window = (after + before).distinctBy { it.id }
            if (window.isNotEmpty()) {
                history.value = (history.value + window)
                    .distinctBy { it.id }
                    .sortedWith(compareByDescending<Message> { it.date }.thenByDescending { it.id })
                _scrollTarget.value = messageId
            } else {
                _error.value = "Message not found"
            }
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
        voicePlayer.stopPlayback()
        super.onCleared()
    }

    companion object {
        fun create(
            chatId: Long,
            targetMessageId: Long? = null,
        ): ChatDetailViewModel = ChatDetailViewModel(
            chatId,
            AppContainer.observeNewMessages,
            AppContainer.loadChatHistory,
            AppContainer.sendMessage,
            AppContainer.openChat,
            AppContainer.closeChat,
            AppContainer.viewMessages,
            AppContainer.downloadFile,
            AppContainer.observeFileUpdates,
            AppContainer.voicePlayer,
            AppContainer.searchChatMessages,
            targetMessageId,
        )
    }
}
