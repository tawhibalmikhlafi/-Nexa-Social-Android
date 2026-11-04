package com.nexa.social.data.repository

import android.content.Context
import android.net.Uri
import java.io.File

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.sync.NexaOfflineSyncWorker
import com.nexa.social.data.local.ClipLocalDataSource
import com.nexa.social.data.local.PendingClipAction
import com.nexa.social.data.local.PendingClipComment
import com.nexa.social.data.local.PendingClipUpload
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.SupabaseClipDataSource
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.ClipComment
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.ClipRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val remote: SupabaseClipDataSource? = null,
    private val appContext: Context? = null
) : ClipRepository {

    private val _clips = MutableStateFlow(local.readClips())
    private val outboxLock = kotlinx.coroutines.sync.Mutex()
    private val pendingActions = MutableStateFlow(local.readOutbox())
    private val commentOutboxLock = kotlinx.coroutines.sync.Mutex()
    private val pendingComments = MutableStateFlow(local.readCommentOutbox())
    private val uploadOutboxLock = kotlinx.coroutines.sync.Mutex()
    private val uploadExecutionLock = kotlinx.coroutines.sync.Mutex()
    private val pendingUploads = MutableStateFlow(local.readUploadOutbox())

    override fun observeClips(): Flow<List<Clip>> = sessionFlow.flatMapLatest { session ->
        if (session is SessionState.RemoteAccount && remote != null) {
            combine(remote.observeClips(), pendingActions, pendingUploads) { fresh, actions, uploads ->
                applyPending(fresh, actions, uploads, session.userId)
            }.onStart { emit(_clips.value) }
                .onEach { merged ->
                    _clips.value = merged
                    persist()
                }
        } else {
            _clips.asStateFlow()
        }
    }.map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeClip(id: String): Flow<Clip?> =
        observeClips().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun addClip(videoUri: String, caption: String, coverUri: String?): Resource<Clip> {
        if (videoUri.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val session = sessionFlow.first()
        if (session !is SessionState.LocalAccount && session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val user = userRepository.currentUser.first() ?: return Resource.Failure(FailureReason.GENERIC)
        val author = UserSummary.from(user)
        if (session is SessionState.RemoteAccount) {
            uploadExecutionLock.lock()
            try {
            val storage = mediaStorage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            val remoteStore = remote ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            val clipId = Ids.newId()
            val backup = copyPendingVideo(videoUri, clipId) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
            val coverBackup = coverUri?.let { copyPendingAsset(it, "${clipId}-cover", "jpg") }
            val path = "${session.userId}/$clipId.mp4"
            enqueueUpload(PendingClipUpload(clipId, session.userId, backup, caption.trim(), System.currentTimeMillis(), author, coverBackup))
            when (val upload = storage.uploadVideoResumable(backup, path, null, 0L) { url, offset ->
                updateUploadCheckpoint(clipId, url, offset)
            }) {
                is Resource.Success -> {
                    updateUploadCheckpoint(clipId, null, 0L, uploadedVideoUrl = upload.data)
                    val coverUrl = if (coverBackup != null) {
                        when (val coverUpload = storage.uploadImage(coverBackup, MediaBucket.CLIP_COVERS, "${session.userId}/$clipId.jpg")) {
                            is Resource.Success -> {
                                updateUploadCheckpoint(clipId, null, 0L, uploadedCoverUrl = coverUpload.data)
                                coverUpload.data
                            }
                            is Resource.Failure -> {
                                if (coverUpload.reason == FailureReason.NETWORK) {
                                    val pending = pendingUploads.value.first { it.id == clipId }.let(::toPendingClip)
                                    _clips.value = listOf(pending) + _clips.value.filterNot { it.id == clipId }
                                    persist()
                                    return Resource.Success(pending)
                                }
                                removeUpload(clipId)
                                File(Uri.parse(backup).path.orEmpty()).delete()
                                File(Uri.parse(coverBackup).path.orEmpty()).delete()
                                return coverUpload
                            }
                            Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
                        }
                    } else null
                    val created = remoteStore.createClip(author, path, upload.data, caption, clipId, coverUrl)
                    if (created != null) {
                        removeUpload(clipId)
                        File(Uri.parse(backup).path.orEmpty()).delete()
                        coverBackup?.let { File(Uri.parse(it).path.orEmpty()).delete() }
                        achievements.record(CounterKind.CLIPS_CREATED)
                        remoteStore.notifyChanged()
                        return Resource.Success(created)
                    }
                    // Keep the source and stable clip ID; the retry is safe if the first insert committed.
                    val pending = pendingUploads.value.first { it.id == clipId }.let(::toPendingClip)
                    _clips.value = listOf(pending) + _clips.value.filterNot { it.id == clipId }
                    persist()
                    return Resource.Success(pending)
                }
                is Resource.Failure -> {
                    if (upload.reason != FailureReason.NETWORK) {
                        removeUpload(clipId)
                        File(Uri.parse(backup).path.orEmpty()).delete()
                        coverBackup?.let { File(Uri.parse(it).path.orEmpty()).delete() }
                        return upload
                    }
                    val pending = Clip(clipId, author, backup, caption.trim(), System.currentTimeMillis(), 0, 0, false, false, true)
                    _clips.value = listOf(pending) + _clips.value.filterNot { it.id == clipId }
                    persist()
                    return Resource.Success(pending)
                }
                Resource.Loading -> return Resource.Failure(FailureReason.GENERIC)
            }
            } finally {
                uploadExecutionLock.unlock()
            }
        }
        val clip = Clip(
            id = Ids.newId(), author = author, videoUri = videoUri,
            caption = caption.trim(), createdAt = System.currentTimeMillis(),
            likeCount = 0, commentCount = 0, likedByMe = false, savedByMe = false, isLocal = true, coverUrl = coverUri
        )
        _clips.value = listOf(clip) + _clips.value
        persist()
        achievements.record(CounterKind.CLIPS_CREATED)
        return Resource.Success(clip)
    }

    private suspend fun copyPendingVideo(source: String, id: String): String? = copyPendingAsset(source, id, "mp4")

    private suspend fun copyPendingAsset(source: String, id: String, extension: String): String? {
        val context = appContext ?: return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "pending_clips")
                if (!directory.exists() && !directory.mkdirs()) return@withContext null
                val target = File(directory, "$id.$extension")
                val uri = Uri.parse(source)
                val input = if (uri.scheme == "file") File(uri.path.orEmpty()).inputStream()
                    else context.contentResolver.openInputStream(uri) ?: return@withContext null
                input.use { stream -> target.outputStream().use { sink -> stream.copyTo(sink) } }
                Uri.fromFile(target).toString()
            }
        }.getOrNull()
    }

    private suspend fun enqueueUpload(item: PendingClipUpload) {
        uploadOutboxLock.lock()
        try {
            val next = pendingUploads.value.filterNot { it.id == item.id } + item
            pendingUploads.value = next
            withContext(Dispatchers.IO) { local.writeUploadOutbox(next) }
            appContext?.let { NexaOfflineSyncWorker.schedule(it) }
        } finally { uploadOutboxLock.unlock() }
    }

    private suspend fun removeUpload(id: String) {
        uploadOutboxLock.lock()
        try {
            val next = pendingUploads.value.filterNot { it.id == id }
            pendingUploads.value = next
            withContext(Dispatchers.IO) { local.writeUploadOutbox(next) }
        } finally { uploadOutboxLock.unlock() }
    }

    private suspend fun updateUploadCheckpoint(id: String, uploadUrl: String?, offset: Long, uploadedVideoUrl: String? = null, uploadedCoverUrl: String? = null) {
        uploadOutboxLock.lock()
        try {
            val next = pendingUploads.value.map { item ->
                if (item.id == id) item.copy(uploadUrl = uploadUrl, uploadOffset = offset, uploadedVideoUrl = uploadedVideoUrl ?: item.uploadedVideoUrl, uploadedCoverUrl = uploadedCoverUrl ?: item.uploadedCoverUrl) else item
            }
            pendingUploads.value = next
            withContext(Dispatchers.IO) { local.writeUploadOutbox(next) }
            val owner = next.firstOrNull { it.id == id }?.userId
            if (owner != null) {
                _clips.value = applyPending(_clips.value, pendingActions.value, next, owner)
                persist()
            }
        } finally { uploadOutboxLock.unlock() }
    }

    private suspend fun drainUploadOutbox() {
        uploadExecutionLock.lock()
        try {
        val session = sessionFlow.first() as? SessionState.RemoteAccount ?: return
        val storage = mediaStorage ?: return
        val store = remote ?: return
        for (item in pendingUploads.value.filter { it.userId == session.userId }.sortedBy { it.createdAt }) {
            val path = "${session.userId}/${item.id}.mp4"
            val videoUrl = item.uploadedVideoUrl ?: when (val uploaded = storage.uploadVideoResumable(
                item.localVideoPath, path, item.uploadUrl, item.uploadOffset
            ) { url, offset -> updateUploadCheckpoint(item.id, url, offset) }) {
                is Resource.Success -> uploaded.data
                else -> break
            }
            if (item.uploadedVideoUrl == null) updateUploadCheckpoint(item.id, null, 0L, uploadedVideoUrl = videoUrl)
            val coverUrl = item.uploadedCoverUrl ?: item.localCoverPath?.let { localCover ->
                when (val coverUpload = storage.uploadImage(localCover, MediaBucket.CLIP_COVERS, "${session.userId}/${item.id}.jpg")) {
                    is Resource.Success -> coverUpload.data.also {
                        updateUploadCheckpoint(item.id, null, 0L, uploadedCoverUrl = it)
                    }
                    else -> null
                }
            }
            if (item.localCoverPath != null && coverUrl == null) break
            val clip = store.createClip(item.author, path, videoUrl, item.caption, item.id, coverUrl)
                ?: break
            File(Uri.parse(item.localVideoPath).path.orEmpty()).delete()
            item.localCoverPath?.let { File(Uri.parse(it).path.orEmpty()).delete() }
            removeUpload(item.id)
            _clips.value = listOf(clip) + _clips.value.filterNot { it.id == clip.id }
            achievements.record(CounterKind.CLIPS_CREATED)
        }
        store.notifyChanged()
        persist()
    }

    override suspend fun toggleLike(clipId: String) {
        val session = sessionFlow.first()
        val current = _clips.value.firstOrNull { it.id == clipId } ?: return
        val desired = !current.likedByMe
        if (session is SessionState.RemoteAccount && remote != null) {
            applyLocalDesiredState(clipId, "like", desired)
            if (remote.setLike(clipId, desired)) {
                removePending(session.userId, clipId, "like")
                remote.notifyChanged()
            } else enqueueAction(session.userId, clipId, "like", desired)
            return
        }
        _clips.value = _clips.value.map { clip ->
            if (clip.id != clipId) clip else clip.copy(
                likedByMe = desired,
                likeCount = (clip.likeCount + if (desired) 1 else -1).coerceAtLeast(0)
            )
        }
        persist()
    }

    override suspend fun toggleSave(clipId: String) {
        val session = sessionFlow.first()
        val current = _clips.value.firstOrNull { it.id == clipId } ?: return
        val desired = !current.savedByMe
        if (session is SessionState.RemoteAccount && remote != null) {
            applyLocalDesiredState(clipId, "save", desired)
            if (remote.setSave(clipId, desired)) {
                removePending(session.userId, clipId, "save")
                remote.notifyChanged()
            } else enqueueAction(session.userId, clipId, "save", desired)
            return
        }
        _clips.value = _clips.value.map { clip ->
            if (clip.id == clipId) clip.copy(savedByMe = desired) else clip
        }
        persist()
    }

    override suspend fun getComments(clipId: String): Resource<List<ClipComment>> {
        val session = sessionFlow.first() as? SessionState.RemoteAccount ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        val remoteRows = remote?.getComments(clipId)
        val queued = pendingComments.value.filter { it.userId == session.userId && it.clipId == clipId }.map { item ->
            ClipComment(item.id, item.clipId, item.userId, item.authorName, item.authorAvatarUrl, item.content, item.createdAt, true)
        }
        if (remoteRows == null && queued.isEmpty()) return Resource.Failure(FailureReason.NETWORK)
        return Resource.Success((remoteRows.orEmpty() + queued).distinctBy { it.id }.sortedBy { it.createdAt })
    }

    override suspend fun addComment(clipId: String, content: String): Resource<ClipComment> {
        if (content.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val session = sessionFlow.first() as? SessionState.RemoteAccount ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        val user = userRepository.currentUser.first() ?: return Resource.Failure(FailureReason.GENERIC)
        val id = Ids.newId()
        val comment = remote?.addComment(clipId, content, id)
        if (comment != null) return Resource.Success(comment)
        val item = PendingClipComment(id, session.userId, clipId, user.displayName, user.avatarUri, content.trim(), System.currentTimeMillis())
        enqueueComment(item)
        return Resource.Success(ClipComment(id, clipId, session.userId, user.displayName, user.avatarUri, content.trim(), item.createdAt, true))
    }

    private suspend fun enqueueComment(item: PendingClipComment) {
        commentOutboxLock.lock()
        try {
            val next = pendingComments.value.filterNot { it.id == item.id } + item
            pendingComments.value = next
            withContext(Dispatchers.IO) { local.writeCommentOutbox(next) }
        } finally { commentOutboxLock.unlock() }
    }

    private suspend fun removeQueuedComment(id: String) {
        commentOutboxLock.lock()
        try {
            val next = pendingComments.value.filterNot { it.id == id }
            pendingComments.value = next
            withContext(Dispatchers.IO) { local.writeCommentOutbox(next) }
        } finally { commentOutboxLock.unlock() }
    }

    private suspend fun drainCommentOutbox() {
        val session = sessionFlow.first() as? SessionState.RemoteAccount ?: return
        val store = remote ?: return
        for (item in pendingComments.value.filter { it.userId == session.userId }) {
            if (store.addComment(item.clipId, item.content, item.id) == null) break
            removeQueuedComment(item.id)
        }
    }

    override suspend fun deleteComment(commentId: String): Boolean {
        val session = sessionFlow.first()
        if (session !is SessionState.RemoteAccount || remote == null) return false
        val deleted = remote.deleteComment(commentId)
        if (!deleted) enqueueAction(session.userId, commentId, "delete_comment", true)
        else removePending(session.userId, commentId, "delete_comment")
        return true
    }

    override suspend fun recordView(clipId: String) {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount) remote?.recordView(clipId)
    }
    override suspend fun updateCaption(clipId: String, caption: String): Boolean {
        if (caption.length > 2200) return false
        val session = sessionFlow.first() as? SessionState.RemoteAccount ?: return false
        val updated = remote?.updateCaption(clipId, caption) ?: false
        if (updated) remote?.notifyChanged()
        return updated
    }
    override suspend fun deleteClip(clipId: String): Boolean {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            val path = remote.deleteClip(clipId)
            if (path == null) {
                enqueueAction(session.userId, clipId, "delete_clip", true)
                return true
            }
            mediaStorage?.deleteObject(MediaBucket.CLIPS, path)
            removePending(session.userId, clipId, "delete_clip")
            remote.notifyChanged()
            return true
        }
        val currentId = (session as? SessionState.LocalAccount)?.userId ?: return false
        val existing = _clips.value.firstOrNull { it.id == clipId } ?: return false
        if (existing.author.id != currentId) return false
        _clips.value = _clips.value.filterNot { it.id == clipId }
        persist()
        return true
    }

    override suspend fun refresh() {
        drainUploadOutbox()
        drainOutbox()
        drainCommentOutbox()
        remote?.notifyChanged()
    }

    private suspend fun enqueueAction(userId: String, clipId: String, relation: String, enabled: Boolean) {
        outboxLock.lock()
        try {
            val next = pendingActions.value
                .filterNot { it.userId == userId && it.clipId == clipId && it.relation == relation } +
                PendingClipAction(
                    id = Ids.newId(), clipId = clipId, userId = userId,
                    relation = relation, enabled = enabled, createdAt = System.currentTimeMillis()
                )
            pendingActions.value = next
            withContext(Dispatchers.IO) { local.writeOutbox(next) }
        } finally {
            outboxLock.unlock()
        }
    }

    private suspend fun removePending(userId: String, clipId: String, relation: String) {
        outboxLock.lock()
        try {
            val next = pendingActions.value.filterNot { it.userId == userId && it.clipId == clipId && it.relation == relation }
            pendingActions.value = next
            withContext(Dispatchers.IO) { local.writeOutbox(next) }
        } finally {
            outboxLock.unlock()
        }
    }

    private suspend fun applyLocalDesiredState(clipId: String, relation: String, enabled: Boolean) {
        _clips.value = _clips.value.map { clip ->
            if (clip.id != clipId) clip else when (relation) {
                "like" -> {
                    val delta = when {
                        enabled && !clip.likedByMe -> 1
                        !enabled && clip.likedByMe -> -1
                        else -> 0
                    }
                    clip.copy(likedByMe = enabled, likeCount = (clip.likeCount + delta).coerceAtLeast(0))
                }
                else -> clip.copy(savedByMe = enabled)
            }
        }
        persist()
        } finally {
            uploadExecutionLock.unlock()
        }
    }

    private fun applyPending(
        clips: List<Clip>,
        actions: List<PendingClipAction>,
        uploads: List<PendingClipUpload>,
        userId: String
    ): List<Clip> {
        val ownUploads = uploads.filter { it.userId == userId }.associateBy { it.id }
        val merged = clips.map { clip -> ownUploads[clip.id]?.let(::toPendingClip) ?: clip }.toMutableList()
        ownUploads.values.forEach { item ->
            if (merged.none { it.id == item.id }) merged += toPendingClip(item)
        }
        return merged.map { original ->
            actions.filter { it.userId == userId && it.clipId == original.id }
                .fold(original) { clip, action ->
                    when (action.relation) {
                        "like" -> {
                            val delta = when {
                                action.enabled && !clip.likedByMe -> 1
                                !action.enabled && clip.likedByMe -> -1
                                else -> 0
                            }
                            clip.copy(likedByMe = action.enabled, likeCount = (clip.likeCount + delta).coerceAtLeast(0))
                        }
                        "save" -> clip.copy(savedByMe = action.enabled)
                        else -> clip
                    }
                }
        }
    }

    private fun toPendingClip(item: PendingClipUpload): Clip {
        val totalBytes = File(Uri.parse(item.localVideoPath).path.orEmpty()).length()
        val progress = if (item.uploadedVideoUrl != null) 1f
            else if (totalBytes > 0L) (item.uploadOffset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else 0f
        val state = when {
            item.uploadedVideoUrl != null -> "publishing"
            item.uploadUrl != null -> "uploading"
            else -> "waiting"
        }
        return Clip(
            id = item.id, author = item.author,
            videoUri = item.uploadedVideoUrl ?: item.localVideoPath,
            caption = item.caption, createdAt = item.createdAt,
            likeCount = 0, commentCount = 0, likedByMe = false, savedByMe = false,
            isLocal = true, coverUrl = item.uploadedCoverUrl ?: item.localCoverPath,
            uploadProgress = progress, uploadState = state
        )
    }

    private suspend fun drainOutbox() {
        val session = sessionFlow.first() as? SessionState.RemoteAccount ?: return
        val store = remote ?: return
        val actions = pendingActions.value.filter { it.userId == session.userId }
        for (action in actions) {
            val success = when (action.relation) {
                "like" -> store.setLike(action.clipId, action.enabled)
                "save" -> store.setSave(action.clipId, action.enabled)
                "delete_comment" -> store.deleteComment(action.clipId)
                "delete_clip" -> {
                    val path = store.deleteClip(action.clipId)
                    if (path != null) { mediaStorage?.deleteObject(MediaBucket.CLIPS, path); true } else false
                }
                else -> true
            }
            if (success) removePending(session.userId, action.clipId, action.relation) else break
        }
    }

    private suspend fun persist() {
        val snapshot = _clips.value
        withContext(Dispatchers.IO) { local.writeClips(snapshot) }
    }
}
