package com.filmvault.app.util

import android.content.Context
import com.filmvault.app.data.model.MovieItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 首页缓存：按仓库域名隔离，避免切换仓库后展示上一个仓库的数据。 */
class HomeCacheStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("filmvault_home_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(siteHost: String): Map<String, List<MovieItem>> = runCatching {
        val raw = preferences.getString(key(siteHost), null) ?: return emptyMap()
        json.decodeFromString<Map<String, List<MovieItem>>>(raw)
    }.getOrDefault(emptyMap())

    fun write(siteHost: String, sections: Map<String, List<MovieItem>>) {
        if (sections.isEmpty()) return
        runCatching {
            preferences.edit().putString(key(siteHost), json.encodeToString(sections)).apply()
        }
    }

    // 封面地址解析规则变化时自动隔离旧缓存，避免继续使用已失效的图片地址。
    private fun key(siteHost: String): String = "home_v2_${siteHost.lowercase()}"
}
