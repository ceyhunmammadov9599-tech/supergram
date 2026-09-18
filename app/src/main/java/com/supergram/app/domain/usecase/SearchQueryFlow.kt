package com.supergram.app.domain.usecase

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Search input processing pipeline: trim -> debounce (default 300 ms) ->
 * drop duplicates -> ignore blank queries. Extracted as a pure extension so
 * the debounce behavior is unit-testable.
 */
@OptIn(FlowPreview::class)
fun Flow<String>.searchDebounce(debounceMs: Long = 300L): Flow<String> =
    map { it.trim() }
        .debounce(debounceMs)
        .distinctUntilChanged()
        .filter { it.isNotBlank() }
