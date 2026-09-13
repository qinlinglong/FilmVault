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
            val list = (listOf(item) + old.filterNot {
                it.id == item.id && it.dir == item.dir && it.episode == item.episode
            }).take(100)
            prefs[key] = json.encodeToString(list)
        }
    }

    suspend fun current(): List<HistoryItem> = history.first()
}
