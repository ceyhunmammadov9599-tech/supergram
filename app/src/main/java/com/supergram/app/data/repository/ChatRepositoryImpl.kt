package com.supergram.app.data.repository

import com.supergram.app.core.telegram.TelegramClientManager
import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.Message
import com.supergram.app.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * TDLib-backed implementation of [ChatRepository].
 *
 * In addition to the request/response methods, it observes
 * [TelegramClientManager.updates] and keeps the chat list fresh in real time:
 *  - TdApi.UpdateNewMessage        -> newMessages stream + last message refresh
 *  - TdApi.UpdateChatLastMessage   -> last message / order refresh
 *  - TdApi.UpdateChatReadInbox     -> unread counter refresh
 *
 * All TDLib types stay inside the data layer.
 */
class ChatRepositoryImpl(
    private val clientManager: TelegramClientManager,
) : ChatRepository {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("ChatRepository")
    )

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    override val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _newMessages = MutableSharedFlow<Message>(extraBufferCapacity = 128)
    override val newMessages: SharedFlow<Message> = _newMessages.asSharedFlow()

    init {
        observeTdlibUpdates()
    }

    override suspend fun loadChats(limit: Int): Result<Unit> = runCatching {
        val request = TdApi.GetChats().apply {
            chatList = TdApi.ChatListMain()
            this.limit = limit
        }
        val result = clientManager.send(request)
        ifErrorThrow(result)
        val chatIds = (result as TdApi.Chats).chatIds

        val loaded = mutableListOf<Chat>()
        for (id in chatIds) {
            when (val chatResult = clientManager.send(TdApi.GetChat(id))) {
                is TdApi.Chat -> loaded.add(chatResult.toDomainChat())
                else -> Unit
            }
        }

        _chats.value = loaded.sortedByDescending { it.order }
    }

    override suspend fun loadHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
    ): Result<List<Message>> = runCatching {
        val request = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            this.fromMessageId = fromMessageId
            offset = 0
            this.limit = limit
            onlyLocal = false
        }
        val result = clientManager.send(request)
        ifErrorThrow(result)
        val messages = (result as TdApi.Messages).messages.orEmpty()
        messages.map { it.toDomainMessage() }
    }

    override suspend fun sendMessage(chatId: Long, text: String): Result<Unit> = runCatching {
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageText().apply {
                this.text = TdApi.FormattedText().apply { this.text = text }
            }
        }
        ifErrorThrow(clientManager.send(request))
    }

    fun dispose() {
        scope.cancel()
    }

    // ---------------------------------------------------------------- private

    private fun observeTdlibUpdates() {
        scope.launch {
            clientManager.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateNewMessage -> onNewMessage(update.message)
                    is TdApi.UpdateChatLastMessage -> onLastMessage(
                        update.chatId,
                        update.lastMessage,
                        update.positions,
                    )
                    is TdApi.UpdateChatReadInbox -> onUnreadCount(update.chatId, update.unreadCount)
                    else -> Unit
                }
            }
        }
    }

    private fun onNewMessage(message: TdApi.Message) {
        val domain = message.toDomainMessage()
        scope.launch { _newMessages.emit(domain) }
        // Keep the chat list fresh: refresh last message and unread counters.
        if (!message.isOutgoing) {
            updateChat(message.chatId) { it.copy(unreadCount = it.unreadCount + 1) }
        }
        updateChatLastMessage(message)
    }

    private fun onLastMessage(
        chatId: Long,
        lastMessage: TdApi.Message?,
        positions: Array<TdApi.ChatPosition>,
    ) {
        if (lastMessage != null) {
            updateChatLastMessage(lastMessage)
        }
        val mainOrder = positions.orEmpty()
            .firstOrNull { it.list is TdApi.ChatListMain }?.order
        if (mainOrder != null) {
            updateChat(chatId) { it.copy(order = mainOrder) }
            resort()
        }
    }

    private fun onUnreadCount(chatId: Long, unreadCount: Int) {
        updateChat(chatId) { it.copy(unreadCount = unreadCount) }
    }

    private fun updateChatLastMessage(message: TdApi.Message) {
        updateChat(message.chatId) {
            it.copy(
                lastMessageSnippet = message.content.toSnippet(),
                lastMessageDate = message.date.toLong(),
            )
        }
    }

    private inline fun updateChat(chatId: Long, transform: (Chat) -> Chat) {
        val current = _chats.value
        val index = current.indexOfFirst { it.id == chatId }
        if (index >= 0) {
            val updated = current.toMutableList()
            updated[index] = transform(updated[index])
            _chats.value = updated
        }
    }

    private fun resort() {
        _chats.value = _chats.value.sortedByDescending { it.order }
    }
}

/** Throws when a TDLib response is an error object. */
private fun ifErrorThrow(result: TdApi.Object) {
    if (result is TdApi.Error) {
        throw TelegramAuthException(result.code, result.message)
    }
}

// ------------------------------------------------------------------ mappers

/** Maps a TDLib chat to the domain [Chat]. */
fun TdApi.Chat.toDomainChat(): Chat = Chat(
    id = id,
    title = title.orEmpty(),
    lastMessageSnippet = lastMessage?.content?.toSnippet(),
    lastMessageDate = lastMessage?.date?.toLong(),
    unreadCount = unreadCount,
    order = positions.orEmpty()
        .firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L,
)

/** Maps a TDLib message to the domain [Message]. */
fun TdApi.Message.toDomainMessage(): Message = Message(
    id = id,
    chatId = chatId,
    senderUserId = (senderId as? TdApi.MessageSenderUser)?.userId,
    text = content.toSnippet(),
    isOutgoing = isOutgoing,
    date = date.toLong(),
)

/** Best-effort plain-text preview for any TDLib message content. */
fun TdApi.MessageContent.toSnippet(): String = when (this) {
    is TdApi.MessageText -> text?.text.orEmpty()
    is TdApi.MessagePhoto -> "🖼 Photo" + (caption?.text?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: "")
    is TdApi.MessageVideo -> "📹 Video"
    is TdApi.MessageVoiceNote -> "🎤 Voice message"
    is TdApi.MessageAudio -> "🎵 Audio"
    is TdApi.MessageDocument -> "📄 Document"
    is TdApi.MessageSticker -> "Sticker"
    is TdApi.MessageLocation -> "📍 Location"
    is TdApi.MessageContact -> "👤 Contact"
    is TdApi.MessagePoll -> "📊 Poll"
    is TdApi.MessageCall -> "📞 Call"
    else -> "Message"
}
