package com.supergram.app.domain.model

/**
 * Domain representation of a Telegram chat (private chat, group or channel).
 */
data class Chat(
    val id: Long,
    val title: String,
    /** Short preview of the newest message, for the chat list row. */
    val lastMessageSnippet: String?,
    /** Unix seconds of the newest message. */
    val lastMessageDate: Long?,
    /** Number of unread messages in this chat. */
    val unreadCount: Int,
    /** TDLib main-list sort order (bigger = higher in the list). */
    val order: Long,
)
