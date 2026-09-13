package com.filmvault.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "filmvault_fav")

/**
 * 本地收藏夹（DataStore）。与服务器收藏接口双向同步：
 * 服务器负责跨设备持久化，本地缓存保证离线可读与即时渲染。
 */
class FavoritesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = stringPreferencesKey("fav_list")

    private suspend fun current(): List<FavEntry> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY]?.let { json.decodeFromString<List<FavEntry>>(it) } ?: emptyList()
    }

    val favorites: Flow<List<FavEntry>>
        get() = context.dataStore.data.map { prefs ->
            prefs[KEY]?.let { json.decodeFromString<List<FavEntry>>(it) } ?: emptyList()
        }

    suspend fun add(entry: FavEntry) {
        context.dataStore.edit { prefs ->
            val list = prefs[KEY]?.let { json.decodeFromString<List<FavEntry>>(it) } ?: emptyList()
            if (list.none { it.id == entry.id && it.dir == entry.dir }) {
                prefs[KEY] = json.encodeToString(list + entry)
            }
        }
    }

    suspend fun remove(id: String, dir: String) {
        context.dataStore.edit { prefs ->
            val list = prefs[KEY]?.let { json.decodeFromString<List<FavEntry>>(it) }
                ?.filterNot { it.id == id && it.dir == dir }
                ?: emptyList()
            prefs[KEY] = json.encodeToString(list)
        }
    }

    suspend fun contains(id: String, dir: String): Boolean =
        current().any { it.id == id && it.dir == dir }
}

@kotlinx.serialization.Serializable
data class FavEntry(
    val id: String,
    val dir: String,
    val title: String,
    val year: Int? = null,
    val rating: Double? = null,
    val quality: List<String> = emptyList(),
)
