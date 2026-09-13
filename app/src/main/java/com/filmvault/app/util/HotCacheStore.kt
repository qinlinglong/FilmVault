package com.filmvault.app.util

import android.content.Context
import com.filmvault.app.data.model.MovieItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 热门缓存：按仓库、分类和时间范围隔离。 */
class HotCacheStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("filmvault_hot_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(siteHost: String, dir: String, period: String): List<MovieItem> = runCatching {
        val raw = preferences.getString(key(siteHost, dir, period), null) ?: return emptyList()
        json.decodeFromString<List<MovieItem>>(raw)
    }.getOrDefault(emptyList())

    fun write(siteHost: String, dir: String, period: String, items: List<MovieItem>) {
        if (items.isEmpty()) return
        runCatching {
            preferences.edit().putString(key(siteHost, dir, period), json.encodeToString(items)).apply()
        }
    }

    private fun key(siteHost: String, dir: String, period: String): String =
        "hot_${siteHost.lowercase()}_${dir}_$period"
}
