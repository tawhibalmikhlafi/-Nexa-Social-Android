package com.nexa.social.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.SearchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchTab { PEOPLE, POSTS, WORLDS, SPACES, TAGS }

data class SearchResults(
    val people: List<User> = emptyList(),
    val posts: List<Post> = emptyList(),
    val worlds: List<Community> = emptyList(),
    val spaces: List<Space> = emptyList(),
    val tags: List<String> = emptyList()
)

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(SearchTab.PEOPLE)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    val history: StateFlow<List<String>> = searchRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<SearchResults> = combine(
        _query.debounce(100),
        _tab
    ) { query, tab -> query to tab }
        .mapLatest { (query, tab) ->
            if (query.isBlank()) {
                SearchResults()
            } else {
                when (tab) {
                    SearchTab.PEOPLE -> SearchResults(people = searchRepository.searchPeople(query))
                    SearchTab.POSTS -> SearchResults(posts = searchRepository.searchPosts(query))
                    SearchTab.WORLDS -> SearchResults(worlds = searchRepository.searchWorlds(query))
                    SearchTab.SPACES -> SearchResults(spaces = searchRepository.searchSpaces(query))
                    SearchTab.TAGS -> SearchResults(tags = searchRepository.searchTags(query))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun selectTab(value: SearchTab) {
        _tab.value = value
    }

    fun commitHistory() {
        val current = _query.value
        viewModelScope.launch { searchRepository.addHistory(current) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchRepository.clearHistory() }
    }

    fun clear() {
        _query.value = ""
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(nexaApplication().container.searchRepository)
            }
        }
    }
}
