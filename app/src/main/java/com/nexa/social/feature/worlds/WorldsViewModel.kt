package com.nexa.social.feature.worlds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.domain.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorldsUiState(
    val popular: List<Community> = emptyList(),
    val newWorlds: List<Community> = emptyList(),
    val suggested: List<Community> = emptyList(),
    val isEmpty: Boolean = false
)

class WorldsViewModel(
    private val communityRepository: CommunityRepository
) : ViewModel() {

    private val _category = MutableStateFlow<CommunityCategory?>(null)
    val category: StateFlow<CommunityCategory?> = _category.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val uiState: StateFlow<WorldsUiState> =
        combine(communityRepository.observeCommunities(), _category, _search) { communities, category, search ->
            val filtered = communities.filter { community ->
                val matchesCategory = category == null || community.category == category
                val matchesSearch = search.isBlank() ||
                    community.name.contains(search, ignoreCase = true) ||
                    community.description.contains(search, ignoreCase = true)
                matchesCategory && matchesSearch
            }
            val popular = filtered.sortedByDescending { it.sampleMemberCount }.take(4)
            val popularIds = popular.map { it.id }.toSet()
            val newWorlds = filtered.filter { it.isNew && it.id !in popularIds }.take(4)
            val shownIds = popularIds + newWorlds.map { it.id }
            WorldsUiState(
                popular = popular,
                newWorlds = newWorlds,
                suggested = filtered.filterNot { it.id in shownIds }.take(4),
                isEmpty = filtered.isEmpty()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorldsUiState())

    fun setCategory(value: CommunityCategory?) {
        _category.value = value
    }

    fun setSearch(value: String) {
        _search.value = value
    }

    fun toggleMembership(communityId: String) {
        viewModelScope.launch { communityRepository.toggleMembership(communityId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorldsViewModel(nexaApplication().container.communityRepository)
            }
        }
    }
}
