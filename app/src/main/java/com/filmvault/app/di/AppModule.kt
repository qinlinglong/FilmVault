package com.filmvault.app.di

import android.content.Context
import com.filmvault.app.data.remote.ApiClient
import com.filmvault.app.data.repository.FilmRepository
import com.filmvault.app.util.FavoritesStore
import com.filmvault.app.util.HistoryStore
import com.filmvault.app.util.SiteSettingsStore

/** 简易依赖容器：在 Application 中初始化，全局共享。 */
object AppModule {
    lateinit var appContext: Context
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var favoritesStore: FavoritesStore
        private set
    lateinit var historyStore: HistoryStore
        private set
    lateinit var siteSettings: SiteSettingsStore
        private set
    lateinit var repository: FilmRepository
        private set

    fun init(context: Context) {
        if (::apiClient.isInitialized) return
        appContext = context.applicationContext
        siteSettings = SiteSettingsStore(appContext)
        apiClient = ApiClient(appContext, siteSettings)
        favoritesStore = FavoritesStore(appContext)
        historyStore = HistoryStore(appContext)
        repository = FilmRepository(apiClient, favoritesStore, historyStore)
    }
}
