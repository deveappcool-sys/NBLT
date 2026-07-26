package com.nblt.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nblt.tv.data.api.BilibiliApiClient

class NbltApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .callFactory(BilibiliApiClient.httpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(IMAGE_DISK_CACHE_DIRECTORY))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .build()
    }

    private companion object {
        const val IMAGE_MEMORY_CACHE_BYTES = 24 * 1024 * 1024
        const val IMAGE_DISK_CACHE_BYTES = 128L * 1024L * 1024L
        const val IMAGE_DISK_CACHE_DIRECTORY = "coil_image_cache"
    }
}
