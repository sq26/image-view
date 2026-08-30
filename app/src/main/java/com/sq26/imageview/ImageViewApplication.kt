package com.sq26.imageview

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.sq26.imageview.coil.ZipEntryFetcher
import com.sq26.imageview.coil.ZipEntryKeyer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ImageViewApplication : Application(), SingletonImageLoader.Factory{
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512 * 1024 * 1024)
                    .build()
            }
            .components {
                add(AnimatedImageDecoder.Factory())
                add(ZipEntryKeyer())
                add(ZipEntryFetcher.Factory())
            }
            .build()
    }
}