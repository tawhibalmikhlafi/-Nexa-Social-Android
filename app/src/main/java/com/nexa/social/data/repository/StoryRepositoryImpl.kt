package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.StoryLocalDataSource
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.SupabaseStoryDataSource
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.Story
import com.nexa.social.domain.model.StoryAudienceMember
import com.nexa.social.domain.model.StoryGroup
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.StoryRepository
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
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class StoryRepositoryImpl(
    private val local: StoryLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val achievements: AchievementRepository,
    private val prefs: PreferenceStorage,
    private val mediaStorage: MediaStorage? = null,
    private val remote: SupabaseStoryDataSource? = null
) : StoryRepository {

    private val _stories = MutableStateFlow(local.readStories())

    override fun observeActiveStories(): Flow<List<Story>> =
        observeStoryGroups().map { groups ->
            groups.flatMap { it.stories }.sortedBy { it.createdAt }
        }

    override fun observeStoryGroups(): Flow<List<StoryGroup>> =
        sessionFlow.flatMapLatest { session ->
            val remoteStore = remote
            if (session is SessionState.RemoteAccount && remoteStore != null) {
                combine(remoteStore.observeStories(), prefs.viewedStoryIds) { stories, viewed ->
                    stories.map { story ->
                        story.copy(viewedLocally = story.isMine || story.id in viewed)
                    }
                }.map(::groupStories)
            } else {
                _stories.asStateFlow().map { list ->
                    groupStories(
                        list.filterNot { it.isExpired() }
                            .map { story ->
                                if (story.isMine) story.copy(viewedLocally = true) else story
                            }
                    )
                }
            }
        }

    private fun groupStories(stories: List<Story>): List<StoryGroup> =
        stories
            .filterNot { it.isExpired() }
            .groupBy { it.author.id }
            .map { (_, authorStories) ->
                StoryGroup(
                    author = authorStories.first().author,
                    stories = authorStories.sortedBy { it.createdAt }
                )
            }
            .sortedWith(
                compareByDescending<StoryGroup> { group -> group.stories.any { it.isMine } }
                    .thenByDescending { group -> group.hasUnseen }
                    .thenByDescending { group ->
                        group.stories.maxOfOrNull { it.createdAt } ?: 0L
                    }
            )

    override suspend fun createStory(imageUri: String?, text: String?): Resource<Story> {
        if (imageUri.isNullOrBlank() && text.isNullOrBlank()) {
            return Resource.Failure(FailureReason.REQUIRED)
        }
        val session = sessionFlow.first()
        if (session !is SessionState.LocalAccount && session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val userId = session.accountUserId
            ?: return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        val user = userRepository.getUser(userId)
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val author = UserSummary.from(user)
        val storyId = Ids.newId()
        var storedImage = imageUri
        if (session is SessionState.RemoteAccount && !imageUri.isNullOrBlank() &&
            !imageUri.startsWith("http")
        ) {
            val storage = mediaStorage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            when (val upload = storage.uploadImage(
                localUri = imageUri,
                bucket = MediaBucket.STORIES,
                path = "${session.userId}/$storyId"
            )) {
                is Resource.Success -> storedImage = upload.data
                is Resource.Failure -> return upload
                Resource.Loading -> Unit
            }
        }
        val now = System.currentTimeMillis()
        val story = Story(
            id = storyId,
            author = author,
            imageUri = storedImage,
            text = text?.trim()?.ifBlank { null },
            createdAt = now,
            expiresAt = now + TimeUnit.HOURS.toMillis(24),
            viewedLocally = true,
            isMine = true,
            isLocal = session !is SessionState.RemoteAccount
        )

        if (session is SessionState.RemoteAccount && remote != null) {
            val created = remote.createStory(
                author = author,
                imageUri = storedImage,
                text = story.text
            ) ?: return Resource.Failure(FailureReason.GENERIC)
            achievements.record(CounterKind.STORIES_CREATED)
            return Resource.Success(created)
        }

        _stories.value = listOf(story) + _stories.value
        persist()
        achievements.record(CounterKind.STORIES_CREATED)
        return Resource.Success(story)
    }

    override suspend fun markViewed(storyId: String) {
        val session = sessionFlow.first()
        if (session is SessionState.RemoteAccount && remote != null) {
            val viewed = prefs.viewedStoryIds.first()
            if (storyId !in viewed) {
                prefs.setViewedStoryIds(viewed + storyId)
            }
            // Persist the view for the story owner; the RPC is idempotent.
            remote.recordView(storyId)
            return
        }
        _stories.value = _stories.value.map {
            if (it.id == storyId) it.copy(viewedLocally = true) else it
        }
        persist()
    }

    override suspend fun getStoryAudience(
        storyId: String
    ): Resource<List<StoryAudienceMember>> {
        val session = sessionFlow.first()
        val remoteStore = remote
        if (session !is SessionState.RemoteAccount || remoteStore == null) {
            return Resource.Success(emptyList())
        }
        return remoteStore.fetchAudience(storyId)
    }

    override suspend fun deleteStory(storyId: String): Boolean {
        val session = sessionFlow.first()
        val remoteStore = remote
        if (session is SessionState.RemoteAccount && remoteStore != null) {
            return remoteStore.deleteStory(storyId)
        }
        val existing = _stories.value.firstOrNull { it.id == storyId } ?: return false
        if (!existing.isMine) return false
        if (existing.imageUri?.startsWith("http") == true) {
            mediaStorage?.deleteByPublicUrl(existing.imageUri)
        }
        _stories.value = _stories.value.filterNot { it.id == storyId }
        persist()
        return true
    }

    override suspend fun refresh() {
        runCatching { remote?.notifyChanged() }
    }

    private suspend fun persist() {
        val snapshot = _stories.value
        withContext(Dispatchers.IO) { local.writeStories(snapshot) }
    }
}
