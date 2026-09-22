package com.supergram.app.domain.model

/** Immutable multi-selection state, keeping stable chat-stream order for batch requests. */
data class MessageSelection(val selectedIds: Set<Long> = emptySet(), val active: Boolean = false) {
    val count: Int get() = selectedIds.size
    fun start(id: Long): MessageSelection = MessageSelection(setOf(id), true)
    fun toggle(id: Long): MessageSelection {
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        return MessageSelection(next, active = next.isNotEmpty())
    }
    fun selectAll(messages: List<Message>): MessageSelection =
        MessageSelection(messages.mapTo(linkedSetOf()) { it.id }, messages.isNotEmpty())
    fun clear(): MessageSelection = MessageSelection()
    /** Payload contains only IDs still present in the current loaded chat. */
    fun payload(messages: List<Message>): List<Long> =
        messages.filter { it.id in selectedIds }.map { it.id }
}

/** The first incoming message newer than the inbox read cursor, in oldest-first order. */
fun unreadBoundary(messagesOldestFirst: List<Message>, lastReadInboxMessageId: Long): Long? =
    if (lastReadInboxMessageId < 0L) null else messagesOldestFirst.firstOrNull {
        !it.isOutgoing && it.id > lastReadInboxMessageId
    }?.id
