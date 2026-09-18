package com.supergram.app.domain.model

/**
 * Domain representation of a single Telegram message.
 */
data class Message(
    val id: Long,
    val chatId: Long,
    /** Sender user id, null when the sender is a chat/channel itself. */
    val senderUserId: Long?,
    val text: String,
    /** True when this message was sent by the logged-in account. */
    val isOutgoing: Boolean,
    /** Unix seconds. */
    val date: Long,
    /** Photo or document attachment with its download state, if any. */
    val media: MediaFile?,
)
