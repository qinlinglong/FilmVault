package com.filmvault.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import com.filmvault.app.di.AppModule

class MyApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.30).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("filmvault_images"))
                .maxSizeBytes(128L * 1024L * 1024L)
                .build()
        }
        .okHttpClient {
            OkHttpClient.Builder()
                .dispatcher(Dispatcher().apply {
                    // 海报来自同一个 CDN；提高同域并发，避免首页多行海报排队。
                    maxRequests = 24
                    maxRequestsPerHost = 12
                })
                .build()
        }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()
}
