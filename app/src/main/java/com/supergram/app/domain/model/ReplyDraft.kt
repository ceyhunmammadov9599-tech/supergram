package com.supergram.app.domain.model

/**
 * Pure encapsulation of the in-progress reply state: which message is being
 * replied to and how the dismissible preview bar should render it.
 */
data class ReplyDraft(
    val messageId: Long,
    val snippet: String,
    val isOutgoing: Boolean,
) {
    /** Title for the preview bar: own message vs. a reply to someone. */
    val title: String
        get() = if (isOutgoing) "You" else "Reply"

    companion object {
        /** Builds a draft from a domain message, trimming long snippets. */
        fun of(message: Message): ReplyDraft = ReplyDraft(
            messageId = message.id,
            snippet = message.text.ifBlank {
                message.media?.let { media ->
                    when (media.kind) {
                        MediaKind.PHOTO -> "Photo"
                        MediaKind.DOCUMENT -> "Document"
                        MediaKind.VOICE -> "Voice message"
                    }
                } ?: ""
            }.trim().take(64),
            isOutgoing = message.isOutgoing,
        )
    }
}
