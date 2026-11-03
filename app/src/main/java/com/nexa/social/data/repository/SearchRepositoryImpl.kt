package com.nexa.social.data.repository

import com.nexa.social.data.local.SearchLocalDataSource
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.CommunityRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.SearchRepository
import com.nexa.social.domain.repository.SpaceRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * People and posts come from Supabase when a remote session is active; with a local or guest
 * session they come from local sample data. Worlds and spaces remain local in both modes.
 */
class SearchRepositoryImpl(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val communityRepository: CommunityRepository,
    private val spaceRepository: SpaceRepository,
    private val local: SearchLocalDataSource
) : SearchRepository {

    private val _history = MutableStateFlow(local.readHistory())

    override suspend fun searchPeople(query: String): List<User> = userRepository.searchUsers(query)

    override suspend fun searchPosts(query: String): List<Post> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return postRepository.observeFeed().first().filter { post ->
            post.text.lowercase().contains(q) ||
                post.tags.any { it.lowercase().contains(q) } ||
                post.author.displayName.lowercase().contains(q) ||
                post.author.username.lowercase().contains(q)
        }
    }

    override suspend fun searchWorlds(query: String): List<Community> =
        communityRepository.searchCommunities(query)

    override suspend fun searchSpaces(query: String): List<Space> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return spaceRepository.observeAllSpaces().first().filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    override suspend fun searchTags(query: String): List<String> {
        val q = query.trim().trimStart('#').lowercase()
        if (q.isBlank()) return emptyList()
        return postRepository.observeFeed().first()
            .flatMap { it.tags }
            .distinct()
            .filter { it.lowercase().contains(q) }
            .take(20)
    }

    override val history: Flow<List<String>> = _history.asStateFlow()

    override suspend fun addHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        _history.value = (listOf(trimmed) + _history.value.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(10)
        persist()
    }

    override suspend fun clearHistory() {
        _history.value = emptyList()
        persist()
    }

    private suspend fun persist() {
        val snapshot = _history.value
        withContext(Dispatchers.IO) { local.writeHistory(snapshot) }
    }
}
