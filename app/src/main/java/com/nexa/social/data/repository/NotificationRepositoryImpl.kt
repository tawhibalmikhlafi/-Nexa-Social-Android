package com.nexa.social.data.repository

import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.NotificationLocalDataSource
import com.nexa.social.data.remote.SupabaseNotificationDataSource
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.model.NotificationTargetKind
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hybrid notifications store.
 *
 * - A Supabase session reads the rows written by the server-side triggers.
 * - Local/guest sessions keep the device-only list, which is also used as the sample data set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryImpl(
    private val local: NotificationLocalDataSource,
    private val prefs: PreferenceStorage,
    private val remote: SupabaseNotificationDataSource? = null
) : NotificationRepository {

    private val _notifications = MutableStateFlow(sanitize(local.readNotifications()))
    private val _remote = MutableStateFlow<List<NexaNotification>>(emptyList())

    override fun observeNotifications(): Flow<List<NexaNotification>> =
        prefs.session.flatMapLatest { session ->
            if (session is SessionState.RemoteAccount && remote != null) {
                _remote.value = emptyList()
                channelFlow {
                    launch {
                        remote.observeNotifications(session.userId).collect { rows ->
                            _remote.value = rows
                        }
                    }
                    _remote.collect { send(it) }
                }
            } else {
                _notifications.asStateFlow().map { list ->
                    sanitize(list).sortedByDescending { it.timestamp }
                }
            }
        }

    override fun observeUnreadCount(): Flow<Int> =
        observeNotifications().map { list -> list.count { !it.isRead } }

    override fun observeIsRemote(): Flow<Boolean> =
        prefs.session.map { it is SessionState.RemoteAccount && remote != null }

    override suspend fun markRead(id: String) {
        if (useRemote()) {
            remote?.markRead(id)
            _remote.value = _remote.value.map { if (it.id == id) it.copy(isRead = true) else it }
            return
        }
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        persist()
    }

    override suspend fun markAllRead() {
        if (useRemote()) {
            val uid = (prefs.session.first() as? SessionState.RemoteAccount)?.userId ?: return
            remote?.markAllRead(uid)
            _remote.value = _remote.value.map { it.copy(isRead = true) }
            return
        }
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        persist()
    }

    override suspend fun delete(id: String) {
        if (useRemote()) {
            remote?.delete(id)
            _remote.value = _remote.value.filterNot { it.id == id }
            return
        }
        _notifications.value = _notifications.value.filterNot { it.id == id }
        persist()
    }

    override suspend fun clearAll() {
        if (useRemote()) {
            val uid = (prefs.session.first() as? SessionState.RemoteAccount)?.userId ?: return
            remote?.deleteAll(uid)
            _remote.value = emptyList()
            return
        }
        _notifications.value = emptyList()
        persist()
    }

    override fun observeTypeEnabled(): Flow<Map<NotificationType, Boolean>> = prefs.notificationTypeEnabled

    override suspend fun setTypeEnabled(type: NotificationType, enabled: Boolean) {
        prefs.setNotificationTypeEnabled(type, enabled)
    }

    override suspend fun refresh() {
        if (useRemote()) remote?.refresh()
    }

    private suspend fun useRemote(): Boolean =
        remote != null && prefs.session.first() is SessionState.RemoteAccount

    private suspend fun persist() {
        val snapshot = _notifications.value
        withContext(Dispatchers.IO) { local.writeNotifications(snapshot) }
    }

    /**
     * Gson bypasses constructors, so fields added to the model after a file was first written are
     * left null even though they are declared non-null. Rebuilding each entry with safe fallbacks
     * keeps later `copy(...)` calls (markRead/markAllRead) from throwing a NullPointerException.
     * Entries without an id/type and duplicate ids are dropped as well.
     */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitize(list: List<NexaNotification>): List<NexaNotification> =
        list.asSequence()
            .filter { it.id != null && it.id.isNotBlank() && it.type != null }
            .distinctBy { it.id }
            .map { notification ->
                NexaNotification(
                    id = notification.id,
                    type = notification.type,
                    title = notification.title ?: "",
                    body = notification.body ?: "",
                    timestamp = notification.timestamp,
                    isRead = notification.isRead,
                    actorName = notification.actorName,
                    actorAvatarUri = notification.actorAvatarUri,
                    targetKind = notification.targetKind ?: NotificationTargetKind.NONE,
                    targetId = notification.targetId
                )
            }
            .toList()
}
