package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val privacy: String = "public",
    val category: String? = null,
    val avatarUri: String? = null,
    val coverUri: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdId: String? = null,
)

class CreateGroupViewModel(private val repo: GroupRepository) : ViewModel() {

    private val _state = MutableStateFlow(CreateGroupUiState())
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    // The group is created before its images are uploaded, so we keep the id and
    // the already-uploaded urls around to make retries idempotent.
    private var createdGroupId: String? = null
    private var uploadedAvatarUrl: String? = null
    private var uploadedCoverUrl: String? = null

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onDescription(value: String) = _state.update { it.copy(description = value) }
    fun onPrivacy(value: String) = _state.update { it.copy(privacy = value) }
    fun onCategory(value: String?) = _state.update {
        it.copy(category = value?.trim()?.takeIf(String::isNotEmpty))
    }
    fun onAvatarPicked(uri: String?) = _state.update { it.copy(avatarUri = uri) }
    fun onCoverPicked(uri: String?) = _state.update { it.copy(coverUri = uri) }

    fun submit() {
        val current = _state.value
        if (current.isSaving || current.name.trim().length < 2) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val groupId = createdGroupId ?: when (
                val result = repo.createGroup(
                    name = current.name,
                    description = current.description,
                    privacy = current.privacy,
                    category = current.category,
                )
            ) {
                is Resource.Success -> result.data.also { createdGroupId = it }
                is Resource.Failure -> {
                    _state.update { it.copy(isSaving = false, error = "create_failed") }
                    return@launch
                }
                Resource.Loading -> return@launch
            }

            var uploadFailed = false

            if (uploadedAvatarUrl == null) {
                current.avatarUri?.let { uri ->
                    when (val upload = repo.uploadGroupImage(groupId, uri, cover = false)) {
                        is Resource.Success -> uploadedAvatarUrl = upload.data
                        is Resource.Failure -> uploadFailed = true
                        Resource.Loading -> Unit
                    }
                }
            }
            if (uploadedCoverUrl == null) {
                current.coverUri?.let { uri ->
                    when (val upload = repo.uploadGroupImage(groupId, uri, cover = true)) {
                        is Resource.Success -> uploadedCoverUrl = upload.data
                        is Resource.Failure -> uploadFailed = true
                        Resource.Loading -> Unit
                    }
                }
            }

            var saveFailed = false
            if (uploadedAvatarUrl != null || uploadedCoverUrl != null) {
                val updated = repo.updateGroup(
                    groupId = groupId,
                    name = current.name.trim(),
                    description = current.description,
                    rules = null,
                    category = current.category,
                    privacy = current.privacy,
                    avatarUrl = uploadedAvatarUrl,
                    coverUrl = uploadedCoverUrl,
                    allowMemberPosts = null,
                    allowMemberInvites = null,
                    requireJoinApproval = null,
                )
                saveFailed = updated is Resource.Failure
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    createdId = if (uploadFailed || saveFailed) null else groupId,
                    error = when {
                        uploadFailed -> "upload_failed"
                        saveFailed -> "create_failed"
                        else -> null
                    },
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CreateGroupViewModel(nexaApplication().container.groupRepository) }
        }
    }
}
