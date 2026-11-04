package com.nexa.social.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.core.supabase.AccessTokenProvider
import com.nexa.social.core.supabase.CurrentUidProvider
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.SupabaseHttp.HttpResponse
import com.nexa.social.core.supabase.boolean
import com.nexa.social.core.supabase.string
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupComment
import com.nexa.social.domain.model.GroupInvite
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupMemberStatus
import com.nexa.social.domain.model.GroupPost
import com.nexa.social.domain.model.GroupPrivacy
import com.nexa.social.domain.model.GroupRole
import com.nexa.social.domain.model.GroupSearchResult
import com.nexa.social.domain.model.GroupStats
import com.nexa.social.domain.model.JoinRequest
import com.nexa.social.domain.model.JoinRequestStatus
import com.nexa.social.domain.model.ReactionKind
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

class GroupRepositoryImpl(
    private val http: SupabaseHttp,
    private val accessToken: AccessTokenProvider,
    private val currentUid: CurrentUidProvider,
    private val mediaStorage: MediaStorage? = null,
    private val jsonStore: JsonFileStore? = null,
) : GroupRepository {

    private val base get() = http.config.restUrl
    private val _myGroups = MutableStateFlow<List<Group>>(emptyList())
    private val reactionOutboxLock = kotlinx.coroutines.sync.Mutex()
    private val reactionOutbox = MutableStateFlow(jsonStore?.readList<PendingGroupReaction>("group_reaction_outbox.json").orEmpty())
    private val commentOutboxLock = kotlinx.coroutines.sync.Mutex()
    private val commentOutbox = MutableStateFlow(jsonStore?.readList<PendingGroupComment>("group_comment_outbox.json").orEmpty())
    private val postOutboxLock = kotlinx.coroutines.sync.Mutex()
    private val postOutbox = MutableStateFlow(jsonStore?.readList<PendingGroupPost>("group_post_outbox.json").orEmpty())

    override fun observeMyGroups(): Flow<List<Group>> = _myGroups.asStateFlow()

    // ── Groups ─────────────────────────────────────────────────────────────
    override suspend fun refreshMyGroups(): Resource<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid.uid() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val url = "$base/group_members?user_id=eq.${encode(uid)}&status=eq.active" +
            "&select=role,status,group:groups(*)&order=joined_at.desc"
        when (val result = executeJsonArray(http.authenticatedRequest(url, token).get().build())) {
            is Resource.Success -> {
                drainReactionOutbox(uid, token)
                drainPostOutbox(uid, token)
                drainCommentOutbox(uid, token)
                _myGroups.value = result.data.mapNotNull { element ->
                    val row = element.asJsonObject
                    val group = row.getAsJsonObject("group") ?: return@mapNotNull null
                    parseGroup(group, row.string("role"), row.string("status"))
                }
                Resource.Success(Unit)
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun searchGroups(
        query: String,
        category: String?,
    ): Resource<List<GroupSearchResult>> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply {
            addProperty("p_query", query.trim())
            if (category != null) addProperty("p_category", category)
            addProperty("p_limit", 30)
        }
        val request = http.authenticatedRequest("$base/rpc/search_groups", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = executeJsonArray(request)) {
            is Resource.Success -> Resource.Success(result.data.map { element ->
                val row = element.asJsonObject
                GroupSearchResult(
                    id = row.string("id").orEmpty(),
                    name = row.string("name").orEmpty(),
                    description = row.string("description").orEmpty(),
                    avatarUrl = row.string("avatar_url"),
                    coverUrl = row.string("cover_url"),
                    privacy = GroupPrivacy.fromWire(row.string("privacy")),
                    memberCount = row.intOrZero("member_count"),
                    category = row.string("category"),
                    createdAt = parseMillis(row.string("created_at")),
                )
            })
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun getGroup(groupId: String): Resource<Group> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val uid = currentUid.uid()
        val groupRequest = http.authenticatedRequest(
            "$base/groups?id=eq.${encode(groupId)}&select=*&limit=1",
            token,
        ).get().build()
        val group = when (val result = executeJson(groupRequest)) {
            is Resource.Success -> result.data ?: return@withContext Resource.Failure(FailureReason.NOT_CONFIGURED)
            is Resource.Failure -> return@withContext Resource.Failure(result.reason)
            Resource.Loading -> return@withContext Resource.Failure(FailureReason.GENERIC)
        }

        var role: String? = null
        var status: String? = null
        if (uid != null) {
            val memberRequest = http.authenticatedRequest(
                "$base/group_members?group_id=eq.${encode(groupId)}&user_id=eq.${encode(uid)}" +
                    "&select=role,status&limit=1",
                token,
            ).get().build()
            when (val memberResult = executeJson(memberRequest)) {
                is Resource.Success -> {
                    role = memberResult.data?.string("role")
                    status = memberResult.data?.string("status")
                }
                else -> Unit
            }
            if (status == null) {
                val pendingRequest = http.authenticatedRequest(
                    "$base/group_join_requests?group_id=eq.${encode(groupId)}" +
                        "&user_id=eq.${encode(uid)}&status=eq.pending&select=id&limit=1",
                    token,
                ).get().build()
                when (val pendingResult = executeJsonArray(pendingRequest)) {
                    is Resource.Success -> {
                        if (pendingResult.data.size() > 0) status = "pending"
                    }
                    else -> Unit
                }
            }
        }
        Resource.Success(parseGroup(group, role, status))
    }

    override suspend fun getGroupStats(groupId: String): Resource<GroupStats> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply { addProperty("p_group_id", groupId) }
        val request = http.authenticatedRequest("$base/rpc/group_stats", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = executeJson(request)) {
            is Resource.Success -> {
                val row = result.data ?: return@withContext Resource.Failure(FailureReason.GENERIC)
                Resource.Success(
                    GroupStats(
                        totalMembers = row.intOrZero("total_members"),
                        activeMembers = row.intOrZero("active_members"),
                        pendingMembers = row.intOrZero("pending_members"),
                        bannedMembers = row.intOrZero("banned_members"),
                        totalPosts = row.intOrZero("total_posts"),
                        totalComments = row.intOrZero("total_comments"),
                        postsLast7Days = row.intOrZero("posts_last_7_days"),
                    )
                )
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun createGroup(
        name: String,
        description: String,
        privacy: String,
        category: String?,
    ): Resource<String> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply {
            addProperty("p_name", name.trim())
            addProperty("p_description", description.trim())
            addProperty("p_privacy", privacy)
        }
        val request = http.authenticatedRequest("$base/rpc/create_group", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> {
                val id = runCatching { JsonParser.parseString(result.data.body).asString }.getOrNull()
                if (id.isNullOrBlank()) {
                    Resource.Failure(FailureReason.GENERIC)
                } else {
                    if (!category.isNullOrBlank()) {
                        updateGroup(
                            groupId = id,
                            name = null,
                            description = null,
                            rules = null,
                            category = category,
                            privacy = null,
                            avatarUrl = null,
                            coverUrl = null,
                            allowMemberPosts = null,
                            allowMemberInvites = null,
                            requireJoinApproval = null,
                        )
                    }
                    refreshMyGroups()
                    Resource.Success(id)
                }
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun updateGroup(
        groupId: String,
        name: String?,
        description: String?,
        rules: String?,
        category: String?,
        privacy: String?,
        avatarUrl: String?,
        coverUrl: String?,
        allowMemberPosts: Boolean?,
        allowMemberInvites: Boolean?,
        requireJoinApproval: Boolean?,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply {
            addProperty("p_group_id", groupId)
            name?.let { addProperty("p_name", it) }
            description?.let { addProperty("p_description", it) }
            rules?.let { addProperty("p_rules", it) }
            category?.let { addProperty("p_category", it) }
            privacy?.let { addProperty("p_privacy", it) }
            avatarUrl?.let { addProperty("p_avatar_url", it) }
            coverUrl?.let { addProperty("p_cover_url", it) }
            allowMemberPosts?.let { addProperty("p_allow_member_posts", it) }
            allowMemberInvites?.let { addProperty("p_allow_member_invites", it) }
            requireJoinApproval?.let { addProperty("p_require_join_approval", it) }
        }
        val request = http.authenticatedRequest("$base/rpc/update_group_details", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> {
                refreshMyGroups()
                Resource.Success(Unit)
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun deleteGroup(groupId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply { addProperty("p_group_id", groupId) }
        val request = http.authenticatedRequest("$base/rpc/delete_group", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> {
                refreshMyGroups()
                Resource.Success(Unit)
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun setGroupArchived(groupId: String, archived: Boolean): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val body = JsonObject().apply {
                addProperty("p_group_id", groupId)
                addProperty("p_archived", archived)
            }
            val request = http.authenticatedRequest("$base/rpc/set_group_archived", token)
                .post(http.jsonBody(body))
                .build()
            when (val result = execute(request)) {
                is Resource.Success -> {
                    refreshMyGroups()
                    Resource.Success(Unit)
                }
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    override suspend fun uploadGroupImage(
        groupId: String,
        localUri: String,
        cover: Boolean,
    ): Resource<String> = withContext(Dispatchers.IO) {
        val storage = mediaStorage
            ?: return@withContext Resource.Failure(FailureReason.NOT_CONFIGURED)
        val uid = currentUid.uid()
            ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val kind = if (cover) "cover" else "avatar"
        val path = "$uid/groups/$groupId/$kind-${System.currentTimeMillis()}.jpg"
        storage.uploadImage(
            localUri = localUri,
            bucket = MediaBucket.GROUPS,
            path = path,
        )
    }

    // ── Join / leave / requests ────────────────────────────────────────────
    override suspend fun joinOrRequest(groupId: String, message: String?): Resource<String> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val body = JsonObject().apply {
                addProperty("p_group_id", groupId)
                if (!message.isNullOrBlank()) addProperty("p_message", message.trim())
            }
            val request = http.authenticatedRequest("$base/rpc/request_join_group", token)
                .post(http.jsonBody(body))
                .build()
            when (val result = execute(request)) {
                is Resource.Success -> {
                    val outcome = runCatching {
                        JsonParser.parseString(result.data.body).asString
                    }.getOrElse { result.data.body.trim().trim('"') }
                    refreshMyGroups()
                    Resource.Success(outcome)
                }
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    override suspend fun leaveGroup(groupId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply { addProperty("p_group_id", groupId) }
        val request = http.authenticatedRequest("$base/rpc/leave_group", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> {
                refreshMyGroups()
                Resource.Success(Unit)
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun getJoinRequests(groupId: String): Resource<List<JoinRequest>> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val url = "$base/group_join_requests?group_id=eq.${encode(groupId)}&status=eq.pending" +
                "&select=id,user_id,message,status,created_at," +
                "user:profiles!group_join_requests_user_id_fkey(display_name,username,avatar_url)" +
                "&order=created_at.asc"
            val request = http.authenticatedRequest(url, token).get().build()
            when (val result = executeJsonArray(request)) {
                is Resource.Success -> Resource.Success(result.data.mapNotNull { element ->
                    val row = element.asJsonObject
                    val user = row.getAsJsonObject("user") ?: return@mapNotNull null
                    JoinRequest(
                        id = row.string("id").orEmpty(),
                        groupId = groupId,
                        userId = row.string("user_id").orEmpty(),
                        displayName = user.string("display_name") ?: user.string("username").orEmpty(),
                        username = user.string("username").orEmpty(),
                        avatarUrl = user.string("avatar_url"),
                        message = row.string("message"),
                        status = JoinRequestStatus.fromWire(row.string("status")),
                        createdAt = parseMillis(row.string("created_at")),
                    )
                })
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    override suspend fun approveJoinRequest(requestId: String, approve: Boolean): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val body = JsonObject().apply {
                addProperty("p_request_id", requestId)
                addProperty("p_approve", approve)
            }
            val request = http.authenticatedRequest("$base/rpc/approve_join_request", token)
                .post(http.jsonBody(body))
                .build()
            when (val result = execute(request)) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    // ── Members ────────────────────────────────────────────────────────────
    override suspend fun getMembers(groupId: String): Resource<List<GroupMember>> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val url = "$base/group_members?group_id=eq.${encode(groupId)}" +
                "&select=role,status,joined_at,user:profiles!group_members_user_id_fkey" +
                "(id,display_name,username,avatar_url)&order=role.asc,joined_at.asc"
            val request = http.authenticatedRequest(url, token).get().build()
            when (val result = executeJsonArray(request)) {
                is Resource.Success -> Resource.Success(result.data.mapNotNull { element ->
                    val row = element.asJsonObject
                    val user = row.getAsJsonObject("user") ?: return@mapNotNull null
                    GroupMember(
                        userId = user.string("id").orEmpty(),
                        displayName = user.string("display_name") ?: user.string("username").orEmpty(),
                        username = user.string("username").orEmpty(),
                        avatarUrl = user.string("avatar_url"),
                        role = GroupRole.fromWire(row.string("role")),
                        status = GroupMemberStatus.fromWire(row.string("status")),
                        joinedAt = parseMillis(row.string("joined_at")),
                    )
                })
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    override suspend fun setMemberRole(
        groupId: String,
        userId: String,
        role: String,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply {
            addProperty("p_group_id", groupId)
            addProperty("p_user_id", userId)
            addProperty("p_role", role)
        }
        val request = http.authenticatedRequest("$base/rpc/set_member_role", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun removeMember(
        groupId: String,
        userId: String,
        ban: Boolean,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply {
            addProperty("p_group_id", groupId)
            addProperty("p_user_id", userId)
            addProperty("p_ban", ban)
        }
        val request = http.authenticatedRequest("$base/rpc/remove_member", token)
            .post(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun unbanMember(groupId: String, userId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val body = JsonObject().apply {
                addProperty("p_group_id", groupId)
                addProperty("p_user_id", userId)
            }
            val request = http.authenticatedRequest("$base/rpc/unban_member", token)
                .post(http.jsonBody(body))
                .build()
            when (val result = execute(request)) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    // ── Posts ──────────────────────────────────────────────────────────────
    override suspend fun getGroupPosts(
        groupId: String,
        offset: Int,
        limit: Int,
    ): Resource<List<GroupPost>> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val uid = currentUid.uid()
        val url = "$base/group_posts?group_id=eq.${encode(groupId)}&is_deleted=eq.false" +
            "&select=*,author:profiles!group_posts_author_id_fkey" +
            "(id,display_name,username,avatar_url)" +
            "&order=created_at.desc&offset=$offset&limit=$limit"
        val request = http.authenticatedRequest(url, token).get().build()
        when (val result = executeJsonArray(request)) {
            is Resource.Success -> {
                val rows = result.data
                val reactions = if (uid != null && rows.size() > 0) {
                    fetchMyReactions(
                        uid = uid,
                        postIds = rows.mapNotNull { it.asJsonObject.string("id") },
                        token = token,
                    )
                } else {
                    emptyMap()
                }
                Resource.Success(rows.map { element ->
                    val row = element.asJsonObject
                    val author = row.getAsJsonObject("author")
                    val postId = row.string("id").orEmpty()
                    val authorId = row.string("author_id").orEmpty()
                    GroupPost(
                        id = postId,
                        groupId = row.string("group_id").orEmpty(),
                        authorId = authorId,
                        authorName = author?.string("display_name")
                            ?: author?.string("username")
                            ?: "",
                        authorAvatarUrl = author?.string("avatar_url"),
                        content = row.string("content").orEmpty(),
                        mediaUrl = row.string("media_url"),
                        createdAt = parseMillis(row.string("created_at")),
                        editedAt = parseMillisOrNull(row.string("edited_at")),
                        reactionCount = row.intOrZero("reaction_count"),
                        commentCount = row.intOrZero("comment_count"),
                        myReaction = reactions[postId],
                        isAuthor = uid != null && uid == authorId,
                    )
                })
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    private suspend fun fetchMyReactions(
        uid: String,
        postIds: List<String>,
        token: String,
    ): Map<String, ReactionKind> {
        if (postIds.isEmpty()) return emptyMap()
        val ids = postIds.joinToString(",")
        val url = "$base/group_post_reactions?user_id=eq.${encode(uid)}" +
            "&post_id=in.($ids)&select=post_id,kind"
        val request = http.authenticatedRequest(url, token).get().build()
        return when (val result = executeJsonArray(request)) {
            is Resource.Success -> result.data.mapNotNull { element ->
                val row = element.asJsonObject
                val postId = row.string("post_id") ?: return@mapNotNull null
                val kind = ReactionKind.fromWire(row.string("kind")) ?: return@mapNotNull null
                postId to kind
            }.toMap()
            else -> emptyMap()
        }
    }

    override suspend fun createPost(
        groupId: String,
        content: String,
        mediaUrl: String?,
    ): Resource<String> = withContext(Dispatchers.IO) {
        val uid = currentUid.uid() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        if (content.isBlank()) return@withContext Resource.Failure(FailureReason.REQUIRED)
        if (!mediaUrl.isNullOrBlank()) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NETWORK)
            val id = java.util.UUID.randomUUID().toString()
            return@withContext if (sendGroupPost(uid, id, groupId, content, mediaUrl, token)) Resource.Success(id) else Resource.Failure(FailureReason.NETWORK)
        }
        val id = java.util.UUID.randomUUID().toString()
        val token = accessToken.token()
        if (token != null && sendGroupPost(uid, id, groupId, content, null, token)) {
            Resource.Success(id)
        } else {
            queueGroupPost(PendingGroupPost(id, uid, groupId, content.trim(), System.currentTimeMillis()))
            Resource.Success(id)
        }
    }

    private suspend fun sendGroupPost(uid: String, id: String, groupId: String, content: String, mediaUrl: String?, token: String): Boolean {
        val body = JsonObject().apply {
            addProperty("id", id)
            addProperty("group_id", groupId)
            addProperty("author_id", uid)
            addProperty("content", content.trim())
            if (!mediaUrl.isNullOrBlank()) addProperty("media_url", mediaUrl)
        }
        val request = http.authenticatedRequest("$base/group_posts", token)
            .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(http.jsonBody(body)).build()
        return execute(request) is Resource.Success
    }

    private suspend fun queueGroupPost(item: PendingGroupPost) {
        postOutboxLock.lock()
        try {
            val next = postOutbox.value.filterNot { it.id == item.id } + item
            postOutbox.value = next
            withContext(Dispatchers.IO) { jsonStore?.writeList("group_post_outbox.json", next) }
        } finally { postOutboxLock.unlock() }
    }

    private suspend fun removeGroupPost(id: String) {
        postOutboxLock.lock()
        try {
            val next = postOutbox.value.filterNot { it.id == id }
            postOutbox.value = next
            withContext(Dispatchers.IO) { jsonStore?.writeList("group_post_outbox.json", next) }
        } finally { postOutboxLock.unlock() }
    }

    private suspend fun drainPostOutbox(uid: String, token: String) {
        for (item in postOutbox.value.filter { it.userId == uid }) {
            if (!sendGroupPost(uid, item.id, item.groupId, item.content, null, token)) break
            removeGroupPost(item.id)
        }
    }
    override suspend fun deletePost(postId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply { addProperty("is_deleted", true) }
        val request = http.authenticatedRequest("$base/group_posts?id=eq.${encode(postId)}", token)
            .addHeader("Prefer", "return=minimal")
            .patch(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun reactToPost(postId: String, kind: ReactionKind?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid.uid() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val token = accessToken.token()
            if (token != null && sendGroupReaction(uid, postId, kind, token)) {
                removeQueuedReaction(uid, postId)
                return@withContext Resource.Success(Unit)
            }
            queueGroupReaction(uid, postId, kind)
            Resource.Success(Unit)
        }

    private suspend fun sendGroupReaction(uid: String, postId: String, kind: ReactionKind?, token: String): Boolean {
        val request = if (kind == null) {
            http.authenticatedRequest(
                "$base/group_post_reactions?post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}",
                token,
            ).delete().build()
        } else {
            val body = JsonObject().apply {
                addProperty("post_id", postId)
                addProperty("user_id", uid)
                addProperty("kind", kind.wire)
            }
            http.authenticatedRequest("$base/group_post_reactions", token)
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        }
        return execute(request) is Resource.Success
    }

    private suspend fun queueGroupReaction(uid: String, postId: String, kind: ReactionKind?) {
        reactionOutboxLock.lock()
        try {
            val next = reactionOutbox.value.filterNot { it.userId == uid && it.postId == postId } +
                PendingGroupReaction(uid, postId, kind?.wire, System.currentTimeMillis())
            reactionOutbox.value = next
            withContext(Dispatchers.IO) { jsonStore?.writeList("group_reaction_outbox.json", next) }
        } finally {
            reactionOutboxLock.unlock()
        }
    }

    private suspend fun removeQueuedReaction(uid: String, postId: String) {
        reactionOutboxLock.lock()
        try {
            val next = reactionOutbox.value.filterNot { it.userId == uid && it.postId == postId }
            reactionOutbox.value = next
            withContext(Dispatchers.IO) { jsonStore?.writeList("group_reaction_outbox.json", next) }
        } finally {
            reactionOutboxLock.unlock()
        }
    }

    private suspend fun drainReactionOutbox(uid: String, token: String) {
        for (item in reactionOutbox.value.filter { it.userId == uid }) {
            val kind = ReactionKind.fromWire(item.kind)
            if (item.kind != null && kind == null) {
                removeQueuedReaction(uid, item.postId)
                continue
            }
            if (!sendGroupReaction(uid, item.postId, kind, token)) break
            removeQueuedReaction(uid, item.postId)
        }
    }

    // ── Comments ───────────────────────────────────────────────────────────
    override suspend fun getPostComments(postId: String): Resource<List<GroupComment>> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val uid = currentUid.uid()
            val url = "$base/group_post_comments?post_id=eq.${encode(postId)}&is_deleted=eq.false" +
                "&select=*,author:profiles!group_post_comments_author_id_fkey" +
                "(id,display_name,username,avatar_url)&order=created_at.asc"
            val request = http.authenticatedRequest(url, token).get().build()
            when (val result = executeJsonArray(request)) {
                is Resource.Success -> Resource.Success(result.data.map { element ->
                    val row = element.asJsonObject
                    val author = row.getAsJsonObject("author")
                    val authorId = row.string("author_id").orEmpty()
                    GroupComment(
                        id = row.string("id").orEmpty(),
                        postId = row.string("post_id").orEmpty(),
                        authorId = authorId,
                        authorName = author?.string("display_name")
                            ?: author?.string("username")
                            ?: "",
                        authorAvatarUrl = author?.string("avatar_url"),
                        content = row.string("content").orEmpty(),
                        parentId = row.string("parent_id"),
                        createdAt = parseMillis(row.string("created_at")),
                        editedAt = parseMillisOrNull(row.string("edited_at")),
                        isAuthor = uid != null && uid == authorId,
                    )
                })
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    override suspend fun addComment(
        postId: String,
        content: String,
        parentId: String?,
    ): Resource<String> = withContext(Dispatchers.IO) {
        val uid = currentUid.uid() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        if (content.isBlank()) return@withContext Resource.Failure(FailureReason.REQUIRED)
        val id = java.util.UUID.randomUUID().toString()
        val token = accessToken.token()
        if (token != null && sendGroupComment(uid, id, postId, content, parentId, token)) {
            return@withContext Resource.Success(id)
        }
        queueGroupComment(PendingGroupComment(id, uid, postId, content.trim(), parentId, System.currentTimeMillis()))
        Resource.Success(id)
    }

    private suspend fun sendGroupComment(uid: String, id: String, postId: String, content: String, parentId: String?, token: String): Boolean {
        val body = JsonObject().apply {
            addProperty("id", id)
            addProperty("post_id", postId)
            addProperty("author_id", uid)
            addProperty("content", content.trim())
            if (!parentId.isNullOrBlank()) addProperty("parent_id", parentId)
        }
        val request = http.authenticatedRequest("$base/group_post_comments", token)
            .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(http.jsonBody(body)).build()
        return execute(request) is Resource.Success
    }

    private suspend fun queueGroupComment(item: PendingGroupComment) {
        commentOutboxLock.lock()
        try {
            val next = commentOutbox.value.filterNot { it.id == item.id } + item
            commentOutbox.value = next
            withContext(Dispatchers.IO) { jsonStore?.writeList("group_comment_outbox.json", next) }
        } finally { commentOutboxLock.unlock() }
    }

    private suspend fun removeGroupComment(id: String) {
        commentOutboxLock.lock()
        try {
            val next = commentOutbox.value.filterNot { it.id == id }
            commentOutbox.value = next
            withContext(Dispatchers.IO) { jsonStore?.writeList("group_comment_outbox.json", next) }
        } finally { commentOutboxLock.unlock() }
    }

    private suspend fun drainCommentOutbox(uid: String, token: String) {
        for (item in commentOutbox.value.filter { it.userId == uid }) {
            if (!sendGroupComment(uid, item.id, item.postId, item.content, item.parentId, token)) break
            removeGroupComment(item.id)
        }
    }

    override suspend fun deleteComment(commentId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply { addProperty("is_deleted", true) }
        val request = http.authenticatedRequest("$base/group_post_comments?id=eq.${encode(commentId)}", token)
            .addHeader("Prefer", "return=minimal")
            .patch(http.jsonBody(body))
            .build()
        when (val result = execute(request)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    // ── Invites ────────────────────────────────────────────────────────────
    override suspend fun inviteUser(groupId: String, userId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val body = JsonObject().apply {
                addProperty("p_group_id", groupId)
                addProperty("p_invitee_id", userId)
            }
            val request = http.authenticatedRequest("$base/rpc/invite_to_group", token)
                .post(http.jsonBody(body))
                .build()
            when (val result = execute(request)) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    override suspend fun getMyInvites(): Resource<List<GroupInvite>> = withContext(Dispatchers.IO) {
        val uid = currentUid.uid() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val url = "$base/group_invites?invitee_id=eq.${encode(uid)}&status=eq.pending" +
            "&select=id,group_id,created_at,group:groups(id,name,avatar_url)," +
            "inviter:profiles!group_invites_inviter_id_fkey(id,display_name,username)" +
            "&order=created_at.desc"
        val request = http.authenticatedRequest(url, token).get().build()
        when (val result = executeJsonArray(request)) {
            is Resource.Success -> Resource.Success(result.data.mapNotNull { element ->
                val row = element.asJsonObject
                val group = row.getAsJsonObject("group") ?: return@mapNotNull null
                val inviter = row.getAsJsonObject("inviter")
                GroupInvite(
                    id = row.string("id").orEmpty(),
                    groupId = group.string("id").orEmpty(),
                    groupName = group.string("name").orEmpty(),
                    groupAvatarUrl = group.string("avatar_url"),
                    inviterId = inviter?.string("id").orEmpty(),
                    inviterName = inviter?.string("display_name")
                        ?: inviter?.string("username")
                        ?: "",
                    createdAt = parseMillis(row.string("created_at")),
                )
            })
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }
    }

    override suspend fun respondToInvite(inviteId: String, accept: Boolean): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val token = accessToken.token() ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val body = JsonObject().apply {
                addProperty("p_invite_id", inviteId)
                addProperty("p_accept", accept)
            }
            val request = http.authenticatedRequest("$base/rpc/respond_to_invite", token)
                .post(http.jsonBody(body))
                .build()
            when (val result = execute(request)) {
                is Resource.Success -> {
                    refreshMyGroups()
                    Resource.Success(Unit)
                }
                is Resource.Failure -> Resource.Failure(result.reason)
                Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
            }
        }

    // ── Helpers ────────────────────────────────────────────────────────────
    private suspend fun execute(request: Request): Resource<HttpResponse> =
        http.execute(request).fold(
            onSuccess = { response ->
                if (response.successful) {
                    Resource.Success(response)
                } else {
                    Resource.Failure(http.failureReason(response))
                }
            },
            onFailure = { error -> Resource.Failure(http.transportFailure(error)) },
        )

    private suspend fun executeJsonArray(request: Request): Resource<JsonArray> =
        when (val result = execute(request)) {
            is Resource.Success -> {
                val array = runCatching {
                    JsonParser.parseString(result.data.body).asJsonArray
                }.getOrElse { return Resource.Failure(FailureReason.GENERIC) }
                Resource.Success(array)
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }

    private suspend fun executeJson(request: Request): Resource<JsonObject?> =
        when (val result = execute(request)) {
            is Resource.Success -> {
                val json = runCatching {
                    val element = JsonParser.parseString(result.data.body)
                    when {
                        element.isJsonObject -> element.asJsonObject
                        element.isJsonArray -> element.asJsonArray.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                        else -> null
                    }
                }.getOrNull()
                Resource.Success(json)
            }
            is Resource.Failure -> Resource.Failure(result.reason)
            Resource.Loading -> Resource.Failure(FailureReason.GENERIC)
        }

    private fun parseGroup(row: JsonObject, role: String?, status: String?): Group = Group(
        id = row.string("id").orEmpty(),
        ownerId = row.string("owner_id").orEmpty(),
        name = row.string("name").orEmpty(),
        description = row.string("description").orEmpty(),
        rules = row.string("rules").orEmpty(),
        category = row.string("category"),
        slug = row.string("slug"),
        avatarUrl = row.string("avatar_url"),
        coverUrl = row.string("cover_url"),
        privacy = GroupPrivacy.fromWire(row.string("privacy")),
        memberCount = row.intOrZero("member_count"),
        createdAt = parseMillis(row.string("created_at")),
        allowMemberPosts = row.boolean("allow_member_posts") ?: true,
        allowMemberInvites = row.boolean("allow_member_invites") ?: true,
        requireJoinApproval = row.boolean("require_join_approval") ?: false,
        isArchived = row.boolean("is_archived") ?: false,
        myRole = role?.let { GroupRole.fromWire(it) },
        myStatus = status?.let { GroupMemberStatus.fromWire(it) },
    )

    private fun JsonObject.intOrZero(name: String): Int =
        runCatching { get(name)?.asInt }.getOrNull() ?: 0

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun parseMillis(value: String?): Long = parseMillisOrNull(value) ?: 0L

    private fun parseMillisOrNull(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
    }
}


data class PendingGroupReaction(
    val userId: String,
    val postId: String,
    val kind: String?,
    val createdAt: Long
)


data class PendingGroupComment(
    val id: String,
    val userId: String,
    val postId: String,
    val content: String,
    val parentId: String?,
    val createdAt: Long
)


data class PendingGroupPost(
    val id: String,
    val userId: String,
    val groupId: String,
    val content: String,
    val createdAt: Long
)
