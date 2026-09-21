package com.supergram.app.domain.model

/**
 * Pure state flow of the delete-confirmation dialog:
 * Hidden -> Confirming(message, revoke) -> (confirm -> DeleteRequest | dismiss -> Hidden).
 */
data class DeleteDialogState(
    val messageId: Long? = null,
    val revoke: Boolean = false,
    val canRevoke: Boolean = false,
    val visible: Boolean = false,
) {
    /** Immutable transition: open the dialog for a message. */
    fun shownFor(messageId: Long, canRevoke: Boolean): DeleteDialogState =
        if (this.messageId == messageId && this.canRevoke == canRevoke) {
            copy(visible = true)
        } else {
            DeleteDialogState(
                messageId = messageId,
                canRevoke = canRevoke,
                revoke = false,
                visible = true,
            )
        }

    /** Immutable transition: flip the "delete for everyone" checkbox. */
    fun revokeToggled(): DeleteDialogState =
        if (visible && canRevoke) copy(revoke = !revoke) else this

    /** Immutable transition: confirm -> a dispatchable delete request. */
    fun confirmed(): DeleteRequest? = messageId?.takeIf { visible }?.let {
        DeleteRequest(messageId = it, revoke = revoke && canRevoke)
    }

    /** Immutable transition: dismiss back to the hidden state. */
    fun dismissed(): DeleteDialogState = copy(visible = false)

    companion object {
        val HIDDEN = DeleteDialogState()
    }
}

/** A confirmed deletion to dispatch (message + both-sides revoke flag). */
data class DeleteRequest(
    val messageId: Long,
    val revoke: Boolean,
)
