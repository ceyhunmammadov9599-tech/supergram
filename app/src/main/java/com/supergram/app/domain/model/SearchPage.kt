package com.supergram.app.domain.model

/**
 * One page of search results together with TDLib's pagination cursor.
 *
 *  - Global search (SearchMessages -> FoundMessages) pages via a
 *    [nextOffset] string passed back as the next request's offset.
 *  - In-chat search (SearchChatMessages -> FoundChatMessages) pages via
 *    [nextFromMessageId] passed back as the next request's fromMessageId.
 *
 * A null cursor means "no more pages".
 */
data class SearchPage(
    val results: List<SearchResult>,
    val nextOffset: String? = null,
    val nextFromMessageId: Long? = null,
)

/**
 * Immutable pagination state accumulator for search results.
 * Pure state machine: every transition returns a new value, so the whole
 * cursor/accumulator behavior is unit-testable without Android or TDLib.
 *
 * @param results       Accumulated, de-duplicated results in page order.
 * @param nextOffset    Global-search cursor for the next page ("" = start).
 * @param nextFromMessageId In-chat-search cursor for the next page (0 = start).
 * @param hasMore       True when another page can be requested.
 * @param loadingMore   True while a page request is in flight.
 */
data class SearchPaginator(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val nextOffset: String? = "",
    val nextFromMessageId: Long? = 0L,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
) {
    companion object {
        /** Fresh state for a new query: no results, cursors at start. */
        fun start(query: String): SearchPaginator = SearchPaginator(
            query = query,
            nextOffset = "",
            nextFromMessageId = 0L,
            hasMore = true,
            loadingMore = false,
        )
    }

    /** Marks a page request as in flight (load-more guard). */
    fun beginLoadingMore(): SearchPaginator = copy(loadingMore = true)

    /**
     * Appends a global-search page: accumulates de-duplicated results and
     * adopts the page's [SearchPage.nextOffset] cursor. An empty page or a
     * null/blank cursor ends pagination.
     */
    fun appendGlobalPage(page: SearchPage): SearchPaginator = copy(
        results = (results + page.results).distinctBy { it.messageId },
        nextOffset = page.nextOffset,
        hasMore = page.results.isNotEmpty() && !page.nextOffset.isNullOrBlank(),
        loadingMore = false,
    )

    /**
     * Appends an in-chat-search page: accumulates de-duplicated results and
     * adopts the page's [SearchPage.nextFromMessageId] cursor. An empty page
     * or a zero cursor ends pagination.
     */
    fun appendChatPage(page: SearchPage): SearchPaginator = copy(
        results = (results + page.results).distinctBy { it.messageId },
        nextFromMessageId = page.nextFromMessageId,
        hasMore = page.results.isNotEmpty() && (page.nextFromMessageId ?: 0L) > 0L,
        loadingMore = false,
    )

    /** A failed page request keeps the accumulated results, stops loading. */
    fun loadFailed(): SearchPaginator = copy(loadingMore = false)
}
