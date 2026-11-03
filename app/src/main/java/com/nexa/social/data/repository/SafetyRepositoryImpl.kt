package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.SafetyLocalDataSource
import com.nexa.social.data.remote.SupabaseSafetyDataSource
import com.nexa.social.domain.model.BlockedUser
import com.nexa.social.domain.model.ContentKind
import com.nexa.social.domain.model.HiddenContent
import com.nexa.social.domain.model.MutedUser
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.model.ReportReason
import com.nexa.social.domain.model.ReportRecord
import com.nexa.social.domain.model.ReportTargetType
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.SafetyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Safety state. Blocks are mirrored to Supabase when an account is signed in and fall back to the
 * on-device copy when offline. Reports are submitted to Supabase for remote accounts and kept locally for offline/local accounts. The UI states this explicitly.
 */
class SafetyRepositoryImpl(
    private val local: SafetyLocalDataSource,
    private val prefs: PreferenceStorage,
    private val remote: SupabaseSafetyDataSource? = null
) : SafetyRepository {

    private val _blocked = MutableStateFlow(local.readBlocked())
    private val _muted = MutableStateFlow(local.readMuted())
    private val _hidden = MutableStateFlow(local.readHidden())
    private val _reports = MutableStateFlow(local.readReports())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { syncBlocksFromRemote() }
    }

    private suspend fun isRemoteSession(): Boolean =
        prefs.session.first() is SessionState.RemoteAccount

    /** Server is the source of truth once signed in; blocks created offline are pushed up first. */
    private suspend fun syncBlocksFromRemote() {
        val remoteStore = remote ?: return
        if (!isRemoteSession()) return
        val remoteList = remoteStore.fetchBlocked() ?: return
        val remoteIds = remoteList.map { it.userId }.toSet()
        val localOnly = _blocked.value.filterNot { it.userId in remoteIds }
        localOnly.forEach { runCatching { remoteStore.block(it.userId) } }
        val merged = (remoteList + localOnly)
            .distinctBy { it.userId }
            .sortedByDescending { it.blockedAt }
        _blocked.value = merged
        local.writeBlocked(merged)
    }

    override fun observeBlockedUsers(): Flow<List<BlockedUser>> =
        _blocked.asStateFlow().map { it.sortedByDescending { u -> u.blockedAt } }

    override fun observeMutedUsers(): Flow<List<MutedUser>> =
        _muted.asStateFlow().map { it.sortedByDescending { u -> u.mutedAt } }

    override fun observeHiddenContent(): Flow<List<HiddenContent>> =
        _hidden.asStateFlow().map { it.sortedByDescending { h -> h.hiddenAt } }

    override fun observePrivacySettings(): Flow<PrivacySettings> = prefs.privacySettings

    override fun observeBlockedIds(): Flow<Set<String>> =
        _blocked.asStateFlow().map { list -> list.map { it.userId }.toSet() }

    override suspend fun block(user: UserSummary): Resource<Unit> {
        if (_blocked.value.any { it.userId == user.id }) return Resource.Success(Unit)
        val remoteStore = remote
        if (remoteStore != null && isRemoteSession() && !remoteStore.block(user.id)) {
            return Resource.Failure(FailureReason.NETWORK)
        }
        _blocked.value = _blocked.value + BlockedUser(
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            blockedAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) { local.writeBlocked(_blocked.value) }
        return Resource.Success(Unit)
    }

    override suspend fun unblock(userId: String): Resource<Unit> {
        val remoteStore = remote
        if (remoteStore != null && isRemoteSession() && !remoteStore.unblock(userId)) {
            return Resource.Failure(FailureReason.NETWORK)
        }
        _blocked.value = _blocked.value.filterNot { it.userId == userId }
        withContext(Dispatchers.IO) { local.writeBlocked(_blocked.value) }
        return Resource.Success(Unit)
    }

    override suspend fun mute(user: UserSummary): Resource<Unit> {
        if (_muted.value.any { it.userId == user.id }) return Resource.Success(Unit)
        _muted.value = _muted.value + MutedUser(
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            mutedAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) { local.writeMuted(_muted.value) }
        return Resource.Success(Unit)
    }

    override suspend fun unmute(userId: String): Resource<Unit> {
        _muted.value = _muted.value.filterNot { it.userId == userId }
        withContext(Dispatchers.IO) { local.writeMuted(_muted.value) }
        return Resource.Success(Unit)
    }

    override suspend fun hide(contentId: String, kind: ContentKind) {
        if (_hidden.value.any { it.contentId == contentId && it.kind == kind }) return
        _hidden.value = _hidden.value + HiddenContent(
            contentId = contentId,
            kind = kind,
            hiddenAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) { local.writeHidden(_hidden.value) }
    }

    override suspend fun unhide(contentId: String) {
        _hidden.value = _hidden.value.filterNot { it.contentId == contentId }
        withContext(Dispatchers.IO) { local.writeHidden(_hidden.value) }
    }

    override suspend fun submitReport(
        targetType: ReportTargetType,
        targetId: String,
        reason: ReportReason,
        note: String
    ): Resource<Unit> {
        if (isRemoteSession()) {
            val remoteStore = remote ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            val submitted = remoteStore.submitReport(targetType, targetId, reason, note)
            return if (submitted) {
                Resource.Success(Unit)
            } else {
                Resource.Failure(FailureReason.NETWORK)
            }
        }
        val record = ReportRecord(
            id = Ids.newId(),
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            note = note.trim(),
            createdAt = System.currentTimeMillis()
        )
        _reports.value = _reports.value + record
        withContext(Dispatchers.IO) { local.writeReports(_reports.value) }
        return Resource.Success(Unit)
    }

    override suspend fun updatePrivacy(settings: PrivacySettings) {
        prefs.setPrivacySettings(settings)
    }
}
