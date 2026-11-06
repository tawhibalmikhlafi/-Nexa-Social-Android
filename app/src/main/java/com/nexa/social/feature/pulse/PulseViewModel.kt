package com.nexa.social.feature.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.repository.CommunityRepository
import com.nexa.social.domain.repository.PostRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.time.Instant

data class PulseTag(
    val name: String,
    val posts: Int,
    val engagement: Int
)

data class PulseCreator(
    val user: UserSummary,
    val posts: Int,
    val engagement: Int
)

data class PulseWorld(
    val community: Community,
    val posts: Int
)

data class PulseSnapshot(
    val totalPosts: Int = 0,
    val activeToday: Int = 0,
    val tags: List<PulseTag> = emptyList(),
    val creators: List<PulseCreator> = emptyList(),
    val worlds: List<PulseWorld> = emptyList()
)

/**
 * Pulse is a local signal processor over the real feed. It never invents trends or remote
 * statistics; every number is derived from posts already present on this device.
 */
class PulseViewModel(
    postRepository: PostRepository,
    communityRepository: CommunityRepository,
    private val dailyRepository: SupabaseDailyPulseRepository?
) : ViewModel() {

    data class DailyState(
        val loading: Boolean = true,
        val prompt: DailyPulsePrompt? = null,
        val entries: List<DailyPulseEntry> = emptyList(),
        val myEntryId: String? = null,
        val message: String? = null,
        val submitting: Boolean = false,
        val badges: List<PulseBadge> = emptyList()
    )

    data class FriendsState(
        val loading: Boolean = false,
        val friends: List<PulseFriend> = emptyList(),
        val challenges: List<PulseFriendChallenge> = emptyList(),
        val submitting: Boolean = false,
        val message: String? = null
    )

    data class CommentsState(
        val entryId: String? = null,
        val loading: Boolean = false,
        val comments: List<PulseComment> = emptyList(),
        val submitting: Boolean = false,
        val message: String? = null
    )

    private val _dailyState = MutableStateFlow(DailyState())
    val dailyState = _dailyState.asStateFlow()
    private val _commentsState = MutableStateFlow(CommentsState())
    val commentsState = _commentsState.asStateFlow()
    private val _friendsState = MutableStateFlow(FriendsState())
    val friendsState = _friendsState.asStateFlow()

    init { refreshDaily() }

    fun refreshDaily() {
        viewModelScope.launch {
            _dailyState.value = _dailyState.value.copy(loading = true, message = null)
            val feed = dailyRepository?.load()
            val earnedBadges = dailyRepository?.loadMyBadges().orEmpty().associateBy { it.id }
            val badges = PulseBadge.catalog.map { definition ->
                definition.copy(earnedAt = earnedBadges[definition.id]?.earnedAt)
            }
            _dailyState.value = if (feed == null) {
                DailyState(loading = false, message = "تعذر الاتصال بنبض Nexa. تحقق من تسجيل الدخول والإنترنت.", badges = badges)
            } else {
                DailyState(
                    loading = false,
                    prompt = feed.prompt,
                    entries = feed.entries,
                    myEntryId = feed.myEntryId,
                    badges = badges
                )
            }
            feed?.prompt?.id?.let(::refreshFriends)
        }
    }

    fun refreshFriends(promptId: String) {
        viewModelScope.launch {
            _friendsState.value = _friendsState.value.copy(loading = true, message = null)
            val friends = dailyRepository?.loadMutualFriends()
            val challenges = dailyRepository?.loadFriendChallenges(promptId)
            _friendsState.value = if (friends == null || challenges == null) {
                FriendsState(message = "تعذر تحميل الأصدقاء والتحديات. تحقق من الاتصال.")
            } else {
                FriendsState(friends = friends, challenges = challenges)
            }
        }
    }

    fun sendFriendChallenge(friendId: String, message: String = "") {
        val promptId = _dailyState.value.prompt?.id ?: return
        viewModelScope.launch {
            _friendsState.value = _friendsState.value.copy(submitting = true, message = null)
            val ok = dailyRepository?.sendFriendChallenge(promptId, friendId, message) == true
            _friendsState.value = _friendsState.value.copy(
                submitting = false,
                message = if (ok) "تم إرسال التحدي إلى صديقك." else "تعذر إرسال التحدي. تأكد أنكما تتابعان بعضكما."
            )
            if (ok) refreshFriends(promptId)
        }
    }

    fun respondToFriendChallenge(challengeId: String, accepted: Boolean) {
        val promptId = _dailyState.value.prompt?.id ?: return
        viewModelScope.launch {
            val ok = dailyRepository?.respondToFriendChallenge(challengeId, accepted) == true
            _friendsState.value = _friendsState.value.copy(
                message = if (ok) "تم تحديث حالة التحدي." else "تعذر تحديث التحدي."
            )
            if (ok) refreshFriends(promptId)
        }
    }

    fun publish(text: String, imageUri: String? = null, videoUri: String? = null) {
        val prompt = _dailyState.value.prompt ?: return
        if (_dailyState.value.submitting || (text.isBlank() && imageUri.isNullOrBlank())) return
        viewModelScope.launch {
            _dailyState.value = _dailyState.value.copy(submitting = true, message = null)
            val ok = dailyRepository?.publish(prompt.id, text, imageUri, videoUri) == true
            if (ok) refreshDaily()
            else _dailyState.value = _dailyState.value.copy(
                submitting = false,
                message = "لم تُنشر المشاركة. تحقق من الصورة والاتصال، أو ربما شاركت بالفعل في تحدي اليوم."
            )
        }
    }

    fun loadComments(entryId: String) {
        viewModelScope.launch {
            _commentsState.value = CommentsState(entryId = entryId, loading = true)
            val comments = dailyRepository?.loadComments(entryId)
            _commentsState.value = if (comments == null) {
                CommentsState(entryId = entryId, message = "تعذر تحميل التعليقات. تحقق من الاتصال ثم أعد المحاولة.")
            } else {
                CommentsState(entryId = entryId, comments = comments)
            }
        }
    }

    fun addComment(entryId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 500 || _commentsState.value.submitting) return
        viewModelScope.launch {
            _commentsState.value = _commentsState.value.copy(submitting = true, message = null)
            val ok = dailyRepository?.addComment(entryId, clean) == true
            if (ok) {
                loadComments(entryId)
                refreshDaily()
            } else {
                _commentsState.value = _commentsState.value.copy(
                    submitting = false,
                    message = "تعذر نشر التعليق. قد يكون التحدي انتهى أو انقطع الاتصال."
                )
            }
        }
    }

    fun toggleReaction(entry: DailyPulseEntry) {
        viewModelScope.launch {
            val ok = dailyRepository?.setReaction(entry.id, !entry.likedByMe) == true
            if (ok) refreshDaily()
            else _dailyState.value = _dailyState.value.copy(message = "تعذر تسجيل التفاعل.")
        }
    }

    fun deleteMyEntry() {
        val id = _dailyState.value.myEntryId ?: return
        viewModelScope.launch {
            val ok = dailyRepository?.deleteEntry(id) == true
            if (ok) refreshDaily()
            else _dailyState.value = _dailyState.value.copy(message = "تعذر حذف المشاركة.")
        }
    }

    val snapshot: StateFlow<PulseSnapshot> = dailyState
        .map { state -> computePulseStats(state.entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PulseSnapshot())

    /**
     * Pulse analytics are computed only from rows returned by pulse_entries and
     * pulse_reactions. The worlds list stays empty until worlds are cloud-backed and
     * Pulse entries can be linked to a real world id; never substitute local feed counts.
     */
    private fun computePulseStats(entries: List<DailyPulseEntry>): PulseSnapshot {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val tagScores = mutableMapOf<String, Pair<Int, Int>>()
        entries.forEach { entry ->
            hashtagsOf(entry.body).distinct().forEach { raw ->
                val tag = raw.trim().lowercase()
                if (tag.isNotBlank()) {
                    val current = tagScores[tag] ?: (0 to 0)
                    tagScores[tag] = (current.first + 1) to (current.second + entry.reactions)
                }
            }
        }

        val creators = entries.groupBy { it.userId }.map { (userId, authored) ->
            val first = authored.first()
            PulseCreator(
                user = UserSummary(
                    id = userId,
                    username = first.username,
                    displayName = first.displayName,
                    avatarUri = first.avatarUrl,
                    accountOrigin = AccountOrigin.REMOTE_ACCOUNT
                ),
                posts = authored.size,
                engagement = authored.sumOf { it.reactions }
            )
        }.sortedWith(
            compareByDescending<PulseCreator> { it.engagement }
                .thenByDescending { it.posts }
        ).take(5)

        return PulseSnapshot(
            totalPosts = entries.size,
            activeToday = entries.count { entry ->
                runCatching { Instant.parse(entry.createdAt).toEpochMilli() >= startOfDay }.getOrDefault(false)
            },
            tags = tagScores.map { (name, score) -> PulseTag(name, score.first, score.second) }
                .sortedWith(compareByDescending<PulseTag> { it.engagement }.thenByDescending { it.posts })
                .take(12),
            creators = creators,
            worlds = emptyList()
        )
    }

    private fun hashtagsOf(text: String): List<String> =
        Regex("#([\\p{L}\\p{N}_]{2,30})")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                PulseViewModel(
                    postRepository = container.postRepository,
                    communityRepository = container.communityRepository,
                    dailyRepository = container.dailyPulseRepository
                )
            }
        }
    }
}
