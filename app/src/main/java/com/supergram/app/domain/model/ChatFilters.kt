package com.supergram.app.domain.model

/**
 * Pure chat-list filtering used by the category tabs ("folder splitting").
 * Extracted from the UI so it is unit-testable.
 */
fun List<Chat>.filterByCategory(category: ChatCategory?): List<Chat> =
    if (category == null) this else filter { it.category == category }
