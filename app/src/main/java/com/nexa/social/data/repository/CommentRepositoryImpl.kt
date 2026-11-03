package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.data.local.CommentLocalDataSource
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.SupabaseCommentDataSource
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.GuestIdentity
import com.nexa.social.domain.model.PostComment
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.CommentRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class CommentRepositoryImpl(
    private val local: CommentLocalDataSource,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val achievements: AchievementRepository,
    private val remote: SupabaseCommentDataSource? = null,
    private val mediaStorage: MediaStorage? = null
) : CommentRepository {

    private val _comments = MutableStateFlow(local.readComments())

    override fun observeComments(postId: String): Flow<List<PostComment>> {
        val commentsFlow: Flow<List<PostComment>> = sessionFlow.flatMapLatest { session ->
            if (session is SessionState.RemoteAccount && remote != null) {
                remote.observeComments(postId)
            } else {
                _comments.asStateFlow().map { list ->
                    list.filter { it.postId == postId }.sortedBy { it.createdAt }
                }
            }
        }
        val visible = postRepository.observePost(postId)
            .map { it != null }
            .catch { emit(false) }
        return combine(commentsFlow, visible) { comments, isVisible ->
            if (isVisible) comments else emptyList()
        }
    }

    override fun observeCommentCount(postId: String): Flow<Int> =
        observeComments(postId).map { it.size }

    override suspend fun addComment(
        postId: String,
        text: String,
        parentId: String?,
        imageUri: String?
    ): Resource<PostComment> {
        val clean = text.trim()
        val hasImage = !imageUri.isNullOrBlank()
        if (clean.length < CommentRepository.MIN_COMMENT_LENGTH && !hasImage) {
            return Resource.Failure(FailureReason.REQUIRED)
        }
        if (clean.length > CommentRepository.MAX_COMMENT_LENGTH) {
            return Resource.Failure(FailureReason.GENERIC)
        }
        if (postRepository.observePost(postId).first() == null) {
            return Resource.Failure(FailureReason.PERMISSION_DENIED)
        }
        val author = currentIdentity()
        val session = sessionFlow.first()
        val remoteStore = remote
        if (session is SessionState.RemoteAccount && remoteStore != null) {
            val storedImage = when (val upload = uploadCommentImage(session.userId, imageUri)) {
                is Resource.Success -> upload.data
                is Resource.Failure -> return Resource.Failure(upload.reason)
                Resource.Loading -> null
            }
            return try {
                val comment = remoteStore.addComment(postId, author, clean, parentId, storedImage)
                postRepository.adjustCommentCount(postId, 1)
                achievements.record(CounterKind.COMMENTS_CREATED)
                Resource.Success(comment)
            } catch (error: Exception) {
                Resource.Failure(FailureReason.NETWORK)
            }
        }
        val now = System.currentTimeMillis()
        val comment = PostComment(
            id = Ids.newId(),
            postId = postId,
            author = author,
            text = clean,
            imageUri = imageUri,
            createdAt = now,
            updatedAt = now,
            parentId = parentId,
            isMine = true,
            canDelete = true,
            isLocal = true
        )
        _comments.value = _comments.value + comment
        persist()
        postRepository.adjustCommentCount(postId, 1)
        achievements.record(CounterKind.COMMENTS_CREATED)
        return Resource.Success(comment)
    }

    override suspend fun editComment(commentId: String, text: String): Resource<PostComment> {
        val clean = text.trim()
        if (clean.length < CommentRepository.MIN_COMMENT_LENGTH) return Resource.Failure(FailureReason.REQUIRED)
        if (clean.length > CommentRepository.MAX_COMMENT_LENGTH) return Resource.Failure(FailureReason.GENERIC)
        val session = sessionFlow.first()
        val remoteStore = remote
        if (session is SessionState.RemoteAccount && remoteStore != null) {
            val updated = runCatching { remoteStore.editComment(commentId, clean) }.getOrNull()
                ?: return Resource.Failure(FailureReason.PERMISSION_DENIED)
            return Resource.Success(updated)
        }
        val existing = _comments.value.firstOrNull { it.id == commentId }
            ?: return Resource.Failure(FailureReason.GENERIC)
        if (!existing.isMine) return Resource.Failure(FailureReason.PERMISSION_DENIED)
        val updated = existing.copy(text = clean, updatedAt = System.currentTimeMillis())
        _comments.value = _comments.value.map { if (it.id == commentId) updated else it }
        persist()
        return Resource.Success(updated)
    }

    override suspend fun deleteComment(commentId: String): Boolean {
        val session = sessionFlow.first()
        val remoteStore = remote
        if (session is SessionState.RemoteAccount && remoteStore != null) {
            val existing = remoteStore.getComment(commentId) ?: return false
            val deleted = runCatching { remoteStore.deleteComment(commentId) }.getOrDefault(false)
            if (deleted) {
                postRepository.adjustCommentCount(existing.postId, -1)
                existing.imageUri?.let { mediaStorage?.deleteByPublicUrl(it) }
            }
            return deleted
        }
        val existing = _comments.value.firstOrNull { it.id == commentId } ?: return false
        if (!existing.isMine && !existing.canDelete) return false
        _comments.value = _comments.value.filterNot { it.id == commentId }
        persist()
        postRepository.adjustCommentCount(existing.postId, -1)
        return true
    }

    override suspend fun toggleCommentLike(commentId: String) {
        val session = sessionFlow.first()
        val remoteStore = remote
        if (session is SessionState.RemoteAccount && remoteStore != null) {
            runCatching { remoteStore.toggleLike(commentId) }
            return
        }
        _comments.value = _comments.value.map { comment ->
            if (comment.id != commentId) comment else {
                val liked = !comment.likedByMe
                comment.copy(
                    likedByMe = liked,
                    likeCount = (comment.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                )
            }
        }
        persist()
    }

    override fun observeCommentPageSize(): Flow<Int> =
        remote?.pageSize?.map { it.toInt() } ?: flowOf(Int.MAX_VALUE)

    override suspend fun loadMoreComments() {
        remote?.loadMore()
    }

    override suspend fun refreshAuthorSnapshot(author: UserSummary) {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            runCatching { remote?.refreshAuthor(author) }
        }
        val updated = _comments.value.map { comment ->
            if (comment.author.id == author.id) comment.copy(author = author) else comment
        }
        if (updated != _comments.value) {
            _comments.value = updated
            persist()
        }
    }

    private suspend fun uploadCommentImage(uid: String, localUri: String?): Resource<String?> {
        if (localUri.isNullOrBlank()) return Resource.Success(null)
        if (localUri.startsWith("http://") || localUri.startsWith("https://")) {
            return Resource.Success(localUri)
        }
        val storage = mediaStorage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        val path = "$uid/comments/${Ids.newId()}"
        return when (val upload = storage.uploadImage(localUri, MediaBucket.POSTS, path)) {
            is Resource.Success -> Resource.Success(upload.data)
            is Resource.Failure -> Resource.Failure(upload.reason)
            Resource.Loading -> Resource.Failure(FailureReason.UPLOAD_FAILED)
        }
    }

    private suspend fun currentIdentity(): UserSummary {
        val session = sessionFlow.first()
        return when (session) {
            is SessionState.LocalAccount, is SessionState.RemoteAccount ->
                userRepository.currentUser.first()?.let { UserSummary.from(it) } ?: GuestIdentity.summary
            else -> GuestIdentity.summary
        }
    }

    private suspend fun persist() {
        val snapshot = _comments.value
        withContext(Dispatchers.IO) { local.writeComments(snapshot) }
    }
}
