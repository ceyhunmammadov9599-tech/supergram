package com.supergram.app.presentation.chatdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.Message
import com.supergram.app.domain.model.SearchPage
import com.supergram.app.domain.model.SearchPaginator
import com.supergram.app.domain.model.DeleteDialogState
import com.supergram.app.domain.model.MessageSelection
import com.supergram.app.domain.model.unreadBoundary
import com.supergram.app.domain.model.ReplyDraft
import com.supergram.app.domain.model.SearchResult
import com.supergram.app.domain.model.mergeMessages
import com.supergram.app.domain.model.newestMessageId
import com.supergram.app.domain.model.oldestMessageId
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.map
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
    observeChatList: com.supergram.app.domain.usecase.ObserveChatListUseCase,
    private val loadChatHistory: com.supergram.app.domain.usecase.LoadChatHistoryUseCase,
    private val sendMessage: com.supergram.app.domain.usecase.SendMessageUseCase,
    private val forwardMessages: com.supergram.app.domain.usecase.ForwardMessagesUseCase,
    private val deleteMessages: com.supergram.app.domain.usecase.DeleteMessagesUseCase,
    private val openChat: com.supergram.app.domain.usecase.OpenChatUseCase,
    private val closeChat: com.supergram.app.domain.usecase.CloseChatUseCase,
    private val viewMessages: com.supergram.app.domain.usecase.ViewMessagesUseCase,
    private val downloadFileUseCase: com.supergram.app.domain.usecase.DownloadFileUseCase,
    observeFileUpdates: com.supergram.app.domain.usecase.ObserveFileUpdatesUseCase,
    private val voicePlayer: com.supergram.app.core.audio.VoiceNotePlayer,
    private val searchChatMessages: com.supergram.app.domain.usecase.SearchChatMessagesUseCase,
    private val getPinnedMessage: com.supergram.app.domain.usecase.GetPinnedMessageUseCase,
    private val getUnreadCursor: com.supergram.app.domain.usecase.GetUnreadCursorUseCase,
    observePinnedChanges: com.supergram.app.domain.usecase.ObservePinnedChangesUseCase,
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

    // ---------------------- Multi-select, pinned and unread state ----------------------

    private val _selection = MutableStateFlow(MessageSelection())
    val selection: StateFlow<MessageSelection> = _selection.asStateFlow()
    private val _batchForwardIds = MutableStateFlow<List<Long>>(emptyList())
    val batchForwardIds: StateFlow<List<Long>> = _batchForwardIds.asStateFlow()
    private val _batchDeleteIds = MutableStateFlow<List<Long>>(emptyList())
    private val _pinnedMessage = MutableStateFlow<Message?>(null)
    val pinnedMessage: StateFlow<Message?> = _pinnedMessage.asStateFlow()
    private val _hiddenPinnedId = MutableStateFlow<Long?>(null)
    val hiddenPinnedId: StateFlow<Long?> = _hiddenPinnedId.asStateFlow()
    private val _unreadCursor = MutableStateFlow<Long?>(null)
    val unreadBoundaryId: StateFlow<Long?> =
        combine(messages, _unreadCursor) { list, cursor ->
            cursor?.let { unreadBoundary(list.asReversed(), it) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun startSelection(messageId: Long) {
        _selection.value = _selection.value.start(messageId)
        _contextMenuMessage.value = null
    }
    fun toggleSelection(messageId: Long) {
        _selection.value = _selection.value.toggle(messageId)
    }
    fun selectAllMessages() {
        _selection.value = _selection.value.selectAll(messages.value.asReversed())
    }
    fun clearSelection() {
        _selection.value = _selection.value.clear()
    }
    fun startBatchForward() {
        val ids = _selection.value.payload(messages.value.asReversed())
        if (ids.isNotEmpty()) _batchForwardIds.value = ids
    }
    fun startBatchDelete() {
        val ids = _selection.value.payload(messages.value.asReversed())
        if (ids.isEmpty()) return
        _batchDeleteIds.value = ids
        val canRevoke = messages.value.filter { it.id in ids }.all { it.isOutgoing }
        _deleteDialog.value = DeleteDialogState.HIDDEN.shownFor(ids.first(), canRevoke)
    }
    fun dismissPinnedMessage() {
        _hiddenPinnedId.value = _pinnedMessage.value?.id
    }
    fun jumpToPinnedMessage() {
        _pinnedMessage.value?.id?.let(::jumpToMessageById)
    }
    private suspend fun refreshPinnedMessage() {
        getPinnedMessage(chatId)
            .onSuccess { pinned -> _pinnedMessage.value = pinned }
            .onFailure { _error.value = it.message ?: "Failed to load pinned message" }
    }

    // ---------------------- Message context actions ----------------------

    /** All chats, for the forward destination picker. */
    val chats: StateFlow<List<com.supergram.app.domain.model.Chat>> =
        observeChatList().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The message whose long-press context menu (bottom sheet) is open. */
    private val _contextMenuMessage = MutableStateFlow<Message?>(null)
    val contextMenuMessage: StateFlow<Message?> = _contextMenuMessage.asStateFlow()

    /** The message being forwarded (destination picker is open for it). */
    private val _forwardTarget = MutableStateFlow<Message?>(null)
    val forwardTarget: StateFlow<Message?> = _forwardTarget.asStateFlow()

    /** In-progress reply draft shown in the dismissible preview bar. */
    private val _replyDraft = MutableStateFlow<ReplyDraft?>(null)
    val replyDraft: StateFlow<ReplyDraft?> = _replyDraft.asStateFlow()

    /** Delete-confirmation dialog state (pure state machine). */
    private val _deleteDialog = MutableStateFlow(DeleteDialogState.HIDDEN)
    val deleteDialog: StateFlow<DeleteDialogState> = _deleteDialog.asStateFlow()

    fun openContextMenu(message: Message) {
        _contextMenuMessage.value = message
    }

    fun closeContextMenu() {
        _contextMenuMessage.value = null
    }

    /** Reply action: remember the draft; the input bar now sends with replyTo. */
    fun startReply(message: Message) {
        _replyDraft.value = ReplyDraft.of(message)
        _contextMenuMessage.value = null
    }

    fun cancelReply() {
        _replyDraft.value = null
    }

    /** Forward action: open the destination picker for this message. */
    fun startForward(message: Message) {
        _forwardTarget.value = message
        _contextMenuMessage.value = null
    }

    fun closeForwardPicker() {
        _forwardTarget.value = null
        _batchForwardIds.value = emptyList()
    }

    /** Forwards the pending target to the given destination chat. */
    fun forwardTo(toChatId: Long) {
        val target = _forwardTarget.value
        val ids = _batchForwardIds.value.ifEmpty { target?.let { listOf(it.id) } ?: emptyList() }
        if (ids.isEmpty()) return
        if (toChatId == chatId) {
            _forwardTarget.value = null
            _batchForwardIds.value = emptyList()
            return // forwarding to the same chat: no-op (avoid duplicates)
        }
        viewModelScope.launch {
            forwardMessages(chatId, toChatId, ids)
                .onSuccess {
                    _forwardTarget.value = null
                    _batchForwardIds.value = emptyList()
                    clearSelection()
                }
                .onFailure { _error.value = it.message ?: "Failed to forward message" }
        }
    }

    /** Delete action: open the confirmation dialog. */
    fun showDeleteDialog(message: Message) {
        _deleteDialog.value = _deleteDialog.value.shownFor(message.id, canRevoke = message.isOutgoing)
        _contextMenuMessage.value = null
    }

    /** Revoke ("delete for everyone") checkbox toggle. */
    fun toggleDeleteRevoke() {
        _deleteDialog.value = _deleteDialog.value.revokeToggled()
    }

    fun dismissDeleteDialog() {
        _deleteDialog.value = _deleteDialog.value.dismissed()
        _batchDeleteIds.value = emptyList()
    }

    /** Confirmed deletion: dispatch via TDLib and drop the message locally. */
    fun confirmDelete() {
        val request = _deleteDialog.value.confirmed() ?: return
        val ids = _batchDeleteIds.value.ifEmpty { listOf(request.messageId) }
        _deleteDialog.value = _deleteDialog.value.dismissed()
        _batchDeleteIds.value = emptyList()
        viewModelScope.launch {
            deleteMessages(chatId, ids, request.revoke)
                .onSuccess {
                    history.value = history.value.filterNot { it.id in ids }
                    incoming.value = incoming.value.filterNot { it.id in ids }
                    clearSelection()
                }
                .onFailure { _error.value = it.message ?: "Failed to delete message" }
        }
    }

    // ------------------------- In-chat search -------------------------

    /** Current search query (debounced 300 ms before hitting TDLib). */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    /** Paged in-chat search state: accumulated results + nextFromMessageId cursor. */
    private val _paginator = MutableStateFlow(SearchPaginator())
    val searchPaginator: StateFlow<SearchPaginator> = _paginator.asStateFlow()

    /** In-chat search results, derived from the paged paginator state. */
    val searchResults: StateFlow<List<SearchResult>> =
        _paginator
            .map { it.results }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Debounced query -> reset the paginator and fetch the first page.
        _searchQuery
            .searchDebounce()
            .onEach { query ->
                _paginator.value = SearchPaginator.start(query)
                if (query.isNotBlank()) loadSearchPage()
            }
            .launchIn(viewModelScope)
    }

    /** Loads the next in-chat search page from the paginator cursor. */
    fun loadMoreSearch() {
        val state = _paginator.value
        if (state.query.isBlank() || !state.hasMore || state.loadingMore) return
        viewModelScope.launch { loadSearchPage() }
    }

    private suspend fun loadSearchPage() {
        val state = _paginator.value
        if (!state.hasMore || state.loadingMore) return
        _paginator.value = state.beginLoadingMore()
        searchChatMessages(chatId, state.query, fromMessageId = state.nextFromMessageId ?: 0L)
            .fold(
                onSuccess = { page: SearchPage -> _paginator.value = _paginator.value.appendChatPage(page) },
                onFailure = { _paginator.value = _paginator.value.loadFailed() },
            )
    }

    /** Message id the UI should scroll to (jump-to-result), or null. */
    private val _scrollTarget = MutableStateFlow<Long?>(null)
    val scrollTarget: StateFlow<Long?> = _scrollTarget.asStateFlow()

    // ------------------------- Bidirectional paging -------------------------

    /** True while older messages can still be requested. */
    private val _hasOlder = MutableStateFlow(true)
    val hasOlder: StateFlow<Boolean> = _hasOlder.asStateFlow()

    /**
     * True while newer messages exist outside the loaded window (typical
     * after jumping deep into history). Scrolling to the bottom pages them in.
     */
    private val _hasNewer = MutableStateFlow(false)
    val hasNewer: StateFlow<Boolean> = _hasNewer.asStateFlow()

    /** True while a page request is in flight (guards list triggers). */
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    /** Count of older messages prepended by the last loadMore (scroll-anchor signal). */
    private val _prependCount = MutableStateFlow(0)
    val prependCount: StateFlow<Int> = _prependCount.asStateFlow()

    /** Page size for both directions. */
    private val pageSize = 50

    init {
        // Snapshot the read cursor BEFORE opening/viewing the chat. ViewMessages
        // clears TDLib's unread count, so later reads cannot recover this divider.
        viewModelScope.launch {
            getUnreadCursor(chatId).onSuccess { _unreadCursor.value = it }
            refreshPinnedMessage()
            openChat(chatId)
                .onFailure { _error.value = it.message ?: "Failed to open chat" }
            loadChatHistory(chatId)
                .onSuccess { page ->
                    history.value = page
                    viewMessages(chatId, page.map { it.id })
                    _hasNewer.value = false
                }
                .onFailure { _error.value = it.message ?: "Failed to load history" }
            targetMessageId?.let { jumpToMessageById(it) }
        }

        observePinnedChanges().filter { it == chatId }
            .onEach { refreshPinnedMessage() }
            .launchIn(viewModelScope)

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
        val replyTo = _replyDraft.value?.messageId
        viewModelScope.launch {
            _sending.value = true
            sendMessage(chatId, text, replyToMessageId = replyTo)
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
            _replyDraft.value = null
        }
    }

    /**
     * Pages to OLDER messages (upward scroll direction). Signals the screen
     * about the number of prepended items so it can preserve the scroll
     * anchor. An empty page flips [hasOlder] off (end of history).
     */
    fun loadMore() {
        if (_historyLoading.value || !_hasOlder.value) return
        val fromMessageId = (history.value + incoming.value).oldestMessageId() ?: return
        viewModelScope.launch {
            _historyLoading.value = true
            loadChatHistory(chatId, fromMessageId)
                .onSuccess { page ->
                    val known = history.value.map { it.id }.toSet()
                    val fresh = page.filter { it.id !in known }
                    if (fresh.isEmpty()) {
                        _hasOlder.value = false
                    } else {
                        history.value = mergeMessages(history.value, fresh)
                        _prependCount.value = _prependCount.value + fresh.size
                        viewMessages(chatId, fresh.map { it.id })
                    }
                }
            _historyLoading.value = false
        }
    }

    /**
     * Pages to NEWER messages (downward scroll direction). Uses TDLib's
     * negative offset (fromMessageId, offset = -pageSize, limit = pageSize)
     * which returns the anchor message plus up to pageSize-1 newer ones.
     * Only reachable after a jump-to-message (otherwise hasNewer is false);
     * an empty page flips [hasNewer] off (caught up with the newest).
     */
    fun loadNewer() {
        if (_historyLoading.value || !_hasNewer.value) return
        val anchorId = (history.value + incoming.value).newestMessageId() ?: return
        viewModelScope.launch {
            _historyLoading.value = true
            loadChatHistory(chatId, fromMessageId = anchorId, limit = pageSize, offset = -pageSize)
                .onSuccess { page ->
                    val known = history.value.map { it.id }.toSet()
                    val fresh = page.filter { it.id !in known && it.id > anchorId }
                    if (fresh.isEmpty()) {
                        _hasNewer.value = false
                    } else {
                        history.value = mergeMessages(history.value, fresh)
                        viewMessages(chatId, fresh.map { it.id })
                    }
                }
            _historyLoading.value = false
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
            // The window AROUND the target: the target plus newer context via a
            // negative offset, and older context below it.
            val newer = loadChatHistory(chatId, fromMessageId = messageId, limit = 50, offset = -50)
                .getOrDefault(emptyList())
                .filter { it.id >= messageId }
            val older = loadChatHistory(chatId, fromMessageId = messageId, limit = 25)
                .getOrDefault(emptyList())
                .filter { it.id < messageId }
            val window = mergeMessages(newer, older)
            if (window.any { it.id == messageId }) {
                history.value = mergeMessages(history.value, window)
                // There is newer history above the window unless the newer
                // page came up short (i.e. it reached the newest messages).
                _hasNewer.value = newer.size < 50
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
        clearSelection()
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
            AppContainer.observeChatList,
            AppContainer.loadChatHistory,
            AppContainer.sendMessage,
            AppContainer.forwardMessages,
            AppContainer.deleteMessages,
            AppContainer.openChat,
            AppContainer.closeChat,
            AppContainer.viewMessages,
            AppContainer.downloadFile,
            AppContainer.observeFileUpdates,
            AppContainer.voicePlayer,
            AppContainer.searchChatMessages,
            AppContainer.getPinnedMessage,
            AppContainer.getUnreadCursor,
            AppContainer.observePinnedChanges,
            targetMessageId,
        )
    }
}
