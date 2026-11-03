package com.nexa.social.feature.worlds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.domain.model.WorldPrivacy
import com.nexa.social.domain.repository.CommunityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateWorldViewModel(
    private val communityId: String?,
    private val communityRepository: CommunityRepository
) : ViewModel() {

    val isEditing: Boolean = communityId != null

    val existing: StateFlow<Community?> = if (communityId == null) {
        kotlinx.coroutines.flow.flowOf(null).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    } else {
        communityRepository.observeCommunity(communityId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    fun canEdit(community: Community?): Boolean = community?.createdByMe == true

    fun submit(
        name: String,
        description: String,
        category: CommunityCategory,
        privacy: WorldPrivacy,
        rules: List<String>,
        onResult: (FailureReason?, String?) -> Unit
    ) {
        viewModelScope.launch {
            if (communityId == null) {
                when (val result = communityRepository.createCommunity(name, description, category, privacy, rules)) {
                    is Resource.Success -> onResult(null, result.data.id)
                    is Resource.Failure -> onResult(result.reason, null)
                    Resource.Loading -> Unit
                }
            } else {
                val updated = communityRepository.updateCommunity(communityId, name, description, category, privacy)
                if (updated != null) onResult(null, updated.id) else onResult(FailureReason.GENERIC, null)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = communityId ?: return
        viewModelScope.launch {
            if (communityRepository.deleteCommunity(id)) onDone()
        }
    }

    companion object {
        fun factory(communityId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateWorldViewModel(communityId, nexaApplication().container.communityRepository)
            }
        }
    }
}
