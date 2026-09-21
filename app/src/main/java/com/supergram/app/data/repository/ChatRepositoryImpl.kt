package com.supergram.app.data.repository

import com.supergram.app.core.telegram.TelegramClientManager
import com.supergram.app.domain.model.Chat
import com.supergram.app.domain.model.MediaFile
import com.supergram.app.domain.model.MediaKind
import com.supergram.app.domain.model.Message
import com.supergram.app.domain.model.SearchFilter
import com.supergram.app.domain.model.SearchPage
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
import java.util.concurrent.ConcurrentHashMap

/**
 * TDLib-backed implementation of [ChatRepository].
 *
 * Observes [TelegramClientManager.updates] and keeps state fresh in real time:
 *  - TdApi.UpdateNewMessage        -> newMessages stream + last message refresh
 *  - TdApi.UpdateChatLastMessage   -> last message / order refresh
 *  - TdApi.UpdateChatReadInbox     -> unread counter refresh
 *  - TdApi.UpdateFile              -> file download progress (photos/documents)
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

    private val _fileUpdates = MutableSharedFlow<MediaFile>(extraBufferCapacity = 256)
    override val fileUpdates: SharedFlow<MediaFile> = _fileUpdates.asSharedFlow()

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
        offset: Int,
    ): Result<List<Message>> = runCatching {
        val request = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            this.fromMessageId = fromMessageId
            this.offset = offset.coerceIn(-99, 0)
            this.limit = limit
            onlyLocal = false
        }
        val result = clientManager.send(request)
        ifErrorThrow(result)
        val messages = (result as TdApi.Messages).messages.orEmpty()
        messages.map { it.toDomainMessage() }
    }

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
    ): Result<Unit> = runCatching {
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            replyToMessageId?.let { id ->
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = id }
            }
            inputMessageContent = TdApi.InputMessageText().apply {
                this.text = TdApi.FormattedText().apply { this.text = text }
            }
        }
        ifErrorThrow(clientManager.send(request))
    }

    override suspend fun forwardMessages(
        fromChatId: Long,
        toChatId: Long,
        messageIds: List<Long>,
    ): Result<Unit> = runCatching {
        if (messageIds.isEmpty()) return@runCatching
        val request = TdApi.ForwardMessages().apply {
            chatId = toChatId
            this.fromChatId = fromChatId
            this.messageIds = messageIds.toLongArray()
            options = TdApi.MessageSendOptions()
            sendCopy = false
            removeCaption = false
        }
        ifErrorThrow(clientManager.send(request))
    }

    override suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<Long>,
        revoke: Boolean,
    ): Result<Unit> = runCatching {
        if (messageIds.isEmpty()) return@runCatching
        val request = TdApi.DeleteMessages().apply {
            this.chatId = chatId
            this.messageIds = messageIds.toLongArray()
            this.revoke = revoke
        }
        ifErrorThrow(clientManager.send(request))
    }

    override suspend fun openChat(chatId: Long): Result<Unit> = runCatching {
        ifErrorThrow(clientManager.send(TdApi.OpenChat(chatId)))
    }

    override suspend fun closeChat(chatId: Long): Result<Unit> = runCatching {
        ifErrorThrow(clientManager.send(TdApi.CloseChat(chatId)))
    }

    override suspend fun viewMessages(chatId: Long, messageIds: List<Long>): Result<Unit> =
        runCatching {
            if (messageIds.isEmpty()) return@runCatching
            val request = TdApi.ViewMessages().apply {
                this.chatId = chatId
                this.messageIds = messageIds.toLongArray()
                source = TdApi.MessageSourceChatHistory()
                forceRead = true
            }
            ifErrorThrow(clientManager.send(request))
        }

    override suspend fun searchChatMessages(
        chatId: Long,
        query: String,
        limit: Int,
        fromMessageId: Long,
        filter: SearchFilter,
    ): Result<SearchPage> = runCatching {
        val tdFilter = filter.toTdFilter()
        val request = TdApi.SearchChatMessages().apply {
            this.chatId = chatId
            this.query = query
            this.limit = limit
            offset = 0
            this.fromMessageId = fromMessageId
            this.filter = tdFilter
        }
        val result = clientManager.send(request)
        ifErrorThrow(result)
        val found = result as TdApi.FoundChatMessages
        val title = resolveChatTitle(chatId)
        SearchPage(
            results = found.messages.orEmpty().map { message ->
                message.toSearchResult(title, resolveSenderName(message.senderId))
            },
            nextOffset = null,
            nextFromMessageId = found.nextFromMessageId.takeIf { it > 0 },
        )
    }

    override suspend fun searchMessages(
        query: String,
        limit: Int,
        offset: String,
        filter: SearchFilter,
    ): Result<SearchPage> = runCatching {
        val tdFilter = filter.toTdFilter()
        val request = TdApi.SearchMessages().apply {
            chatList = TdApi.ChatListMain()
            this.query = query
            this.offset = offset
            this.limit = limit
            this.filter = tdFilter
        }
        val result = clientManager.send(request)
        ifErrorThrow(result)
        val found = result as TdApi.FoundMessages
        val messages = found.messages.orEmpty()
        val titles = messages.map { it.chatId }.distinct()
            .associateWith { resolveChatTitle(it) }
        val senders = messages
            .mapNotNull { it.senderId as? TdApi.MessageSenderUser }
            .map { it.userId }.distinct()
            .associateWith { resolveUserName(it) }
        SearchPage(
            results = messages.map { message ->
                message.toSearchResult(
                    chatTitle = titles[message.chatId] ?: "Chat",
                    senderName = (message.senderId as? TdApi.MessageSenderUser)
                        ?.let { senders[it.userId] },
                )
            },
            nextOffset = found.nextOffset.takeIf { it.isNotBlank() },
            nextFromMessageId = null,
        )
    }

    /** Domain search filter -> TDLib filter (null = no filter). Data layer only. */
