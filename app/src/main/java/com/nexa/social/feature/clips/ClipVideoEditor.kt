package com.nexa.social.feature.clips

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
object ClipVideoEditor {

    fun durationMs(context: Context, uri: String): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    fun extractCover(context: Context, uri: String, positionMs: Long): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            val bitmap = retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val target = File(context.cacheDir, "nexa-cover-${System.nanoTime()}.jpg")
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            Uri.fromFile(target).toString()
        } finally {
            retriever.release()
        }
    }.getOrNull()

    suspend fun export(
        context: Context,
        inputUri: String,
        startMs: Long,
        endMs: Long,
        volume: Float,
        filter: String
    ): String? = suspendCancellableCoroutine { continuation ->
        val output = File(context.cacheDir, "nexa-edited-${System.nanoTime()}.mp4")
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs.coerceAtLeast(0L))
            .setEndPositionMs(endMs.coerceAtLeast(startMs + 1L))
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(inputUri))
            .setClippingConfiguration(clipping)
            .build()
        val audio = if (volume < 0.999f) {
            listOf(GainProcessor(DefaultGainProvider.Builder(volume.coerceIn(0f, 1f)).build()))
        } else emptyList()
        val videoEffects: List<Effect> = when (filter) {
            "grayscale" -> listOf(RgbFilter.createGrayscaleFilter())
            "invert" -> listOf(RgbFilter.createInvertedFilter())
            else -> emptyList()
        }
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audio, videoEffects))
            .build()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Uri.fromFile(output).toString())
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    output.delete()
                    if (continuation.isActive) continuation.resume(null)
                }
            })
            .build()
        continuation.invokeOnCancellation { runCatching { transformer.cancel() }; output.delete() }
        runCatching { transformer.start(edited, output.absolutePath) }
            .onFailure {
                output.delete()
                if (continuation.isActive) continuation.resume(null)
            }
    }
}
