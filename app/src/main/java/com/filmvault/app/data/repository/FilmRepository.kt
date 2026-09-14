package com.filmvault.app.data.repository

import com.filmvault.app.data.model.DetailMeta
import com.filmvault.app.data.model.MovieItem
import com.filmvault.app.data.model.LoginResult
import com.filmvault.app.data.model.Resources
import com.filmvault.app.data.remote.ApiClient
import com.filmvault.app.util.FavEntry
import com.filmvault.app.util.FavoritesStore
import com.filmvault.app.util.HistoryStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 业务仓库：组合网络层与本地存储，对外提供干净的用例方法。
 */
class FilmRepository(
    private val api: ApiClient,
    private val favStore: FavoritesStore,
    private val historyStore: HistoryStore,
) {
    suspend fun login(email: String, password: String, captcha: String = ""): LoginResult = api.login(email, password, captcha)
    fun isLoggedIn(): Boolean = api.isLoggedIn()
    fun logout() = api.logout()

    suspend fun getList(dir: String, page: Int, query: Map<String, String>): Pair<List<MovieItem>, Int> =
        api.getList(dir, page, query)

    suspend fun search(q: String, page: Int = 1, type: String = "", mode: String = "1"): Pair<List<MovieItem>, Int> = api.search(q, page, type, mode)

    suspend fun getHot(dir: String, period: String = "day"): List<MovieItem> = api.getHot(dir, period)
    suspend fun getHome(): Map<String, List<MovieItem>> = api.getHome()

    suspend fun resolvePlayUrl(lineId: String, episode: Int): String? =
        api.resolvePlayUrl(lineId, episode)

    suspend fun getDetail(dir: String, id: String, title: String): Pair<DetailMeta, Resources> {
        return coroutineScope {
            val meta = async { api.getDetailMeta(dir, id, title) }
            val res = async { api.getResources(dir, id) }
            meta.await() to res.await()
        }
    }

    suspend fun getHistory(): List<com.filmvault.app.data.model.HistoryItem> = api.getHistory()

    val history: Flow<List<com.filmvault.app.data.model.HistoryItem>> = historyStore.history

    suspend fun recordHistory(item: com.filmvault.app.data.model.HistoryItem) = historyStore.add(item)

    /** 合并服务器历史到本地，服务器暂时不可用时仍保留本地历史。 */
    suspend fun syncHistory() {
        runCatching { getHistory() }.getOrNull()?.forEach { historyStore.add(it) }
    }

    /** 收藏：本地 + 服务器双向写入 */
    suspend fun addFavorite(item: MovieItem) {
        favStore.add(FavEntry(item.id, item.dir, item.title, item.year, item.rating, item.quality, item.posterUrl))
        api.addFavorite(item.dir, item.id)
    }

    suspend fun delFavorite(item: MovieItem) {
        favStore.remove(item.id, item.dir)
        api.delFavorite(item.dir, item.id)
    }

    suspend fun isFavorite(id: String, dir: String): Boolean = favStore.contains(id, dir)

    val favorites: Flow<List<FavEntry>> = favStore.favorites
}
