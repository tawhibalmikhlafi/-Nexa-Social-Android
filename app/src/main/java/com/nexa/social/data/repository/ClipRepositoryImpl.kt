package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.data.local.ClipLocalDataSource
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.SupabaseClipDataSource
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.ClipRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ClipRepositoryImpl(
    private val local: ClipLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val achievements: AchievementRepository,
    private val mediaStorage: MediaStorage? = null,
    private val remote: SupabaseClipDataSource? = null
) : ClipRepository {

    private val _clips = MutableStateFlow(local.readClips())

    override fun observeClips(): Flow<List<Clip>> = sessionFlow.flatMapLatest { session ->
        if (session is SessionState.RemoteAccount && remote != null) {
            remote.observeClips()
                .onStart { emit(_clips.value) }
                .onEach { fresh ->
                    _clips.value = fresh
                    persist()
                }
        } else {
            _clips.asStateFlow()
        }
    }.map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeClip(id: String): Flow<Clip?> =
        observeClips().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun addClip(videoUri: String, caption: String): Resource<Clip> {
        if (videoUri.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val session = sessionFlow.first()
        if (session !is SessionState.LocalAccount && session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val user = userRepository.currentUser.first() ?: return Resource.Failure(FailureReason.GENERIC)
        if (session is SessionState.RemoteAccount) {
            val storage = mediaStorage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            val remoteStore = remote ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            val clipId = Ids.newId()
            val path = "${session.userId}/$clipId.mp4"
            val videoUrl = when (val upload = storage.uploadVideo(videoUri, path)) {
                is Resource.Success -> upload.data
                is Resource.Failure -> return upload
                Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
            }
            val created = remoteStore.createClip(UserSummary.from(user), path, videoUrl, caption)
            if (created == null) {
                storage.deleteObject(MediaBucket.CLIPS, path)
                return Resource.Failure(FailureReason.GENERIC)
            }
            achievements.record(CounterKind.CLIPS_CREATED)
            remoteStore.notifyChanged()
            return Resource.Success(created)
        }
        val clip = Clip(
            id = Ids.newId(), author = UserSummary.from(user), videoUri = videoUri,
            caption = caption.trim(), createdAt = System.currentTimeMillis(),
            likeCount = 0, commentCount = 0, likedByMe = false, savedByMe = false, isLocal = true
        )
        _clips.value = listOf(clip) + _clips.value
        persist()
        achievements.record(CounterKind.CLIPS_CREATED)
        return Resource.Success(clip)
    }

    override suspend fun toggleLike(clipId: String) {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            if (remote.toggleLike(clipId)) remote.notifyChanged()
            return
        }
        _clips.value = _clips.value.map { clip ->
            if (clip.id != clipId) clip else {
                val liked = !clip.likedByMe
                clip.copy(likedByMe = liked, likeCount = (clip.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
            }
        }
        persist()
    }

    override suspend fun toggleSave(clipId: String) {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            if (remote.toggleSave(clipId)) remote.notifyChanged()
            return
        }
        _clips.value = _clips.value.map { clip ->
            if (clip.id == clipId) clip.copy(savedByMe = !clip.savedByMe) else clip
        }
        persist()
    }

    override suspend fun deleteClip(clipId: String): Boolean {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            val path = remote.deleteClip(clipId) ?: return false
            mediaStorage?.deleteObject(MediaBucket.CLIPS, path)
            remote.notifyChanged()
            return true
        }
        val currentId = (session as? SessionState.LocalAccount)?.userId ?: return false
        val existing = _clips.value.firstOrNull { it.id == clipId } ?: return false
        if (existing.author.id != currentId) return false
        _clips.value = _clips.value.filterNot { it.id != clipId }
        persist()
        return true
    }

    override suspend fun refresh() {
        remote?.notifyChanged()
    }

    private suspend fun persist() {
        val snapshot = _clips.value
        withContext(Dispatchers.IO) { local.writeClips(snapshot) }
    }
}
