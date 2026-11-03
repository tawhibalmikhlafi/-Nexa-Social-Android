package com.nexa.social.domain.usecase

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostCreationException
import com.nexa.social.domain.repository.PostRepository

/** Validates content before creating a local post. */
class CreatePostUseCase(
    private val postRepository: PostRepository
) {
    suspend operator fun invoke(
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: Audience,
        communityId: String? = null
    ): Resource<Post> {
        if (text.isBlank() && imageUris.isEmpty()) {
            return Resource.Failure(FailureReason.REQUIRED)
        }
        val post = try {
            postRepository.createPost(
                text = text,
                imageUris = imageUris,
                tags = tags,
                audience = audience,
                communityId = communityId
            )
        } catch (error: PostCreationException) {
            return Resource.Failure(error.reason)
        }
        return Resource.Success(post)
    }
}
