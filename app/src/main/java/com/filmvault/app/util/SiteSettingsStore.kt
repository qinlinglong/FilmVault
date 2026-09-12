package com.filmvault.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "filmvault_site")

/**
 * 站点地址配置（DataStore 持久化）。
 * 网站常换域名，这里让用户随时配置 / 切换站点地址，并保留历史地址以便一键切换。
 *
 * - [siteUrlNow] 为内存缓存，供网络层在非挂起上下文中读取当前站点地址。
 * - [siteUrlFlow] / [savedSitesFlow] 供 UI 订阅。
 */
class SiteSettingsStore(private val context: Context) {

    private val KEY_SITE = stringPreferencesKey("site_url")
    private val KEY_SAVED = stringPreferencesKey("saved_sites")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前站点地址（内存缓存，默认留空，不内置任何资源站点） */
    @Volatile
    var siteUrlNow: String = Constants.DEFAULT_BASE_URL
        private set

    val siteUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SITE]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_BASE_URL
    }

    val savedSitesFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SAVED]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    init {
        // 用 flow 持续同步内存缓存
        scope.launch { siteUrlFlow.collect { siteUrlNow = it } }
    }

    /** 读取当前站点地址（挂起，读最新持久化值） */
    suspend fun getSiteUrl(): String =
        context.dataStore.data.first()[KEY_SITE]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_BASE_URL

    /** 保存并切换站点地址：写入当前地址，并提到历史列表首位。 */
    suspend fun setSite(raw: String) {
        val url = normalize(raw)
        context.dataStore.edit { prefs ->
            prefs[KEY_SITE] = url
            val list = (prefs[KEY_SAVED]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList())
                .toMutableList()
            list.remove(url)
            list.add(0, url)
            while (list.size > 12) list.removeAt(list.lastIndex)
            prefs[KEY_SAVED] = list.joinToString("\n")
        }
    }

    /** 从历史事件列表中移除某个地址。 */
    suspend fun removeSite(raw: String) {
        val url = normalize(raw)
        context.dataStore.edit { prefs ->
            val list = (prefs[KEY_SAVED]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList())
                .toMutableList()
            list.remove(url)
            prefs[KEY_SAVED] = list.joinToString("\n")
        }
    }

    companion object {
        /** 规范化用户输入：补 https、去尾部斜杠。 */
        fun normalize(raw: String): String {
            var u = raw.trim()
            if (u.isEmpty()) return Constants.DEFAULT_BASE_URL
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            u = u.trimEnd('/')
            return u
        }
    }
}
