package com.nexa.social.data.remote

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.media.StoredMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Uploads media to Supabase Storage.
 *
 * Only images are accepted for posts, stories and profiles. The private message bucket also
 * accepts short videos, audio and PDF files. The class validates type and size before opening the
 * stream and deletes the object when the owning content is deleted.
 */
class SupabaseStorageDataSource(
    context: Context,
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?
) : MediaStorage {

    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val cacheDir: File = context.applicationContext.cacheDir

    private data class FileInfo(
        val mimeType: String,
        val size: Long,
        val displayName: String?
    )

    override suspend fun uploadImage(
        localUri: String,
        bucket: MediaBucket,
        path: String,
        onProgress: (Float) -> Unit
    ): Resource<String> {
        val info = readFileInfo(localUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (bucket == MediaBucket.MESSAGES) return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (!info.mimeType.startsWith("image/")) return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        val maxSourceBytes = if (bucket == MediaBucket.PROFILES) {
            MAX_PROFILE_SOURCE_BYTES
        } else {
            MAX_IMAGE_BYTES
        }
        if (info.size <= 0L || info.size > maxSourceBytes) {
            return Resource.Failure(FailureReason.FILE_TOO_LARGE)
        }
        var sourceUri = localUri
        var sourceInfo = info
        var optimizedFile: File? = null
        if (bucket == MediaBucket.PROFILES) {
            optimizedFile = withContext(Dispatchers.IO) { optimizeProfileImage(localUri) }
            if (optimizedFile != null) {
                val optimizedUri = Uri.fromFile(optimizedFile).toString()
                readFileInfo(optimizedUri)?.let {
                    sourceUri = optimizedUri
                    sourceInfo = it
                }
            }
        }
        return try {
            when (val result = upload(sourceUri, bucket, path, sourceInfo, onProgress)) {
                is Resource.Success -> {
                    val publicUrl = config.normalizedUrl +
                        "/storage/v1/object/public/${bucket.id}/${encodePath(path)}"
                    Resource.Success(publicUrl)
                }
                is Resource.Failure -> result
                Resource.Loading -> Resource.Loading
            }
        } finally {
            optimizedFile?.delete()
        }
    }

    override suspend fun uploadVideo(localUri: String, path: String): Resource<String> {
        val info = readFileInfo(localUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (!info.mimeType.startsWith("video/")) return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (info.size <= 0L || info.size > MAX_CLIP_BYTES) return Resource.Failure(FailureReason.FILE_TOO_LARGE)
        return when (val result = upload(localUri, MediaBucket.CLIPS, path, info, {})) {
            is Resource.Success -> Resource.Success(config.normalizedUrl + "/storage/v1/object/public/${MediaBucket.CLIPS.id}/${encodePath(path)}")
            is Resource.Failure -> result
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun uploadAttachment(
        localUri: String,
        path: String
    ): Resource<StoredMedia> {
        val info = readFileInfo(localUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        val allowed = info.mimeType.startsWith("image/") ||
            info.mimeType.startsWith("video/") ||
            info.mimeType.startsWith("audio/") ||
            info.mimeType == "application/pdf"
        if (!allowed) return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (info.size <= 0L || info.size > MAX_ATTACHMENT_BYTES) {
            return Resource.Failure(FailureReason.FILE_TOO_LARGE)
        }
        return when (val result = upload(localUri, MediaBucket.MESSAGES, path, info, {})) {
            is Resource.Success -> Resource.Success(
                StoredMedia(
                    path = path,
                    publicUrl = null,
                    mimeType = info.mimeType,
                    displayName = info.displayName,
                    sizeBytes = info.size
                )
            )
            is Resource.Failure -> result
            Resource.Loading -> Resource.Loading
        }
    }

    private suspend fun upload(
        localUri: String,
        bucket: MediaBucket,
        path: String,
        info: FileInfo,
        onProgress: (Float) -> Unit
    ): Resource<Unit> {
        val token = accessToken() ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val uri = Uri.parse(localUri)
        val body = streamingBody(uri, info, onProgress)
            ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        val url = "${config.storageUrl}/object/${bucket.id}/${encodePath(path)}"
        val request = http.authenticatedRequest(url, token)
            .header("x-upsert", "false")
            .post(body)
            .build()
        val response = http.execute(request).getOrElse { error ->
            return Resource.Failure(http.transportFailure(error))
        }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        onProgress(1f)
        return Resource.Success(Unit)
    }

    override suspend fun deleteByPublicUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val marker = "/storage/v1/object/public/"
        val index = url.indexOf(marker)
        if (index <= 0) return
        val remainder = url.substring(index + marker.length)
        val bucketId = remainder.substringBefore('/', missingDelimiterValue = "")
        val path = remainder.substringAfter('/', missingDelimiterValue = "")
        val bucket = MediaBucket.entries.firstOrNull { it.id == bucketId } ?: return
        if (path.isBlank()) return
        deleteObject(bucket, path)
    }

    override suspend fun deleteObject(bucket: MediaBucket, path: String) {
        if (path.isBlank()) return
        val token = accessToken() ?: return
        val url = "${config.storageUrl}/object/${bucket.id}/${encodePath(path)}"
        val request = http.authenticatedRequest(url, token).delete().build()
        http.execute(request)
    }

    override suspend fun signedUrl(
        bucket: MediaBucket,
        path: String,
        expiresInSeconds: Long
    ): Resource<String> {
        if (path.isBlank()) return Resource.Failure(FailureReason.REQUIRED)
        val token = accessToken() ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val url = "${config.storageUrl}/object/sign/${bucket.id}/${encodePath(path)}"
        val payload = com.google.gson.JsonObject().apply {
            addProperty("expiresIn", expiresInSeconds)
        }
        val request = http.authenticatedRequest(url, token)
            .post(http.jsonBody(payload))
            .build()
        val response = http.execute(request).getOrElse { error ->
            return Resource.Failure(http.transportFailure(error))
        }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        val json = response.jsonOrNull() ?: return Resource.Failure(FailureReason.GENERIC)
        val signed = json.string("signedURL")
            ?: json.string("signedUrl")
            ?: return Resource.Failure(FailureReason.GENERIC)
        return if (signed.startsWith("http")) {
            Resource.Success(signed)
        } else {
            Resource.Success(config.normalizedUrl + "/" + signed.trimStart('/'))
        }
    }

    private fun optimizeProfileImage(localUri: String): File? = runCatching {
        val uri = Uri.parse(localUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_PROFILE_DIMENSION)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null
        val scaled = scaleDown(decoded, MAX_PROFILE_DIMENSION)
        val rotated = rotateByExif(scaled, uri)
        val file = File.createTempFile("nexa-profile-", ".jpg", cacheDir)
        FileOutputStream(file).use { output ->
            rotated.compress(Bitmap.CompressFormat.JPEG, PROFILE_JPEG_QUALITY, output)
        }
        file
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun rotateByExif(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return bitmap
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private suspend fun readFileInfo(localUri: String): FileInfo? {
        if (localUri.isBlank()) return null
        val uri = Uri.parse(localUri)
        return withContext(Dispatchers.IO) {
            val mime = resolver.getType(uri) ?: guessMimeFromPath(uri.lastPathSegment)
            if (mime == null) {
                null
            } else {
                val size = when (uri.scheme) {
                    ContentResolver.SCHEME_FILE -> File(uri.path.orEmpty()).length()
                    else -> querySize(uri)
                }
                FileInfo(
                    mimeType = mime,
                    size = size,
                    displayName = queryDisplayName(uri) ?: uri.lastPathSegment
                )
            }
        }
    }

    private fun streamingBody(
        uri: Uri,
        info: FileInfo,
        onProgress: (Float) -> Unit
    ): RequestBody? {
        val mediaType = info.mimeType.toMediaTypeOrNull() ?: return null
        return object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = info.size

            override fun writeTo(sink: BufferedSink) {
                val stream = resolver.openInputStream(uri)
                    ?: throw IOException("Cannot open $uri")
                stream.use { input ->
                    input.source().use { source ->
                        val buffer = Buffer()
                        var written = 0L
                        while (true) {
                            val read = source.read(buffer, BUFFER_SIZE)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            written += read
                            if (info.size > 0L) {
                                onProgress((written.toFloat() / info.size.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            } else {
                0L
            }
        } ?: 0L
    }.getOrDefault(0L)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    private fun guessMimeFromPath(path: String?): String? {
        val extension = path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            else -> null
        }
    }

    fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "audio/mp4" -> "m4a"
        "audio/mpeg" -> "mp3"
        "application/pdf" -> "pdf"
        else -> "bin"
    }

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment -> Uri.encode(segment) }

    companion object {
        const val MAX_IMAGE_BYTES: Long = 10L * 1024L * 1024L
        private const val MAX_CLIP_BYTES: Long = 500L * 1024L * 1024L
        const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024L * 1024L
        private const val MAX_PROFILE_SOURCE_BYTES: Long = 25L * 1024L * 1024L
        private const val MAX_PROFILE_DIMENSION = 1440
        private const val PROFILE_JPEG_QUALITY = 88
        private const val BUFFER_SIZE = 8 * 1024L
    }
}
