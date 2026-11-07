package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.common.Validators
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.AccountLocalDataSource
import com.nexa.social.data.local.LocalAccountRecord
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.UserRemoteDataSource
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryImpl(
    private val prefs: PreferenceStorage,
    private val accounts: AccountLocalDataSource,
    private val remote: UserRemoteDataSource? = null,
    private val storage: MediaStorage? = null
) : UserRepository {

    private fun allUsers(records: List<LocalAccountRecord>): List<User> =
        SampleData.users + records.map { it.user }

    /**
     * Immediately reflects a profile the user just saved, before the remote poll catches up.
     * The override is dropped as soon as the source flow emits the same editable values.
     */
    private val profileOverrides = MutableStateFlow<Map<String, User>>(emptyMap())

    private val _uploadProgress = MutableStateFlow(0)

    override val uploadProgress: Flow<Int> = _uploadProgress.asStateFlow()

    override val currentUser: Flow<User?> = prefs.session.flatMapLatest { session ->
        val userId = when (session) {
            is SessionState.RemoteAccount -> session.userId
            is SessionState.LocalAccount -> session.userId
            else -> null
        }
        if (userId == null) {
            flowOf(null)
        } else {
            val base: Flow<User?> = when (session) {
                is SessionState.RemoteAccount ->
                    remote?.observeProfile(userId) ?: flowOf(null)
                is SessionState.LocalAccount -> accounts.records.map { records ->
                    records.firstOrNull { it.user.id == userId }?.user
                }
                else -> flowOf(null)
            }
            withProfileOverride(userId, base)
        }
    }

    override fun observeUser(userId: String): Flow<User?> = accounts.records.flatMapLatest { records ->
        val local = allUsers(records).firstOrNull { it.id == userId }
        val base = if (local != null) flowOf(local) else remote?.observeProfile(userId) ?: flowOf(null)
        withProfileOverride(userId, base)
    }

    private fun withProfileOverride(userId: String, base: Flow<User?>): Flow<User?> =
        base
            .onEach { latest ->
                val pending = profileOverrides.value[userId]
                if (pending != null && latest != null && sameEditableProfile(latest, pending)) {
                    profileOverrides.update { it - userId }
                }
            }
            .combine(profileOverrides) { latest, overrides ->
                overrides[userId] ?: latest
            }

    override suspend fun getUser(userId: String): User? {
        allUsers(accounts.records.value).firstOrNull { it.id == userId }?.let { return it }
        return remote?.getProfile(userId)
    }

    override fun observeAllUsers(): Flow<List<User>> = prefs.session.flatMapLatest { session ->
        if (session is SessionState.RemoteAccount && remote != null) {
            // Remote mode must never present bundled sample accounts as real users.
            remote.observeUsers()
        } else {
            accounts.records.map { allUsers(it) }
        }
    }

    override suspend fun searchUsers(query: String): List<User> {
        val canonical = Validators.canonicalUsername(query).trim()
        if (canonical.isBlank()) return emptyList()
        if (prefs.session.first() is SessionState.RemoteAccount) {
            return remote?.searchUsers(query).orEmpty()
        }
        val local = allUsers(accounts.records.value).filter { user ->
            user.username.startsWith(canonical, ignoreCase = true) ||
                user.displayName.startsWith(canonical, ignoreCase = true)
        }
        return local.distinctBy { it.id }
    }

    override suspend fun usernameTakenLocally(username: String): Boolean {
        if (accounts.isUsernameTaken(username)) return true
        return remote?.isUsernameAvailable(username) == false
    }

    override suspend fun updateProfile(
        userId: String,
        displayName: String,
        bio: String,
        avatarUri: String?,
        coverUri: String?,
        city: String?,
        website: String?,
        username: String?,
        placeOfOrigin: String?,
        currentResidence: String?,
        birthDate: String?,
        skills: List<String>,
        hobbies: List<String>
    ): Resource<User> {
        val remoteStore = remote
        val activeSession = prefs.session.first()
        if (remoteStore != null && activeSession is SessionState.RemoteAccount &&
            userId == activeSession.userId
        ) {
            try {
            val requestedUsername = username?.let(Validators::canonicalUsername)
            val existingProfile = runCatching { remoteStore.getProfile(userId, includePrivate = true) }.getOrNull()
            if (!requestedUsername.isNullOrBlank() && existingProfile?.username != requestedUsername) {
                when (val result = remoteStore.changeUsername(userId, requestedUsername)) {
                    is Resource.Success -> profileOverrides.update { it + (userId to result.data) }
                    is Resource.Failure -> return result
                    Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
                }
            }
            val avatar = when (val result = uploadProfileImage(
                MediaBucket.PROFILES,
                "$userId/avatar-${Ids.newId()}",
                avatarUri,
                onProgress = { progress ->
                    _uploadProgress.value = (progress * 50f).toInt().coerceIn(0, 50)
                }
            )) {
                is Resource.Success -> result.data
                is Resource.Failure -> return result
                Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
            }
            val cover = when (val result = uploadProfileImage(
                MediaBucket.PROFILES,
                "$userId/cover-${Ids.newId()}",
                coverUri,
                onProgress = { progress ->
                    _uploadProgress.value = (50 + progress * 50f).toInt().coerceIn(50, 100)
                }
            )) {
                is Resource.Success -> result.data
                is Resource.Failure -> return result
                Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
            }
            val previous = runCatching { remoteStore.getProfile(userId) }.getOrNull()
            val updated = when (val result = remoteStore.updateProfile(
                uid = userId,
                displayName = displayName.trim().ifBlank { "Nexa" },
                bio = bio.trim(),
                avatarUri = avatar,
                coverUri = cover,
                city = city?.trim()?.ifBlank { null },
                website = website?.trim()?.ifBlank { null },
                placeOfOrigin = placeOfOrigin?.trim()?.ifBlank { null },
                currentResidence = currentResidence?.trim()?.ifBlank { null },
                birthDate = birthDate?.takeIf { it.isNotBlank() },
                skills = skills.map(String::trim).filter(String::isNotBlank).distinct(),
                hobbies = hobbies.map(String::trim).filter(String::isNotBlank).distinct()
            )) {
                is Resource.Success -> result.data
                is Resource.Failure -> return result
                Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
            }
            if (previous?.avatarUri != updated.avatarUri) {
                storage?.deleteByPublicUrl(previous?.avatarUri)
            }
            if (previous?.coverUri != updated.coverUri) {
                storage?.deleteByPublicUrl(previous?.coverUri)
            }
            profileOverrides.update { it + (userId to updated) }
            return Resource.Success(updated)
            } finally {
                _uploadProgress.value = 0
            }
        }
        if (activeSession is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.NOT_CONFIGURED)
        }
        val existing = accounts.getById(userId)?.user
            ?: return Resource.Failure(FailureReason.GENERIC)
        val updated = existing.copy(
            displayName = displayName.trim().ifBlank { existing.displayName },
            bio = bio.trim(),
            avatarUri = avatarUri,
            coverUri = coverUri,
            city = city?.trim()?.ifBlank { null } ?: existing.city,
            website = website?.trim()?.ifBlank { null } ?: existing.website,
            placeOfOrigin = placeOfOrigin?.trim()?.ifBlank { null },
            currentResidence = currentResidence?.trim()?.ifBlank { null },
            birthDate = birthDate?.takeIf { it.isNotBlank() },
            skills = skills.map(String::trim).filter(String::isNotBlank).distinct(),
            hobbies = hobbies.map(String::trim).filter(String::isNotBlank).distinct(),
            username = username?.let(Validators::canonicalUsername)?.takeIf { it.isNotBlank() } ?: existing.username
        )
        accounts.updateUser(updated)
        profileOverrides.update { it + (userId to updated) }
        return Resource.Success(updated)
    }

    override fun observeFollowingIds(): Flow<Set<String>> = prefs.session.flatMapLatest { session ->
        if (session is SessionState.RemoteAccount && remote != null) {
            combine(remote.observeFollowingIds(session.userId), prefs.followingIds) { remoteIds, localIds ->
                remoteIds + localIds
            }
        } else {
            prefs.followingIds
        }
    }

    override fun observeFollowing(): Flow<List<User>> = prefs.session.flatMapLatest { session ->
        val localFlow = combine(prefs.followingIds, accounts.records) { ids, records ->
            allUsers(records).filter { it.id in ids }
        }
        if (session is SessionState.RemoteAccount && remote != null) {
            combine(remote.observeFollowing(session.userId), localFlow) { remoteUsers, localUsers ->
                (remoteUsers + localUsers).distinctBy { it.id }
            }
        } else {
            localFlow
        }
    }

    override fun observeFollowers(): Flow<List<User>> = prefs.session.flatMapLatest { session ->
        if (session is SessionState.RemoteAccount && remote != null) {
            remote.observeFollowers(session.userId)
        } else {
            flowOf(emptyList())
        }
    }

    override fun observeFollowersOf(userId: String): Flow<List<User>> =
        prefs.session.flatMapLatest { session ->
            if (session is SessionState.RemoteAccount && remote != null && !isLocalTarget(userId)) {
                remote.observeFollowers(userId)
            } else {
                flowOf(emptyList())
            }
        }

    override fun observeFollowingOf(userId: String): Flow<List<User>> =
        prefs.session.flatMapLatest { session ->
            if (session is SessionState.RemoteAccount && remote != null && !isLocalTarget(userId)) {
                remote.observeFollowing(userId)
            } else if (session is SessionState.LocalAccount && session.userId == userId) {
                observeFollowing()
            } else {
                flowOf(emptyList())
            }
        }

    private fun isLocalTarget(userId: String): Boolean =
        allUsers(accounts.records.value).any { it.id == userId }

    override suspend fun removeFollower(followerId: String): Boolean {
        val session = prefs.session.first()
        val remoteStore = remote
        if (session !is SessionState.RemoteAccount || remoteStore == null ||
            followerId.isBlank()
        ) return false
        return runCatching {
            remoteStore.removeFollower(session.userId, followerId)
        }.getOrDefault(false)
    }

    override suspend fun setFollowing(userId: String, following: Boolean): Boolean {
        val session = prefs.session.first()
        val remoteStore = remote
        val localTarget = allUsers(accounts.records.value).any { it.id == userId }
        if (!localTarget) {
            if (session is SessionState.RemoteAccount && remoteStore != null) {
                return runCatching {
                    remoteStore.setFollowing(session.userId, userId, following)
                }.getOrDefault(false)
            }
            return false
        }
        val current = prefs.followingIds.first()
        val updated = if (following) current + userId else current - userId
        prefs.setFollowingIds(updated)
        return true
    }

    override suspend fun hasPendingFollowRequest(userId: String): Boolean? {
        val session = prefs.session.first()
        val remoteStore = remote
        if (session !is SessionState.RemoteAccount || remoteStore == null || isLocalTarget(userId)) {
            return false
        }
        return runCatching {
            remoteStore.hasPendingFollowRequest(session.userId, userId)
        }.getOrNull()
    }

    override suspend fun loadIncomingFollowRequests(): List<com.nexa.social.domain.model.FollowRequest>? {
        val session = prefs.session.first()
        val remoteStore = remote
        if (session !is SessionState.RemoteAccount || remoteStore == null) return emptyList()
        return runCatching { remoteStore.loadIncomingFollowRequests(session.userId) }.getOrNull()
    }

    override suspend fun respondToFollowRequest(requestId: String, approve: Boolean): Boolean {
        val session = prefs.session.first()
        val remoteStore = remote
        if (session !is SessionState.RemoteAccount || remoteStore == null) return false
        return runCatching {
            remoteStore.respondToFollowRequest(requestId, approve)
        }.getOrDefault(false)
    }

    override suspend fun isFollowing(userId: String): Boolean {
        val session = prefs.session.first()
        val remoteStore = remote
        val localTarget = allUsers(accounts.records.value).any { it.id == userId }
        if (!localTarget) {
            if (session is SessionState.RemoteAccount && remoteStore != null) {
                return runCatching {
                    remoteStore.isFollowing(session.userId, userId)
                }.getOrDefault(false)
            }
            return false
        }
        return userId in prefs.followingIds.first()
    }

    override suspend fun refresh() {
        remote?.refresh()
    }

    private suspend fun uploadProfileImage(
        bucket: MediaBucket,
        path: String,
        uri: String?,
        onProgress: (Float) -> Unit = {}
    ): Resource<String?> {
        if (uri.isNullOrBlank()) return Resource.Success(null)
        if (uri.startsWith("http")) return Resource.Success(uri)
        val storageStore = storage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        return when (val result = storageStore.uploadImage(uri, bucket, path, onProgress)) {
            is Resource.Success -> Resource.Success(result.data)
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    private fun sameEditableProfile(first: User, second: User): Boolean =
        first.displayName == second.displayName &&
            first.bio == second.bio &&
            first.avatarUri == second.avatarUri &&
            first.coverUri == second.coverUri &&
            first.city == second.city &&
            first.website == second.website &&
            first.placeOfOrigin == second.placeOfOrigin &&
            first.currentResidence == second.currentResidence &&
            first.birthDate == second.birthDate &&
            first.skills == second.skills &&
            first.hobbies == second.hobbies
}
