package com.nexa.social.data.repository

import android.content.Context
import android.net.Uri
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.MessageLocalDataSource
import com.nexa.social.data.local.PendingMessage
import com.nexa.social.data.local.PendingMessageAction
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.media.StoredMedia
import com.nexa.social.data.remote.SupabaseMessageDataSource
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.Message
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Message delivery is server-backed for Supabase accounts: messages are written to the
 * `messages` table and every participant polls the conversation while the screen is open.
 * Local accounts keep the device-only behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryImpl(
    private val prefs: PreferenceStorage,
    private val local: MessageLocalDataSource,
    private val userRepository: UserRepository,
    private val mediaStorage: MediaStorage? = null,
    private val appContext: Context? = null,
    private val remote: SupabaseMessageDataSource? = null
) : MessageRepository {

    private val _conversations = MutableStateFlow(local.readConversations())

    private val pendingMessages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val outboxLock = kotlinx.coroutines.sync.Mutex()
    private val actionOutboxLock = kotlinx.coroutines.sync.Mutex()
    private val outbox = MutableStateFlow(local.readOutbox())
    private val actionOutbox = MutableStateFlow(local.readActionOutbox())
    private var lastRemoteUid: String? = null
    init {
        val restored = outbox.value
        if (restored.isNotEmpty()) {
            pendingMessages.value = restored.groupBy { it.conversationId }.mapValues { (_, items) ->
                items.map { item ->
                    Message(
                        id = item.id, conversationId = item.conversationId, senderId = item.senderId,
                        text = item.text, timestamp = item.createdAt, isRead = true,
                        isMine = true, isLocal = true,
                        attachmentPath = item.attachmentPath ?: item.localAttachmentUri,
                        attachmentMime = item.attachmentMime,
                        attachmentName = item.attachmentName
                    )
                }
            }
        }
    }


    private fun isRemote(session: SessionState): Boolean =
        session is SessionState.RemoteAccount && remote != null

    override fun observeConversations(): Flow<List<Conversation>> =
        prefs.session.flatMapLatest { session ->
            if (isRemote(session) && remote != null) {
                val remoteUid = (session as SessionState.RemoteAccount).userId
                if (lastRemoteUid != remoteUid) {
                    lastRemoteUid = remoteUid
                    pendingMessages.value = outbox.value
                        .filter { it.senderId == remoteUid }
                        .groupBy { it.conversationId }
                        .mapValues { (_, items) ->
                            items.map { item ->
                                Message(
                                    id = item.id, conversationId = item.conversationId,
                                    senderId = item.senderId, text = item.text,
                                    timestamp = item.createdAt, isRead = true,
                                    isMine = true, isLocal = true,
                                    attachmentPath = item.attachmentPath ?: item.localAttachmentUri,
                                    attachmentMime = item.attachmentMime,
                                    attachmentName = item.attachmentName
                                )
                            }
                        }
                }
                combine(
                    remote.observeConversations(remoteUid),
                    pendingMessages,
                    prefs.pinnedConversationIds,
                    prefs.mutedConversationIds,
                    prefs.archivedConversationIds
                ) { list, pending, pinned, muted, archived ->
                    list.map { conversation ->
                        val extras = pending[conversation.id].orEmpty()
                        val known = conversation.messages.mapTo(HashSet()) { it.id }
                        val merged = conversation.messages + extras.filterNot { it.id in known }
                        val last = merged.maxByOrNull { it.timestamp }
                        conversation.copy(
                            messages = merged,
                            lastMessagePreview = last?.let { message ->
                                message.text.ifBlank { message.attachmentName.orEmpty() }
                            } ?: conversation.lastMessagePreview,
                            lastTimestamp = maxOf(conversation.lastTimestamp, last?.timestamp ?: 0L),
                            unreadCount = merged.count { !it.isRead && !it.isMine },
                            pinnedLocally = conversation.id in pinned,
                            mutedLocally = conversation.id in muted,
                            archivedLocally = conversation.id in archived
                        )
                    }.sortedWith(
                        compareByDescending<Conversation> { it.pinnedLocally }
                            .thenByDescending { it.lastTimestamp }
                    )
                }
            } else {
                _conversations.asStateFlow().map { list ->
                    list.sortedWith(
                        compareByDescending<Conversation> { it.pinnedLocally }
                            .thenByDescending { it.lastTimestamp }
                    )
                }
            }
        }

    override fun observeConversation(id: String): Flow<Conversation?> =
        observeConversations().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?
    ): Resource<Message> {
        if (text.isBlank() && attachmentUri.isNullOrBlank()) {
            return Resource.Failure(FailureReason.REQUIRED)
        }
        val session = prefs.session.first()
        val senderId = when (session) {
            is SessionState.LocalAccount -> session.userId
            is SessionState.RemoteAccount -> session.userId
            else -> return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val clientMessageId = Ids.newId()
        val storedAttachment = if (!attachmentUri.isNullOrBlank()) {
            when (session) {
                is SessionState.RemoteAccount -> {
                    val storage = mediaStorage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
                    val backup = copyLocalAttachment(attachmentUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
                    when (val upload = storage.uploadAttachment(localUri = backup.path, path = "$senderId/$clientMessageId")) {
                        is Resource.Success -> { deleteLocalAttachment(backup.path); upload.data }
                        is Resource.Failure -> {
                            if (upload.reason != FailureReason.NETWORK) {
                                deleteLocalAttachment(backup.path)
                                return upload
                            }
                            val item = PendingMessage(clientMessageId, conversationId, senderId, text.trim(), System.currentTimeMillis(), localAttachmentUri = backup.path, attachmentMime = backup.mimeType, attachmentName = backup.displayName)
                            enqueue(item)
                            val optimistic = Message(clientMessageId, conversationId, senderId, text.trim(), item.createdAt, true, true, true, backup.path, backup.mimeType, backup.displayName)
                            pendingMessages.update { current -> current + (conversationId to (current[conversationId].orEmpty() + optimistic).takeLast(50)) }
                            return Resource.Success(optimistic)
                        }
                        Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
                    }
                }
                else -> copyLocalAttachment(attachmentUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
            }
        } else null
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val sent = remoteStore.sendMessage(
                conversationId = conversationId,
                senderId = senderId,
                text = text,
                attachmentPath = storedAttachment?.path,
                attachmentMime = storedAttachment?.mimeType,
                attachmentName = storedAttachment?.displayName,
                messageId = clientMessageId
            )
            if (sent == null) {
                val item = PendingMessage(clientMessageId, conversationId, senderId, text.trim(), System.currentTimeMillis(), attachmentPath = storedAttachment?.path, attachmentMime = storedAttachment?.mimeType, attachmentName = storedAttachment?.displayName)
                enqueue(item)
                val optimistic = Message(clientMessageId, conversationId, senderId, text.trim(), item.createdAt, true, true, true, storedAttachment?.path, storedAttachment?.mimeType, storedAttachment?.displayName)
                pendingMessages.update { current -> current + (conversationId to (current[conversationId].orEmpty() + optimistic).takeLast(50)) }
                return Resource.Success(optimistic)
            }
            pendingMessages.update { pending ->
                val current = pending[conversationId].orEmpty()
                pending + (conversationId to (current.filterNot { it.id == sent.id } + sent).takeLast(50))
            }
            return Resource.Success(sent)
        }
        val message = Message(
            id = Ids.newId(),
            conversationId = conversationId,
            senderId = senderId,
            text = text.trim(),
            timestamp = System.currentTimeMillis(),
            isRead = true,
            isMine = true,
            isLocal = true,
            attachmentPath = storedAttachment?.path,
            attachmentMime = storedAttachment?.mimeType,
            attachmentName = storedAttachment?.displayName
        )
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.id != conversationId) conversation
            else conversation.copy(
                messages = conversation.messages + message,
                lastMessagePreview = message.text.ifBlank {
                    message.attachmentName.orEmpty()
                },
                lastTimestamp = message.timestamp
            )
        }
        persist()
        return Resource.Success(message)
    }

    override suspend fun resolveAttachmentUrl(message: Message): String? {
        val path = message.attachmentPath ?: return null
        if (path.contains("://")) return path
        val storage = mediaStorage ?: return null
        return when (val result = storage.signedUrl(MediaBucket.MESSAGES, path)) {
            is Resource.Success -> result.data
            is Resource.Failure -> null
            Resource.Loading -> null
        }
    }

    override suspend fun deleteMessage(conversationId: String, messageId: String): Boolean {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            return runCatching { remoteStore.deleteMessage(messageId) }.getOrDefault(false)
        }
        val conversation = _conversations.value.firstOrNull { it.id == conversationId } ?: return false
        val message = conversation.messages.firstOrNull { it.id == messageId } ?: return false
        if (!message.isMine) return false
        message.attachmentPath?.let { path ->
            if (path.contains("://")) {
                deleteLocalAttachment(path)
            } else {
                mediaStorage?.deleteObject(MediaBucket.MESSAGES, path)
            }
        }
        val remaining = conversation.messages.filterNot { it.id == messageId }
        val last = remaining.maxByOrNull { it.timestamp }
        _conversations.value = _conversations.value.map {
            if (it.id != conversationId) it
            else it.copy(
                messages = remaining,
                lastMessagePreview = last?.text ?: "",
                lastTimestamp = last?.timestamp ?: it.lastTimestamp
            )
        }
        persist()
        return true
    }

    override suspend fun editMessage(messageId: String, text: String): Resource<Unit> {
        if (text.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val updated = runCatching { remoteStore.editMessage(messageId, text) }.getOrDefault(false)
            if (!updated) {
                enqueueAction(session.userId, messageId, "edit", text.trim())
                return Resource.Success(Unit)
            }
            removeQueuedAction(session.userId, messageId, "edit")
            pendingMessages.update { pending ->
                pending.mapValues { (_, list) ->
                    list.map { if (it.id == messageId) it.copy(text = text.trim()) else it }
                }
            }
            return Resource.Success(Unit)
        }
        var found = false
        _conversations.value = _conversations.value.map { conversation ->
            val message = conversation.messages.firstOrNull { it.id == messageId }
            if (message == null || !message.isMine) return@map conversation
            found = true
            conversation.copy(
                messages = conversation.messages.map {
                    if (it.id == messageId) {
                        it.copy(text = text.trim(), editedAt = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            )
        }
        if (!found) return Resource.Failure(FailureReason.PERMISSION_DENIED)
        persist()
        return Resource.Success(Unit)
    }

    override suspend fun deleteMessageForMe(
        conversationId: String,
        messageId: String
    ): Resource<Unit> {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val uid = (session as SessionState.RemoteAccount).userId
            val deleted = runCatching { remoteStore.deleteForMe(messageId, uid) }
                .getOrDefault(false)
            if (!deleted) {
                enqueueAction(uid, messageId, "delete_me", null)
                return Resource.Success(Unit)
            }
            removeQueuedAction(uid, messageId, "delete_me")
            pendingMessages.update { pending ->
                pending.mapValues { (_, list) -> list.filterNot { it.id == messageId } }
            }
            return Resource.Success(Unit)
        }
        removeLocalMessage(conversationId, messageId) ?: return Resource.Failure(FailureReason.GENERIC)
        return Resource.Success(Unit)
    }

    override suspend fun deleteMessageForEveryone(
        conversationId: String,
        messageId: String
    ): Resource<Unit> {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val deleted = runCatching { remoteStore.deleteForEveryone(messageId) }
                .getOrDefault(false)
            if (!deleted) {
                enqueueAction((session as SessionState.RemoteAccount).userId, messageId, "delete_all", null)
                return Resource.Success(Unit)
            }
            removeQueuedAction((session as SessionState.RemoteAccount).userId, messageId, "delete_all")
            pendingMessages.update { pending ->
                pending.mapValues { (_, list) -> list.filterNot { it.id == messageId } }
            }
            return Resource.Success(Unit)
        }
        removeLocalMessage(conversationId, messageId, requireMine = true)
            ?: return Resource.Failure(FailureReason.PERMISSION_DENIED)
        return Resource.Success(Unit)
    }

    override suspend fun reactToMessage(messageId: String, emoji: String): Resource<Unit> {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val uid = (session as SessionState.RemoteAccount).userId
            val reacted = runCatching { remoteStore.reactToMessage(messageId, uid, emoji) }
                .getOrDefault(false)
            if (!reacted) enqueueAction(uid, messageId, "react", emoji) else removeQueuedAction(uid, messageId, "react")
            return Resource.Success(Unit)
        }
        return Resource.Failure(FailureReason.NOT_CONFIGURED)
    }

    override suspend fun unreactToMessage(messageId: String): Resource<Unit> {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val uid = (session as SessionState.RemoteAccount).userId
            val removed = runCatching { remoteStore.unreactToMessage(messageId, uid) }
                .getOrDefault(false)
            if (!removed) enqueueAction(uid, messageId, "unreact", null) else removeQueuedAction(uid, messageId, "unreact")
            return Resource.Success(Unit)
        }
        return Resource.Failure(FailureReason.NOT_CONFIGURED)
    }

    override suspend fun sendStoryReply(
        recipientId: String,
        storyId: String,
        storyPreview: String,
        text: String
    ): Resource<Unit> {
        if (text.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val session = prefs.session.first()
        val senderId = when (session) {
            is SessionState.LocalAccount -> session.userId
            is SessionState.RemoteAccount -> session.userId
            else -> return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val body = buildString {
            if (storyPreview.isNotBlank()) {
                append("↩ ")
                append(storyPreview.trim().take(80))
                append('\n')
            }
            append(text.trim().take(3900))
        }
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val conversationId = remoteStore.startConversation(senderId, recipientId)
                ?: return Resource.Failure(FailureReason.NETWORK)
            val sent = remoteStore.sendMessage(
                conversationId = conversationId,
                senderId = senderId,
                text = body,
                attachmentPath = null,
                attachmentMime = null,
                attachmentName = null,
                replyToStoryId = storyId
            ) ?: return Resource.Failure(FailureReason.NETWORK)
            pendingMessages.update { pending ->
                val current = pending[conversationId].orEmpty()
                pending + (conversationId to (current + sent).takeLast(20))
            }
            return Resource.Success(Unit)
        }
        val conversation = _conversations.value.firstOrNull { it.participant.id == recipientId }
            ?: startConversation(recipientId)
            ?: return Resource.Failure(FailureReason.GENERIC)
        return when (sendMessage(conversation.id, body)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(FailureReason.NETWORK)
            Resource.Loading -> Resource.Loading
        }
    }

    private suspend fun removeLocalMessage(
        conversationId: String,
        messageId: String,
        requireMine: Boolean = false
    ): Message? {
        val conversation = _conversations.value.firstOrNull { it.id == conversationId }
            ?: return null
        val message = conversation.messages.firstOrNull { it.id == messageId } ?: return null
        if (requireMine && !message.isMine) return null
        message.attachmentPath?.let { path ->
            if (path.contains("://")) {
                deleteLocalAttachment(path)
            } else {
                mediaStorage?.deleteObject(MediaBucket.MESSAGES, path)
            }
        }
        val remaining = conversation.messages.filterNot { it.id == messageId }
        val last = remaining.maxByOrNull { it.timestamp }
        _conversations.value = _conversations.value.map {
            if (it.id != conversationId) it
            else it.copy(
                messages = remaining,
                lastMessagePreview = last?.text ?: "",
                lastTimestamp = last?.timestamp ?: it.lastTimestamp
            )
        }
        persist()
        return message
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            prefs.setPinnedConversationIds(toggle(prefs.pinnedConversationIds.first(), id, pinned))
            return
        }
        updateConversation(id) { it.copy(pinnedLocally = pinned) }
    }

    override suspend fun setMuted(id: String, muted: Boolean) {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            prefs.setMutedConversationIds(toggle(prefs.mutedConversationIds.first(), id, muted))
            return
        }
        updateConversation(id) { it.copy(mutedLocally = muted) }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            prefs.setArchivedConversationIds(
                toggle(prefs.archivedConversationIds.first(), id, archived)
            )
            return
        }
        updateConversation(id) { it.copy(archivedLocally = archived) }
    }

    private fun toggle(ids: Set<String>, id: String, enabled: Boolean): Set<String> =
        if (enabled) ids + id else ids - id

    private suspend fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        _conversations.value = _conversations.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    override suspend fun markConversationRead(id: String) {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val uid = (session as SessionState.RemoteAccount).userId
            if (!remoteStore.markRead(id, uid)) enqueueConversationAction(uid, id)
            return
        }
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.id != id) conversation
            else conversation.copy(
                unreadCount = 0,
                messages = conversation.messages.map { it.copy(isRead = true) }
            )
        }
        persist()
    }

    override suspend fun startConversation(participantId: String): Conversation? {
        val session = prefs.session.first()
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            val uid = (session as SessionState.RemoteAccount).userId
            val conversationId = remoteStore.startConversation(uid, participantId) ?: return null
            val participant = userRepository.getUser(participantId)?.let { UserSummary.from(it) }
                ?: return null
            return Conversation(
                id = conversationId,
                participant = participant,
                lastMessagePreview = "",
                lastTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                messages = emptyList(),
                isLocalSample = false
            )
        }
        _conversations.value.firstOrNull { it.participant.id == participantId }?.let { return it }
        val user = userRepository.getUser(participantId) ?: return null
        val conversation = Conversation(
            id = Ids.newId(),
            participant = UserSummary.from(user),
            lastMessagePreview = "",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            messages = emptyList(),
            isLocalSample = false
        )
        _conversations.value = listOf(conversation) + _conversations.value
        persist()
        return conversation
    }

    private val acceptedRequestIds = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun setStarred(messageId: String, starred: Boolean): Resource<Unit> {
        val session = prefs.session.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            val action = if (starred) "star" else "unstar"
            val success = remote.setStarred(messageId, session.userId, starred)
            if (success) removeQueuedAction(session.userId, messageId, action)
            else enqueueAction(session.userId, messageId, action, null)
            remote.refresh()
            return Resource.Success(Unit)
        }
        var found = false
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.messages.none { it.id == messageId }) return@map conversation
            found = true
            conversation.copy(messages = conversation.messages.map { if (it.id == messageId) it.copy(isStarred = starred) else it })
        }
        if (!found) return Resource.Failure(FailureReason.GENERIC)
        persist()
        return Resource.Success(Unit)
    }

    override fun observeStarredMessages(): Flow<List<Message>> =
        observeConversations().map { list ->
            list.flatMap { it.messages }.filter { it.isStarred }.sortedByDescending { it.timestamp }
        }

    override fun observeMessageRequests(): Flow<List<Conversation>> =
        observeConversations().map { list ->
            list.filter { conversation ->
                conversation.id !in acceptedRequestIds.value && conversation.unreadCount > 0
            }
        }

    override suspend fun acceptMessageRequest(conversationId: String): Resource<Unit> {
        acceptedRequestIds.update { it + conversationId }
        return Resource.Success(Unit)
    }

    override suspend fun refresh() {
        remote?.refresh()
        drainOutbox()
    }

    private suspend fun enqueue(item: PendingMessage) {
        outboxLock.lock()
        try {
            val next = outbox.value.filterNot { it.id == item.id } + item
            outbox.value = next
            withContext(Dispatchers.IO) { local.writeOutbox(next) }
        } finally {
            outboxLock.unlock()
        }
    }

    private suspend fun drainOutbox() {
        val store = remote ?: return
        val session = prefs.session.first() as? SessionState.RemoteAccount ?: return
        drainMessageActions(store, session.userId)
        for (item in outbox.value.filter { it.senderId == session.userId }) {
            var queued = item
            val localAttachmentUri = queued.localAttachmentUri
            if (localAttachmentUri != null) {
                val storage = mediaStorage ?: continue
                val uploaded = storage.uploadAttachment(localAttachmentUri, "${session.userId}/${queued.id}")
                if (uploaded !is Resource.Success) continue
                queued = queued.copy(attachmentPath = uploaded.data.path, localAttachmentUri = null, attachmentMime = uploaded.data.mimeType, attachmentName = uploaded.data.displayName)
                outboxLock.lock()
                try {
                    val next = outbox.value.map { if (it.id == queued.id) queued else it }
                    outbox.value = next
                    withContext(Dispatchers.IO) { local.writeOutbox(next) }
                } finally { outboxLock.unlock() }
                deleteLocalAttachment(localAttachmentUri)
            }
            val sent = runCatching {
                store.sendMessage(
                    conversationId = queued.conversationId, senderId = queued.senderId,
                    text = queued.text, attachmentPath = queued.attachmentPath, attachmentMime = queued.attachmentMime,
                    attachmentName = queued.attachmentName, messageId = queued.id
                )
            }.getOrNull() ?: continue
            outboxLock.lock()
            try {
                val next = outbox.value.filterNot { it.id == queued.id }
                outbox.value = next
                withContext(Dispatchers.IO) { local.writeOutbox(next) }
            } finally { outboxLock.unlock() }
            pendingMessages.update { current ->
                val list = current[queued.conversationId].orEmpty()
                current + (queued.conversationId to (list.filterNot { it.id == queued.id } + sent).takeLast(50))
            }
        }
    }

    private suspend fun enqueueConversationAction(userId: String, conversationId: String) {
        actionOutboxLock.lock()
        try {
            val next = actionOutbox.value.filterNot { it.userId == userId && it.conversationId == conversationId && it.action == "read" } +
                PendingMessageAction(Ids.newId(), userId, conversationId, "", "read", null, System.currentTimeMillis())
            actionOutbox.value = next
            withContext(Dispatchers.IO) { local.writeActionOutbox(next) }
        } finally { actionOutboxLock.unlock() }
    }
    private suspend fun enqueueAction(userId: String, messageId: String, action: String, value: String?) {
        actionOutboxLock.lock()
        try {
            val next = actionOutbox.value.filterNot { it.userId == userId && it.messageId == messageId && (it.action == action || action == "delete_all" || ((action in setOf("react", "unreact") && it.action in setOf("react", "unreact")) || (action in setOf("star", "unstar") && it.action in setOf("star", "unstar")) )) } +
                PendingMessageAction(Ids.newId(), userId, "", messageId, action, value, System.currentTimeMillis())
            actionOutbox.value = next
            withContext(Dispatchers.IO) { local.writeActionOutbox(next) }
        } finally { actionOutboxLock.unlock() }
    }

    private suspend fun removeQueuedAction(userId: String, messageId: String, action: String) {
        actionOutboxLock.lock()
        try {
            val next = actionOutbox.value.filterNot { it.userId == userId && it.messageId == messageId && (it.action == action || ((action in setOf("react", "unreact") && it.action in setOf("react", "unreact")) || (action in setOf("star", "unstar") && it.action in setOf("star", "unstar"))) || action == "delete_all") }
            actionOutbox.value = next
            withContext(Dispatchers.IO) { local.writeActionOutbox(next) }
        } finally { actionOutboxLock.unlock() }
    }
    private suspend fun drainMessageActions(store: SupabaseMessageDataSource, userId: String) {
        for (item in actionOutbox.value.filter { it.userId == userId }) {
            val success = when (item.action) {
                "edit" -> store.editMessage(item.messageId, item.value.orEmpty())
                "delete_me" -> store.deleteForMe(item.messageId, userId)
                "delete_all" -> store.deleteForEveryone(item.messageId)
                "react" -> store.reactToMessage(item.messageId, userId, item.value.orEmpty())
                "unreact" -> store.unreactToMessage(item.messageId, userId)
                "star" -> store.setStarred(item.messageId, userId, true)
                "unstar" -> store.setStarred(item.messageId, userId, false)
                "read" -> store.markRead(item.conversationId, userId)
                else -> true
            }
            if (!success) break
            actionOutboxLock.lock()
            try {
                val next = actionOutbox.value.filterNot { it.id == item.id }
                actionOutbox.value = next
                withContext(Dispatchers.IO) { local.writeActionOutbox(next) }
            } finally { actionOutboxLock.unlock() }
        }
    }
    private suspend fun copyLocalAttachment(localUri: String): StoredMedia? {
        val context = appContext ?: return null
        val uri = Uri.parse(localUri)
        if (uri.scheme == "file") {
            val file = File(uri.path.orEmpty())
            return StoredMedia(
                path = localUri,
                publicUrl = null,
                mimeType = context.contentResolver.getType(uri).orEmpty(),
                displayName = file.name,
                sizeBytes = file.length()
            )
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "message_attachments")
                if (!directory.exists() && !directory.mkdirs()) return@withContext null
                val target = File(directory, Ids.newId())
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                input.use { source ->
                    target.outputStream().use { sink -> source.copyTo(sink) }
                }
                StoredMedia(
                    path = Uri.fromFile(target).toString(),
                    publicUrl = null,
                    mimeType = context.contentResolver.getType(uri).orEmpty(),
                    displayName = uri.lastPathSegment ?: target.name,
                    sizeBytes = target.length()
                )
            }
        }.getOrNull()
    }

    private suspend fun deleteLocalAttachment(path: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(path)
                if (uri.scheme == "file") File(uri.path.orEmpty()).delete()
            }
        }
    }

    private suspend fun persist() {
        val snapshot = _conversations.value
        withContext(Dispatchers.IO) { local.writeConversations(snapshot) }
    }
}
