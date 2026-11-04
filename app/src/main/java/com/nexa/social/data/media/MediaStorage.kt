package com.nexa.social.data.media

import com.nexa.social.core.common.Resource

/** Supabase Storage buckets used by the app. */
enum class MediaBucket(val id: String) {
    POSTS("post-media"),
    STORIES("story-media"),
    PROFILES("profile-media"),
    GROUPS("group-media"),
    MESSAGES("message-media"),
    CLIPS("clips-media")
}

data class StoredMedia(
    val path: String,
    val publicUrl: String?,
    val mimeType: String,
    val displayName: String?,
    val sizeBytes: Long
)

/**
 * Storage boundary consumed by the repositories. It hides whether the bytes live in Supabase
 * Storage, so the profile record only ever stores a URL or an object path.
 */
interface MediaStorage {
    suspend fun uploadImage(
        localUri: String,
        bucket: MediaBucket,
        path: String,
        onProgress: (Float) -> Unit = {}
    ): Resource<String>

    suspend fun uploadAttachment(
        localUri: String,
        path: String
    ): Resource<StoredMedia>

    suspend fun uploadVideo(localUri: String, path: String): Resource<String> =
        Resource.Failure(com.nexa.social.core.common.FailureReason.NOT_CONFIGURED)

    suspend fun deleteByPublicUrl(url: String?)

    suspend fun deleteObject(bucket: MediaBucket, path: String)

    suspend fun signedUrl(
        bucket: MediaBucket,
        path: String,
        expiresInSeconds: Long = 3_600L
    ): Resource<String>
}
