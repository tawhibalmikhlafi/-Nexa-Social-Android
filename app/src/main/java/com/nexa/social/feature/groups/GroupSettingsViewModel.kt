package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupSettingsUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isLeaving: Boolean = false,
    val isArchiving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val deleted: Boolean = false,
    val left: Boolean = false,
)

class GroupSettingsViewModel(
    private val repo: GroupRepository,
    private val groupId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupSettingsUiState())
    val state: StateFlow<GroupSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val result = repo.getGroup(groupId)) {
            is Resource.Success -> _state.update {
                it.copy(group = result.data, isLoading = false)
            }
            is Resource.Failure -> _state.update {
                it.copy(isLoading = false, error = "load_failed")
            }
            Resource.Loading -> _state.update {
                it.copy(isLoading = false, error = "load_failed")
            }
        }
    }

    fun save(
        name: String,
        description: String,
        rules: String,
        category: String?,
        privacy: String,
        avatarUrl: String?,
        coverUrl: String?,
        allowMemberPosts: Boolean,
        allowMemberInvites: Boolean,
        requireJoinApproval: Boolean,
        avatarLocalUri: String? = null,
        coverLocalUri: String? = null,
    ) = viewModelScope.launch {
        if (_state.value.isSaving || _state.value.isDeleting || _state.value.isArchiving) {
            return@launch
        }
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            var resolvedAvatar = avatarUrl?.trim()?.takeIf(String::isNotEmpty)
            var resolvedCover = coverUrl?.trim()?.takeIf(String::isNotEmpty)

            if (avatarLocalUri != null) {
                when (val upload = repo.uploadGroupImage(groupId, avatarLocalUri, cover = false)) {
                    is Resource.Success -> resolvedAvatar = upload.data
                    is Resource.Failure -> {
                        _state.update { it.copy(error = "upload_failed") }
                        return@launch
                    }
                    Resource.Loading -> Unit
                }
            }
            if (coverLocalUri != null) {
                when (val upload = repo.uploadGroupImage(groupId, coverLocalUri, cover = true)) {
                    is Resource.Success -> resolvedCover = upload.data
                    is Resource.Failure -> {
                        _state.update { it.copy(error = "upload_failed") }
                        return@launch
                    }
                    Resource.Loading -> Unit
                }
            }

            when (repo.updateGroup(
                groupId = groupId,
                name = name.trim(),
                description = description.trim(),
                rules = rules.trim(),
                category = category?.trim()?.takeIf(String::isNotEmpty),
                privacy = privacy,
                avatarUrl = resolvedAvatar,
                coverUrl = resolvedCover,
                allowMemberPosts = allowMemberPosts,
                allowMemberInvites = allowMemberInvites,
                requireJoinApproval = requireJoinApproval,
            )) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(isSaving = false, successMessage = "saved")
                    }
                    load()
                }
                is Resource.Failure -> _state.update {
                    it.copy(isSaving = false, error = "save_failed")
                }
                Resource.Loading -> _state.update {
                    it.copy(isSaving = false, error = "operation_incomplete")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(error = "save_failed") }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun delete() = viewModelScope.launch {
        if (_state.value.isDeleting || _state.value.isSaving || _state.value.isArchiving) {
            return@launch
        }
        _state.update { it.copy(isDeleting = true, error = null) }
        try {
            when (repo.deleteGroup(groupId)) {
                is Resource.Success -> _state.update {
                    it.copy(isDeleting = false, deleted = true)
                }
                is Resource.Failure -> _state.update {
                    it.copy(isDeleting = false, error = "delete_failed")
                }
                Resource.Loading -> _state.update {
                    it.copy(isDeleting = false, error = "operation_incomplete")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(error = "delete_failed") }
        } finally {
            _state.update { it.copy(isDeleting = false) }
        }
    }

    fun leave() = viewModelScope.launch {
        if (_state.value.isLeaving || _state.value.isSaving ||
            _state.value.isDeleting || _state.value.isArchiving
        ) {
            return@launch
        }
        _state.update { it.copy(isLeaving = true, error = null) }
        try {
            when (repo.leaveGroup(groupId)) {
                is Resource.Success -> _state.update {
                    it.copy(isLeaving = false, left = true, successMessage = "left")
                }
                is Resource.Failure -> _state.update {
                    it.copy(isLeaving = false, error = "leave_failed")
                }
                Resource.Loading -> _state.update {
                    it.copy(isLeaving = false, error = "operation_incomplete")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(error = "leave_failed") }
        } finally {
            _state.update { it.copy(isLeaving = false) }
        }
    }

    fun setArchived(archived: Boolean) = viewModelScope.launch {
        if (_state.value.isArchiving || _state.value.isSaving || _state.value.isDeleting) {
            return@launch
        }
        _state.update { it.copy(isArchiving = true, error = null) }
        try {
            when (repo.setGroupArchived(groupId, archived)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            isArchiving = false,
                            successMessage = if (archived) "archived" else "unarchived",
                        )
                    }
                    load()
                }
                is Resource.Failure -> _state.update {
                    it.copy(isArchiving = false, error = "archive_failed")
                }
                Resource.Loading -> _state.update {
                    it.copy(isArchiving = false, error = "operation_incomplete")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(error = "archive_failed") }
        } finally {
            _state.update { it.copy(isArchiving = false) }
        }
    }

    fun clearTransient() {
        _state.update { it.copy(error = null, successMessage = null) }
    }

    companion object {
        fun factory(groupId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupSettingsViewModel(nexaApplication().container.groupRepository, groupId)
            }
        }
    }
}
