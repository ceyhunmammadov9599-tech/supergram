package com.supergram.app.domain.model

/** Chat categorization for the chat list filter tabs. */
enum class ChatCategory {
    DIRECT,
    GROUP,
    CHANNEL,
    BOT,
}

/**
 * Domain representation of a Telegram chat (private chat, group or channel).
 */
data class Chat(
    val id: Long,
    val title: String,
    /** Category used by the chat list filter tabs. */
    val category: ChatCategory,
    /** Short preview of the newest message, for the chat list row. */
    val lastMessageSnippet: String?,
    /** Unix seconds of the newest message. */
    val lastMessageDate: Long?,
    /** Number of unread messages in this chat. */
    val unreadCount: Int,
    /** TDLib main-list sort order (bigger = higher in the list). */
    val order: Long,
)
