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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
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
        val maxSourceBytes = when (bucket) {
            MediaBucket.PROFILES -> MAX_PROFILE_SOURCE_BYTES
            MediaBucket.PULSE -> 5L * 1024L * 1024L
            else -> MAX_IMAGE_BYTES
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
            when (val result = upload(sourceUri, bucket, path, sourceInfo, onProgress, upsert = bucket == MediaBucket.CLIP_COVERS)) {
                is Resource.Success -> {
                    val publicUrl = config.normalizedUrl +
                        "/storage/v1/object/public/${bucket.id}/${encodePath(path)}"
                    uploadedUrl(bucket, path, publicUrl)
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
        return when (val result = upload(localUri, MediaBucket.CLIPS, path, info, {}, upsert = true)) {
            is Resource.Success -> uploadedUrl(MediaBucket.CLIPS, path, config.normalizedUrl + "/storage/v1/object/public/${MediaBucket.CLIPS.id}/${encodePath(path)}")
            is Resource.Failure -> result
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun uploadVideoToBucket(
        localUri: String,
        bucket: MediaBucket,
        path: String,
        maxBytes: Long
    ): Resource<String> {
        val info = readFileInfo(localUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (!info.mimeType.startsWith("video/")) return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (info.size <= 0L || info.size > maxBytes) return Resource.Failure(FailureReason.FILE_TOO_LARGE)
        return when (val result = upload(localUri, bucket, path, info, {}, upsert = false)) {
            is Resource.Success -> uploadedUrl(bucket, path, config.normalizedUrl + "/storage/v1/object/public/${bucket.id}/${encodePath(path)}")
            is Resource.Failure -> result
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun uploadVideoResumable(
        localUri: String,
        path: String,
        uploadUrl: String?,
        offset: Long,
        onCheckpoint: suspend (String?, Long) -> Unit
    ): Resource<String> {
        val info = readFileInfo(localUri) ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (!info.mimeType.startsWith("video/")) return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        if (info.size <= 0L || info.size > MAX_CLIP_BYTES) return Resource.Failure(FailureReason.FILE_TOO_LARGE)
        val token = accessToken() ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val host = Uri.parse(config.normalizedUrl).host ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        val projectRef = host.substringBefore(".supabase.co")
        val endpoint = if (host.endsWith(".supabase.co")) {
            "https://" + projectRef + ".storage.supabase.co/storage/v1/upload/resumable"
        } else {
            config.storageUrl + "/upload/resumable"
        }
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
            val commonHeaders = mapOf(
                "Authorization" to "Bearer " + token,
                "apikey" to config.anonKey,
                "Tus-Resumable" to "1.0.0"
            )
            var currentUrl = uploadUrl
            var currentOffset = offset.coerceIn(0L, info.size)

            if (!currentUrl.isNullOrBlank()) {
                val head = Request.Builder().url(currentUrl).head().apply {
                    commonHeaders.forEach { (key, value) -> header(key, value) }
                }.build()
                val headResult = runCatching { client.newCall(head).execute() }.getOrNull()
                if (headResult != null) {
                    headResult.use { response ->
                        if (response.isSuccessful) {
                            currentOffset = response.header("Upload-Offset")?.toLongOrNull() ?: currentOffset
                        } else if (response.code == 404 || response.code == 410) {
                            currentUrl = null
                            currentOffset = 0L
                        } else {
                            return@withContext Resource.Failure(if (response.code == 401 || response.code == 403) FailureReason.NOT_AUTHENTICATED else FailureReason.NETWORK)
                        }
                    }
                } else {
                    return@withContext Resource.Failure(FailureReason.NETWORK)
                }
            }

            if (currentUrl.isNullOrBlank()) {
                val metadata = listOf(
                    "bucketName" to MediaBucket.CLIPS.id,
                    "objectName" to path,
                    "contentType" to info.mimeType,
                    "cacheControl" to "3600"
                ).joinToString(",") { (key, value) ->
                    key + " " + Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
                }
                val create = Request.Builder().url(endpoint)
                    .post(ByteArray(0).toRequestBody(null))
                    .header("Upload-Length", info.size.toString())
                    .header("Upload-Metadata", metadata)
                    .header("x-upsert", "true")
                    .apply { commonHeaders.forEach { (key, value) -> header(key, value) } }
                    .build()
                val created = runCatching { client.newCall(create).execute() }.getOrNull()
                    ?: return@withContext Resource.Failure(FailureReason.NETWORK)
                created.use { response ->
                    if (response.code != 201) {
                        return@withContext Resource.Failure(
                            if (response.code == 401 || response.code == 403) FailureReason.NOT_AUTHENTICATED
                            else if (response.code == 413) FailureReason.FILE_TOO_LARGE
                            else if (response.code >= 500 || response.code == 408) FailureReason.NETWORK
                            else FailureReason.UPLOAD_FAILED
                        )
                    }
                    val location = response.header("Location")
                        ?: return@withContext Resource.Failure(FailureReason.UPLOAD_FAILED)
                    currentUrl = java.net.URI(endpoint).resolve(location).toString()
                    currentOffset = response.header("Upload-Offset")?.toLongOrNull() ?: 0L
                    onCheckpoint(currentUrl, currentOffset)
                }
            }

            val chunkSize = 6 * 1024 * 1024
            while (currentOffset < info.size) {
                val length = minOf(chunkSize.toLong(), info.size - currentOffset).toInt()
                val bytes = runCatching {
                    resolver.openInputStream(Uri.parse(localUri))?.use { input ->
                        var skipped = 0L
                        while (skipped < currentOffset) {
                            val count = input.skip(currentOffset - skipped)
                            if (count <= 0L) {
                                if (input.read() == -1) break else skipped++
                            } else skipped += count
                        }
                        val chunk = ByteArray(length)
                        var read = 0
                        while (read < length) {
                            val count = input.read(chunk, read, length - read)
                            if (count < 0) break
                            read += count
                        }
                        if (read == length) chunk else chunk.copyOf(read)
                    }
                }.getOrNull() ?: return@withContext Resource.Failure(FailureReason.UNSUPPORTED_FILE)
                if (bytes.isEmpty()) return@withContext Resource.Failure(FailureReason.UPLOAD_FAILED)
                val patch = Request.Builder().url(currentUrl!!)
                    .patch(bytes.toRequestBody("application/offset+octet-stream".toMediaType()))
                    .header("Upload-Offset", currentOffset.toString())
                    .apply { commonHeaders.forEach { (key, value) -> header(key, value) } }
                    .build()
                val result = runCatching { client.newCall(patch).execute() }.getOrNull()
                    ?: return@withContext Resource.Failure(FailureReason.NETWORK)
                result.use { response ->
                    if (response.code == 204) {
                        currentOffset = response.header("Upload-Offset")?.toLongOrNull()
                            ?: (currentOffset + bytes.size)
                        onCheckpoint(currentUrl, currentOffset)
                    } else if (response.code == 409) {
                        val head = Request.Builder().url(currentUrl!!).head()
                            .apply { commonHeaders.forEach { (key, value) -> header(key, value) } }.build()
                        val refreshed = runCatching { client.newCall(head).execute() }.getOrNull()
                            ?: return@withContext Resource.Failure(FailureReason.NETWORK)
                        refreshed.use { headResponse ->
                            if (!headResponse.isSuccessful) return@withContext Resource.Failure(FailureReason.NETWORK)
                            currentOffset = headResponse.header("Upload-Offset")?.toLongOrNull() ?: currentOffset
                            onCheckpoint(currentUrl, currentOffset)
                        }
                    } else {
                        return@withContext Resource.Failure(
                            if (response.code == 401 || response.code == 403) FailureReason.NOT_AUTHENTICATED
                            else if (response.code == 413) FailureReason.FILE_TOO_LARGE
                            else if (response.code >= 500 || response.code == 408) FailureReason.NETWORK
                            else FailureReason.UPLOAD_FAILED
                        )
                    }
                }
            }
            onCheckpoint(null, 0L)
            uploadedUrl(MediaBucket.CLIPS, path, config.normalizedUrl + "/storage/v1/object/public/" + MediaBucket.CLIPS.id + "/" + encodePath(path))
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
        onProgress: (Float) -> Unit,
        upsert: Boolean = false
    ): Resource<Unit> {
        val token = accessToken() ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val uri = Uri.parse(localUri)
        val body = streamingBody(uri, info, onProgress)
            ?: return Resource.Failure(FailureReason.UNSUPPORTED_FILE)
        val url = "${config.storageUrl}/object/${bucket.id}/${encodePath(path)}"
        val request = http.authenticatedRequest(url, token)
            .header("x-upsert", upsert.toString())
            .post(body)
            .build()
        val response = http.execute(request).getOrElse { error ->
            return Resource.Failure(http.transportFailure(error))
        }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        onProgress(1f)
        return Resource.Success(Unit)
    }

    private suspend fun uploadedUrl(bucket: MediaBucket, path: String, publicUrl: String): Resource<String> {
        if (bucket !in PRIVATE_MEDIA_BUCKETS) return Resource.Success(publicUrl)
        return signedUrl(bucket, path)
    }

    override suspend fun deleteByPublicUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val markers = listOf("/storage/v1/object/public/", "/storage/v1/object/sign/")
        val marker = markers.firstOrNull { url.contains(it) } ?: return
        val remainder = url.substringAfter(marker).substringBefore('?')
        val bucketId = remainder.substringBefore('/', missingDelimiterValue = "")
        val path = remainder.substringAfter('/', missingDelimiterValue = "")
        val bucket = MediaBucket.entries.firstOrNull { it.id == bucketId } ?: return
        if (path.isBlank()) return
        deleteObject(bucket, java.net.URLDecoder.decode(path, "UTF-8"))
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
            Resource.Success(config.storageUrl + "/" + signed.trimStart('/'))
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
            val displayName = queryDisplayName(uri) ?: uri.lastPathSegment
            val providerMime = resolver.getType(uri)?.lowercase()?.takeUnless {
                it == "application/octet-stream" || it == "binary/octet-stream"
            }
            val mime = providerMime
                ?: guessMimeFromPath(displayName)
                ?: guessMimeFromPath(uri.lastPathSegment)
                ?: sniffMimeType(uri)
            if (mime.isNullOrBlank()) {
                null
            } else {
                val size = when (uri.scheme) {
                    ContentResolver.SCHEME_FILE -> File(uri.path.orEmpty()).length()
                    else -> querySize(uri)
                }
                FileInfo(
                    mimeType = mime,
                    size = size,
                    displayName = displayName
                )
            }
        }
    }

    /**
     * Some Android photo providers return a content:// URI without a MIME type or extension.
     * Inspect only the file signature as a final fallback so valid photos are not rejected.
     */
    private fun sniffMimeType(uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(16)
            var count = 0
            while (count < header.size) {
                val read = input.read(header, count, header.size - count)
                if (read <= 0) break
                count += read
            }
            fun ascii(offset: Int, value: String): Boolean {
                val bytes = value.toByteArray(Charsets.US_ASCII)
                return offset + bytes.size <= count &&
                    bytes.indices.all { header[offset + it] == bytes[it] }
            }
            when {
                count >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "image/jpeg"
                count >= 8 && header[0] == 0x89.toByte() && ascii(1, "PNG") && header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() && header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte() -> "image/png"
                ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
                ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
                ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "heif") || ascii(8, "mif1")) -> "image/heic"
                ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")) -> "image/avif"
                else -> null
            }
        }
    }.getOrNull()

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
            "avif" -> "image/avif"
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
        "image/avif" -> "avif"
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
        private val PRIVATE_MEDIA_BUCKETS = setOf(
            MediaBucket.STORIES,
            MediaBucket.CLIPS,
            MediaBucket.CLIP_COVERS
        )
        const val MAX_IMAGE_BYTES: Long = 10L * 1024L * 1024L
        private const val MAX_CLIP_BYTES: Long = 500L * 1024L * 1024L
        const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024L * 1024L
        private const val MAX_PROFILE_SOURCE_BYTES: Long = 25L * 1024L * 1024L
        private const val MAX_PROFILE_DIMENSION = 1440
        private const val PROFILE_JPEG_QUALITY = 88
        private const val BUFFER_SIZE = 8 * 1024L
    }
}
