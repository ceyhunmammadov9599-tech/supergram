package com.supergram.app.presentation.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.SearchPage
import com.supergram.app.domain.model.SearchFilter
import com.supergram.app.domain.model.SearchPaginator
import kotlinx.coroutines.flow.combine
import com.supergram.app.domain.usecase.searchDebounce
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

/**
 * MVVM ViewModel for the chat list screen.
 * Loads the chat list once and keeps observing the live TDLib-backed stream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel(
    observeChatList: com.supergram.app.domain.usecase.ObserveChatListUseCase,
    private val loadChats: com.supergram.app.domain.usecase.LoadChatsUseCase,
    private val searchMessages: com.supergram.app.domain.usecase.SearchMessagesUseCase,
) : ViewModel() {

    /** Live, ordered chat list (kept fresh by TDLib updates). */
    val chats: StateFlow<List<Chat>> = observeChatList()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // ------------------------- Global message search -------------------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    /** Active content filter for the global search (chips in the search bar). */
    private val _searchFilter = MutableStateFlow(SearchFilter.ALL)
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    /** Paged global-search state: accumulated results + TDLib offset cursor. */
    private val _paginator = MutableStateFlow(SearchPaginator())
    val paginator: StateFlow<SearchPaginator> = _paginator.asStateFlow()

    /** Global cross-chat results, derived from the paged paginator state. */
    val searchResults: StateFlow<List<com.supergram.app.domain.model.SearchResult>> =
        _paginator
            .map { it.results }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Debounced query + active filter -> reset the paginator and fetch
        // the first page. Filter changes take effect immediately.
        combine(
            _searchQuery.searchDebounce(),
            _searchFilter,
        ) { query, filter -> query to filter }
            .onEach { (query, filter) ->
                _paginator.value = SearchPaginator.start(query, filter)
                if (query.isNotBlank()) loadSearchPage()
            }
            .launchIn(viewModelScope)
    }

    /** Chip action: switch the search content filter (resets pagination). */
    fun setFilter(filter: SearchFilter) {
        if (_searchFilter.value != filter) _searchFilter.value = filter
    }

    /**
     * Loads the next global-search page from the paginator cursor.
     * Guarded by hasMore + loadingMore so the list can call it freely on
     * its load-more trigger without duplicating requests.
     */
    fun loadMoreSearch() {
        val state = _paginator.value
        if (state.query.isBlank() || !state.hasMore || state.loadingMore) return
        _searchJob?.cancel()
        _searchJob = viewModelScope.launch { loadSearchPage() }
    }

    private var _searchJob: Job? = null

    private suspend fun loadSearchPage() {
        val state = _paginator.value
        if (!state.hasMore || state.loadingMore) return
        _paginator.value = state.beginLoadingMore()
        searchMessages(state.query, offset = state.nextOffset ?: "", filter = state.filter)
            .fold(
                onSuccess = { page: SearchPage -> _paginator.value = _paginator.value.appendGlobalPage(page) },
                onFailure = { _paginator.value = _paginator.value.loadFailed() },
            )
    }

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        _searchActive.value = !_searchActive.value
        if (!_searchActive.value) {
            _searchQuery.value = ""
            _paginator.value = SearchPaginator()
        }
    }

    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
        _paginator.value = SearchPaginator()
    }

    init {
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            loadChats()
                .onFailure { _error.value = it.message ?: "Failed to load chats" }
            _loading.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        fun create(): ChatListViewModel = ChatListViewModel(
            AppContainer.observeChatList,
            AppContainer.loadChats,
            AppContainer.searchMessages,
        )
    }
}
