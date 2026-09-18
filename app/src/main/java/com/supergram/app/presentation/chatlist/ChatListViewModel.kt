package com.supergram.app.presentation.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.SearchResult
import com.supergram.app.domain.usecase.searchDebounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    /** Global cross-chat results, refreshed from the debounced query (300 ms). */
    val searchResults: StateFlow<List<SearchResult>> =
        _searchQuery
            .searchDebounce()
            .flatMapLatest { query ->
                flow {
                    searchMessages(query)
                        .onSuccess { emit(it) }
                        .onFailure { emit(emptyList()) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        _searchActive.value = !_searchActive.value
        if (!_searchActive.value) _searchQuery.value = ""
    }

    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
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