private fun SearchFilter.toTdFilter(): TdApi.SearchMessagesFilter? = when (this) {
    SearchFilter.ALL -> null
    SearchFilter.PHOTOS -> TdApi.SearchMessagesFilterPhoto()
    SearchFilter.DOCUMENTS -> TdApi.SearchMessagesFilterDocument()
    SearchFilter.LINKS -> TdApi.SearchMessagesFilterUrl()
}

/** Chat display title via GetChat (fallback to a generic label). */
    private suspend fun resolveChatTitle(chatId: Long): String =
        when (val chat = TelegramClientManager.send(TdApi.GetChat(chatId))) {
            is TdApi.Chat -> chat.title.takeIf { it.isNotBlank() } ?: "Chat"
            else -> "Chat"
        }

    /** User display name via GetUser (null when the sender is not a user). */
    private suspend fun resolveUserName(userId: Long): String? =
        when (val user = TelegramClientManager.send(TdApi.GetUser(userId))) {
            is TdApi.User -> listOfNotNull(
                user.firstName?.takeIf { it.isNotBlank() },
                user.lastName?.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { null }
            else -> null
        }

    /** Sender display name for a search result. */
    private suspend fun resolveSenderName(senderId: TdApi.MessageSender): String? =
        when (senderId) {
            is TdApi.MessageSenderUser -> resolveUserName(senderId.userId)
            else -> null
        }

    override suspend fun downloadFile(fileId: Int): Result<Unit> = runCatching {
        val request = TdApi.DownloadFile().apply {
            this.fileId = fileId
            priority = 32 // 1..32, foreground priority
            offset = 0
            limit = 0 // 0 = whole file
            synchronous = false
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
                    is TdApi.UpdateFile -> onFileUpdate(update.file)
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

    private fun onFileUpdate(file: TdApi.File) {
        val descriptor = fileDescriptors[file.id]
        val state = file.toDomainMediaFile(
            kind = descriptor?.kind ?: MediaKind.PHOTO,
            fileName = descriptor?.fileName,
        )
        fileDescriptors[file.id] = state
        scope.launch { _fileUpdates.emit(state) }
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

/**
 * Maps a TDLib chat to the domain [Chat], resolving the category:
 * private/secret chats are checked against GetUser to detect bots.
 */
suspend fun TdApi.Chat.toDomainChat(): Chat {
    val privateType = type as? TdApi.ChatTypePrivate
    val category = ChatCategoryResolver.resolve(
        chatType = type,
        isBot = privateType != null && isBotUser(privateType.userId),
    )
    return Chat(
        id = id,
        title = title.orEmpty(),
        category = category,
        lastMessageSnippet = lastMessage?.content?.toSnippet(),
        lastMessageDate = lastMessage?.date?.toLong(),
        unreadCount = unreadCount,
        order = positions.orEmpty()
            .firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L,
    )
}

/** True when the Telegram user behind a private chat is a bot account. */
private suspend fun isBotUser(userId: Long): Boolean =
    when (val user = TelegramClientManager.send(TdApi.GetUser(userId))) {
        is TdApi.User -> user.type is TdApi.UserTypeBot
        else -> false
    }

/** Maps a TDLib message to the domain [Message]. */
fun TdApi.Message.toDomainMessage(): Message = Message(
    id = id,
    chatId = chatId,
    senderUserId = (senderId as? TdApi.MessageSenderUser)?.userId,
    text = content.toSnippet(),
    isOutgoing = isOutgoing,
    date = date.toLong(),
    media = content.toDomainMediaFile(),
)

/** Extracts the download state of a photo/document/voice attachment, if any. */
fun TdApi.MessageContent.toDomainMediaFile(): MediaFile? = when (this) {
    is TdApi.MessagePhoto -> {
        val size = photo.sizes.orEmpty().maxByOrNull { it.width }
        size?.photo?.toDomainMediaFile(MediaKind.PHOTO, null)?.also {
            fileDescriptors[it.fileId] = it
        }
    }
    is TdApi.MessageDocument -> {
        document.document
            .toDomainMediaFile(MediaKind.DOCUMENT, document.fileName)
            ?.also { fileDescriptors[it.fileId] = it }
    }
    is TdApi.MessageVoiceNote -> {
        voiceNote.voice
            .toDomainMediaFile(MediaKind.VOICE, null)
            ?.copy(
                durationSeconds = voiceNote.duration,
                waveform = decodeWaveform(voiceNote.waveform),
            )
            ?.also { fileDescriptors[it.fileId] = it }
    }
    else -> null
}

/** Maps a TDLib file to the domain [MediaFile] download state. */
fun TdApi.File.toDomainMediaFile(kind: MediaKind, fileName: String?): MediaFile = MediaFile(
    kind = kind,
    fileId = id,
    fileName = fileName,
    expectedSize = if (expectedSize > 0) expectedSize else size,
    downloadedSize = local?.downloadedSize ?: 0,
    isDownloadingActive = local?.isDownloadingActive ?: false,
    isDownloaded = local?.isDownloadingCompleted ?: false,
    localPath = local?.path?.takeIf { it.isNotEmpty() },
)

// Process-wide descriptor cache (kind, name, last state) per TDLib file id.
private val fileDescriptors = ConcurrentHashMap<Int, MediaFile>()

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
