package com.nexa.social.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.R
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.CommentRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import com.nexa.social.domain.usecase.CreatePostUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class EditProfileViewModel(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val createPost: CreatePostUseCase
) : ViewModel() {

    val user: StateFlow<User?> = userRepository.currentUser
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** 0..100 while the new avatar/cover is uploading. */
    val uploadProgress: StateFlow<Int> = userRepository.uploadProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun save(
        displayName: String,
        username: String,
        bio: String,
        avatarUri: String?,
        coverUri: String?,
        shareCoverToProfile: Boolean = false,
        coverCaption: String = "",
        placeOfOrigin: String = "",
        currentResidence: String = "",
        birthDate: String? = null,
        skills: List<String> = emptyList(),
        hobbies: List<String> = emptyList()
    ) {
        if (_saving.value) return

        val current = user.value
        if (current == null) {
            _error.value = R.string.edit_profile_error_not_ready
            return
        }

        if (displayName.isBlank()) {
            _error.value = R.string.edit_profile_error_display_name
            return
        }
        val canonicalUsername = com.nexa.social.core.common.Validators.canonicalUsername(username)
        if (!com.nexa.social.core.common.Validators.isValidUsername(canonicalUsername)) {
            _error.value = R.string.error_username_invalid
            return
        }
        if (com.nexa.social.core.common.Validators.isReservedUsername(canonicalUsername)) {
            _error.value = R.string.error_username_reserved
            return
        }
        if (!birthDate.isNullOrBlank()) {
            val parsedBirthDate = runCatching { LocalDate.parse(birthDate) }.getOrNull()
            if (parsedBirthDate == null || parsedBirthDate.isAfter(LocalDate.now())) {
                _error.value = R.string.edit_profile_error_unexpected
                return
            }
        }

        viewModelScope.launch {
            _saving.value = true
            _saved.value = false
            _error.value = null

            try {
                when (val result = userRepository.updateProfile(
                    userId = current.id,
                    displayName = displayName.trim(),
                    bio = bio.trim(),
                    avatarUri = avatarUri,
                    coverUri = coverUri,
                    city = current.city,
                    website = current.website,
                    username = canonicalUsername,
                    placeOfOrigin = placeOfOrigin,
                    currentResidence = currentResidence,
                    birthDate = birthDate,
                    skills = skills,
                    hobbies = hobbies
                )) {
                    is Resource.Success -> {
                        // The cover is saved first. Publishing is an explicit opt-in and
                        // reuses the same selected image as a normal profile post.
                        if (shareCoverToProfile && !coverUri.isNullOrBlank()) {
                            when (val publishResult = createPost(
                                text = coverCaption.trim(),
                                imageUris = listOf(coverUri),
                                tags = emptyList(),
                                audience = Audience.PUBLIC_LOCAL
                            )) {
                                is Resource.Success -> Unit
                                is Resource.Failure -> {
                                    _error.value = failureMessageRes(publishResult.reason)
                                    return@launch
                                }
                                Resource.Loading -> Unit
                            }
                        }
                        _saved.value = true
                        val summary = UserSummary.from(result.data)
                        launch {
                            runCatching {
                                postRepository.refreshAuthorSnapshot(summary)
                                commentRepository.refreshAuthorSnapshot(summary)
                            }
                        }
                    }
                    is Resource.Failure -> _error.value = failureMessageRes(result.reason)
                    Resource.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = R.string.edit_profile_error_unexpected
            } finally {
                _saving.value = false
            }
        }
    }

    fun consumeSaved() {
        _saved.value = false
    }

    fun consumeError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                EditProfileViewModel(
                    container.userRepository,
                    container.postRepository,
                    container.commentRepository,
                    container.createPost
                )
            }
        }
    }
}
