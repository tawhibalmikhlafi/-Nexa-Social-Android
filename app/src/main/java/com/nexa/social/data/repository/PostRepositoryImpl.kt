package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.PostLocalDataSource
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.SupabasePostDataSource
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.GuestIdentity
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostCreationException
import com.nexa.social.domain.model.PostDraft
import com.nexa.social.domain.model.PostReaction
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PostRepositoryImpl(
    private val local: PostLocalDataSource,
    private val userRepository: UserRepository,
    private val prefs: PreferenceStorage,
    private val achievements: AchievementRepository,
    private val remote: SupabasePostDataSource? = null,
    private val storage: MediaStorage? = null,
) : PostRepository {

    private val _posts = MutableStateFlow(local.readPosts())
    private val _draft = MutableStateFlow(local.readDraft())
    private val _uploadProgress = MutableStateFlow(0)

    override val uploadProgress: Flow<Int> = _uploadProgress.asStateFlow()

    private fun visible(): List<Post> = _posts.value
        .filterNot { it.isHiddenLocally }
        .sortedWith(compareByDescending<Post> { it.pinned }.thenByDescending { it.createdAt })

    private fun isRemote(session: SessionState): Boolean =
        session is SessionState.RemoteAccount && remote != null

    private fun sortedWithPins(posts: List<Post>, pinnedIds: Set<String>): List<Post> =
        posts.map { if (it.id in pinnedIds) it.copy(pinned = true) else it }
            .sortedWith(compareByDescending<Post> { it.pinned }.thenByDescending { it.createdAt })

    private fun canView(post: Post, uid: String, following: Set<String>): Boolean {
        val mine = post.author.id == uid
        return when (post.audience) {
            Audience.ONLY_ME -> mine
            Audience.FOLLOWERS -> mine || post.author.id in following
            Audience.PUBLIC_LOCAL -> true
        }
    }

    private fun applyReactionOverrides(posts: List<Post>, overrides: Map<String, String>): List<Post> {
        if (overrides.isEmpty()) return posts
        return posts.map { post ->
            val override = overrides[post.id] ?: return@map post
            if (override.isEmpty()) {
                post.copy(likedByMe = false, myReaction = null)
            } else {
                post.copy(likedByMe = true, myReaction = override)
            }
        }
    }

    override fun observeFeed(): Flow<List<Post>> = prefs.session.flatMapLatest { session ->
        val remoteStore = remote
        val uid = session.accountUserId
        if (isRemote(session) && remoteStore != null && uid != null) {
            combine(
                remoteStore.observeFeed(),
                prefs.hiddenPostIds,
                prefs.pinnedPostIds,
                userRepository.observeFollowingIds(),
                prefs.reactionOverrides
            ) { posts, hidden, pinned, following, overrides ->
                val visible = posts
                    .filter { canView(it, uid, following) }
                    .filterNot { it.id in hidden }
                sortedWithPins(applyReactionOverrides(visible, overrides), pinned)
            }
        } else {
            _posts.asStateFlow().map { visible() }
        }
    }

    override fun observePostsByAuthor(authorId: String): Flow<List<Post>> =
        prefs.session.flatMapLatest { session ->
            val remoteStore = remote
            val uid = session.accountUserId
            if (isRemote(session) && remoteStore != null && uid != null) {
                combine(
                    remoteStore.observePostsByAuthor(authorId),
                    prefs.hiddenPostIds,
                    userRepository.observeFollowingIds(),
                    prefs.reactionOverrides
                ) { posts, hidden, following, overrides ->
                    applyReactionOverrides(
                        posts.filter { canView(it, uid, following) }.filterNot { it.id in hidden },
                        overrides
                    )
                }
            } else {
                _posts.asStateFlow().map { posts ->
                    posts.filter { it.author.id == authorId }.sortedByDescending { it.createdAt }
                }
            }
        }

    override fun observeSavedPosts(): Flow<List<Post>> = prefs.session.flatMapLatest { session ->
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            combine(
                remoteStore.observeSavedPosts(),
                prefs.hiddenPostIds,
                prefs.reactionOverrides
            ) { posts, hidden, overrides ->
                applyReactionOverrides(posts.filterNot { it.id in hidden }, overrides)
            }
        } else {
            _posts.asStateFlow().map { posts ->
                posts.filter { it.savedByMe && !it.isHiddenLocally }
                    .sortedByDescending { it.createdAt }
            }
        }
    }

    override fun observeHiddenPosts(): Flow<List<Post>> = prefs.session.flatMapLatest { session ->
        val remoteStore = remote
        if (isRemote(session) && remoteStore != null) {
            remoteStore.observeHiddenPosts(prefs.hiddenPostIds)
        } else {
            _posts.asStateFlow().map { posts -> posts.filter { it.isHiddenLocally } }
        }
    }

    override fun observePost(postId: String): Flow<Post?> = prefs.session.flatMapLatest { session ->
        val remoteStore = remote
        val uid = session.accountUserId
        if (isRemote(session) && remoteStore != null && uid != null) {
            combine(
                remoteStore.observePost(postId),
                userRepository.observeFollowingIds(),
                prefs.reactionOverrides
            ) { post, following, overrides ->
                post?.takeIf { canView(it, uid, following) }
                    ?.let { applyReactionOverrides(listOf(it), overrides).first() }
            }
        } else {
            _posts.asStateFlow().map { posts -> posts.firstOrNull { it.id == postId } }
        }
    }

    override suspend fun loadMoreFeed() {
        if (prefs.session.first() is SessionState.RemoteAccount) remote?.loadMore()
    }

    override suspend fun createPost(
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: Audience,
        communityId: String?
    ): Post {
        val cleanTags = tags.map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()
        val session = prefs.session.first()
        val remoteStore = remote
        if (remoteStore != null && session is SessionState.RemoteAccount) {
            return createRemotePost(
                session = session,
                text = text,
                imageUris = imageUris,
                tags = cleanTags,
                audience = audience,
                communityId = communityId,
                remote = remoteStore
            )
        }
        val author = currentIdentity()
        val now = System.currentTimeMillis()
        val post = Post(
            id = Ids.newId(),
            author = author,
            text = text.trim(),
            imageUri = imageUris.firstOrNull(),
            imageUris = imageUris,
            tags = cleanTags,
            audience = audience,
            createdAt = now,
            updatedAt = now,
            likeCount = 0,
            commentCount = 0,
            shareCount = 0,
            likedByMe = false,
            savedByMe = false,
            communityId = communityId,
            isLocal = true
        )
        _posts.value = listOf(post) + _posts.value
        persist()
        if (audience != Audience.ONLY_ME) achievements.record(CounterKind.POSTS_CREATED)
        return post
    }

    private suspend fun createRemotePost(
        session: SessionState.RemoteAccount,
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: Audience,
        communityId: String?,
        remote: SupabasePostDataSource
    ): Post {
        val author = userRepository.getUser(session.userId)?.let { UserSummary.from(it) }
            ?: throw PostCreationException(FailureReason.NOT_AUTHENTICATED)
        return try {
            val urls = uploadImages(session.userId, imageUris)
            val post = remote.createPost(
                author = author,
                text = text,
                imageUrls = urls,
                tags = tags,
                audience = audience,
                communityId = communityId
            )
            if (audience != Audience.ONLY_ME) achievements.record(CounterKind.POSTS_CREATED)
            post
        } catch (error: PostCreationException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw PostCreationException(
                FailureReason.GENERIC,
                error.message ?: error.javaClass.simpleName
            )
        } finally {
            _uploadProgress.value = 0
        }
    }

    private suspend fun uploadImages(uid: String, imageUris: List<String>): List<String> {
        if (imageUris.isEmpty()) return emptyList()
        val storageStore = storage ?: throw PostCreationException(FailureReason.NOT_CONFIGURED)
        val urls = mutableListOf<String>()
        imageUris.forEachIndexed { index, uri ->
            val path = "$uid/${Ids.newId()}"
            when (val result = storageStore.uploadImage(uri, MediaBucket.POSTS, path) { progress ->
                val overall = ((index + progress) / imageUris.size.toFloat() * 100f).toInt()
                _uploadProgress.value = overall.coerceIn(0, 100)
            }) {
                is Resource.Success -> urls += result.data
                is Resource.Failure -> throw PostCreationException(
                    result.reason.takeUnless { it == FailureReason.GENERIC }
                        ?: FailureReason.UPLOAD_FAILED
                )
                Resource.Loading -> Unit
            }
        }
        _uploadProgress.value = 100
        return urls
    }

    override suspend fun updatePost(
        postId: String,
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: Audience
    ): Post? {
        val cleanTags = tags.map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()
        val session = prefs.session.first()
        val remoteStore = remote
        if (remoteStore != null && session is SessionState.RemoteAccount) {
            val existing = remoteStore.getPost(postId) ?: return null
            if (existing.author.id != session.userId) return null
            val newLocalImages = imageUris.filterNot { it.startsWith("http") }
            val keptRemote = imageUris.filter { it.startsWith("http") }
            return try {
                val uploaded = uploadImages(session.userId, newLocalImages)
                runCatching {
                    remoteStore.updatePost(
                        postId = postId,
                        text = text,
                        imageUrls = keptRemote + uploaded,
                        tags = cleanTags,
                        audience = audience
                    )
                }.getOrNull()
            } catch (error: PostCreationException) {
                null
            } finally {
                _uploadProgress.value = 0
            }
        }
        val existing = _posts.value.firstOrNull { it.id == postId } ?: return null
        if (!canManage(existing)) return null
        val updated = existing.copy(
            text = text.trim(),
            imageUri = imageUris.firstOrNull(),
            imageUris = imageUris,
            tags = cleanTags,
            audience = audience,
            updatedAt = System.currentTimeMillis()
        )
        _posts.value = _posts.value.map { if (it.id == postId) updated else it }
        persist()
        return updated
    }

    override suspend fun toggleLike(postId: String, reaction: String?) {
        val session = prefs.session.first()
        val existing = _posts.value.firstOrNull { it.id == postId }
        val overrideCurrent = prefs.reactionOverrides.first()[postId]?.takeIf { it.isNotEmpty() }
        val currentReaction = existing?.myReaction ?: overrideCurrent
        val newReaction: String? = when {
            reaction != null && currentReaction != reaction -> reaction
            reaction != null && currentReaction == reaction -> null
            else -> if (currentReaction != null) null else "👍"
        }
        prefs.setReactionOverride(postId, newReaction)
        if (isRemote(session)) {
            runCatching { remote?.toggleLike(postId, reaction) }
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else {
                val liked = newReaction != null
                val delta = when {
                    currentReaction == null && newReaction != null -> 1
                    currentReaction != null && newReaction == null -> -1
                    else -> 0
                }
                post.copy(
                    likedByMe = liked,
                    myReaction = newReaction,
                    likeCount = (post.likeCount + delta).coerceAtLeast(0)
                )
            }
        }
        persist()
    }

    override suspend fun refresh() {
        runCatching { remote?.notifyChanged() }
    }

    override suspend fun getPostReactions(postId: String): List<PostReaction> {
        val session = prefs.session.first()
        val remoteStore = remote
        if (remoteStore != null && isRemote(session)) {
            return runCatching { remoteStore.getPostReactions(postId) }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    override suspend fun toggleSave(postId: String) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            runCatching { remote?.toggleSave(postId) }
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else post.copy(savedByMe = !post.savedByMe)
        }
        persist()
    }

    override suspend fun incrementShare(postId: String) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            runCatching { remote?.incrementShare(postId) }
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else post.copy(shareCount = post.shareCount + 1)
        }
        persist()
    }

    override suspend fun adjustCommentCount(postId: String, delta: Int) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            // Postgres triggers keep posts.comment_count authoritative; just refresh the feed.
            runCatching { remote?.notifyChanged() }
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else {
                post.copy(commentCount = (post.commentCount + delta).coerceAtLeast(0))
            }
        }
        persist()
    }

    override suspend fun deletePost(postId: String): Boolean {
        val session = prefs.session.first()
        val remoteStore = remote
        if (remoteStore != null && session is SessionState.RemoteAccount) {
            val existing = remoteStore.getPost(postId) ?: return false
            if (existing.author.id != session.userId) return false
            existing.imageUris.forEach { storage?.deleteByPublicUrl(it) }
            return runCatching { remoteStore.deletePost(postId) }.getOrDefault(false)
        }
        val existing = _posts.value.firstOrNull { it.id == postId } ?: return false
        if (!canManage(existing)) return false
        _posts.value = _posts.value.filterNot { it.id == postId }
        persist()
        return true
    }

    override suspend fun hidePost(postId: String) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            prefs.setHiddenPostIds(prefs.hiddenPostIds.first() + postId)
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else post.copy(isHiddenLocally = true)
        }
        persist()
    }

    override suspend fun unhidePost(postId: String) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            prefs.setHiddenPostIds(prefs.hiddenPostIds.first() - postId)
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else post.copy(isHiddenLocally = false)
        }
        persist()
    }

    override suspend fun togglePin(postId: String) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            val pinned = prefs.pinnedPostIds.first()
            prefs.setPinnedPostIds(
                if (postId in pinned) pinned - postId else pinned + postId
            )
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else post.copy(pinned = !post.pinned)
        }
        persist()
    }

    override fun observeDraft(): Flow<PostDraft?> = _draft.asStateFlow()

    override suspend fun saveDraft(draft: PostDraft) {
        _draft.value = draft
        withContext(Dispatchers.IO) { local.writeDraft(draft) }
    }

    override suspend fun clearDraft() {
        _draft.value = null
        withContext(Dispatchers.IO) { local.writeDraft(null) }
    }

    override suspend fun refreshAuthorSnapshot(author: UserSummary) {
        val session = prefs.session.first()
        if (isRemote(session)) {
            runCatching { remote?.refreshAuthor(author) }
        }
        val updated = _posts.value.map { post ->
            if (post.author.id == author.id) post.copy(author = author) else post
        }
        if (updated != _posts.value) {
            _posts.value = updated
            persist()
        }
    }

    suspend fun canManage(post: Post): Boolean = post.author.id == currentIdentity().id

    private suspend fun currentIdentity(): UserSummary {
        val session = prefs.session.first()
        return when (session) {
            is SessionState.LocalAccount, is SessionState.RemoteAccount ->
                userRepository.currentUser.first()?.let { UserSummary.from(it) } ?: GuestIdentity.summary
            SessionState.Guest -> GuestIdentity.summary
            else -> GuestIdentity.summary
        }
    }

    private suspend fun persist() {
        val snapshot = _posts.value
        withContext(Dispatchers.IO) { local.writePosts(snapshot) }
    }
}
