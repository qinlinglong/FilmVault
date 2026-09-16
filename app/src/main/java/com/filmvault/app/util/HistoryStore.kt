package com.filmvault.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.filmvault.app.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "filmvault_history")

/** 本地观看历史，保证原生播放器播放直链时也能立即留下记录。 */
class HistoryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("history_list")

    val history: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<List<HistoryItem>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun add(item: HistoryItem) {
        context.dataStore.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<List<HistoryItem>>(it) }.getOrNull() }
                ?: emptyList()
            val current = item.copy(watchedAt = item.watchedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
            val list = (listOf(current) + old.filterNot {
                it.id == item.id && it.dir == item.dir && it.episode == item.episode
            }).take(100)
            prefs[key] = json.encodeToString(list)
        }
    }

    /** 合并服务端历史，保留本地真实点击时间，避免同步时把顺序全部改成同步时间。 */
    suspend fun mergeRemote(remote: List<HistoryItem>) {
        if (remote.isEmpty()) return
        context.dataStore.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<List<HistoryItem>>(it) }.getOrNull() }
                ?: emptyList()
            val oldByKey = old.associateBy { historyKey(it) }
            val now = System.currentTimeMillis()
            val synced = remote.mapIndexed { index, item ->
                val previous = oldByKey[historyKey(item)]
                item.copy(
                    posterUrl = item.posterUrl ?: previous?.posterUrl,
                    watchedAt = previous?.watchedAt?.takeIf { it > 0L } ?: (now - index),
                )
            }
            val remoteKeys = synced.mapTo(mutableSetOf()) { historyKey(it) }
            val localOnly = old.filterNot { historyKey(it) in remoteKeys }
            prefs[key] = json.encodeToString((synced + localOnly).sortedByDescending { it.watchedAt }.take(100))
        }
    }

    suspend fun current(): List<HistoryItem> = history.first()

    private fun historyKey(item: HistoryItem): String = "${item.dir}/${item.id}/${item.episode ?: 0}"
}
