package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.data.local.ClipLocalDataSource
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.ClipRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ClipRepositoryImpl(
    private val local: ClipLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val achievements: AchievementRepository
) : ClipRepository {

    private val _clips = MutableStateFlow(local.readClips())

    override fun observeClips(): Flow<List<Clip>> =
        _clips.asStateFlow().map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeClip(id: String): Flow<Clip?> =
        _clips.asStateFlow().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun addClip(videoUri: String, caption: String): Resource<Clip> {
        if (videoUri.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val session = sessionFlow.first()
        if (session !is SessionState.LocalAccount && session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val user = userRepository.currentUser.first() ?: return Resource.Failure(FailureReason.GENERIC)
        val clip = Clip(
            id = Ids.newId(),
            author = UserSummary.from(user),
            videoUri = videoUri,
            caption = caption.trim(),
            createdAt = System.currentTimeMillis(),
            likeCount = 0,
            commentCount = 0,
            likedByMe = false,
            savedByMe = false,
            isLocal = true
        )
        _clips.value = listOf(clip) + _clips.value
        persist()
        achievements.record(CounterKind.CLIPS_CREATED)
        return Resource.Success(clip)
    }

    override suspend fun toggleLike(clipId: String) {
        _clips.value = _clips.value.map { clip ->
            if (clip.id != clipId) clip else {
                val liked = !clip.likedByMe
                clip.copy(
                    likedByMe = liked,
                    likeCount = (clip.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                )
            }
        }
        persist()
    }

    override suspend fun toggleSave(clipId: String) {
        _clips.value = _clips.value.map { clip ->
            if (clip.id != clipId) clip else clip.copy(savedByMe = !clip.savedByMe)
        }
        persist()
    }

    override suspend fun deleteClip(clipId: String): Boolean {
        val session = sessionFlow.first()
        val currentId = when (session) {
            is SessionState.LocalAccount -> session.userId
            is SessionState.RemoteAccount -> session.userId
            else -> null
        }
        val existing = _clips.value.firstOrNull { it.id == clipId } ?: return false
        if (currentId == null || existing.author.id != currentId) return false
        _clips.value = _clips.value.filterNot { it.id == clipId }
        persist()
        return true
    }

    private suspend fun persist() {
        val snapshot = _clips.value
        withContext(Dispatchers.IO) { local.writeClips(snapshot) }
    }
}
