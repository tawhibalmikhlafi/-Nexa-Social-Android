package com.nexa.social.domain.model

/** Input for account creation. Requested fields are kept separate from the public [User] model. */
data class SignUpRequest(
    val displayName: String,
    val username: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val acceptedTerms: Boolean
)

/** A locally saved post draft. */
data class PostDraft(
    val text: String,
    val imageUri: String?,
    val updatedAt: Long,
    val imageUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val audience: Audience = Audience.PUBLIC_LOCAL
) {
    fun allImages(): List<String> = imageUris.ifEmpty { listOfNotNull(imageUri) }
}
