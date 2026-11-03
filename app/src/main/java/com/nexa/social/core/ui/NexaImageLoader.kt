package com.nexa.social.core.ui

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Shared Coil configuration used by every screen.
 *
 * Avatars and post media are fetched from remote storage, so a bounded disk cache is what keeps
 * the feed usable on slow connections and avoids re-downloading the same images when scrolling
 * back. The cache lives in the app cache directory so the system can reclaim it under pressure.
 */
fun buildImageLoader(context: Context): ImageLoader {
    val appContext = context.applicationContext
    return ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder(appContext)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(appContext.cacheDir, "image_cache"))
                .maxSizeBytes(128L * 1024L * 1024L)
                .build()
        }
        .crossfade(180)
        .respectCacheHeaders(false)
        .build()
}
