package com.supergram.app.domain.model

/**
 * Merges two message lists (e.g. the loaded window and a newly paged
 * window) into the canonical stream order: newest first (date desc,
 * then id desc), de-duplicated by message id.
 * Pure function so bidirectional paging is unit-testable.
 */
fun mergeMessages(existing: List<Message>, page: List<Message>): List<Message> =
    (existing + page)
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Message> { it.date }.thenByDescending { it.id })

/**
 * The newest (largest) positive message id in the list, or null.
 * Optimistic local echoes use negative ids and are skipped.
 */
fun List<Message>.newestMessageId(): Long? =
    asSequence().map { it.id }.filter { it > 0 }.maxOrNull()

/**
 * The oldest (smallest) positive message id in the list, or null.
 * Used as the fromMessageId cursor when paging to older messages.
 */
fun List<Message>.oldestMessageId(): Long? =
    asSequence().map { it.id }.filter { it > 0 }.minOrNull()
