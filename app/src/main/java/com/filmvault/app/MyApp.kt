package com.filmvault.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.filmvault.app.di.AppModule

class MyApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("filmvault_images"))
                .maxSizeBytes(64L * 1024L * 1024L)
                .build()
        }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()
}
