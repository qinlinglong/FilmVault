package com.filmvault.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filmvault.app.data.model.DetailMeta
import com.filmvault.app.data.model.HistoryItem
import com.filmvault.app.data.model.MovieItem
import com.filmvault.app.data.model.Resources
import com.filmvault.app.data.repository.FilmRepository
import com.filmvault.app.di.AppModule
import com.filmvault.app.util.FavEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job

private val repo: FilmRepository get() = AppModule.repository

/** 登录 */
class AuthViewModel : ViewModel() {
    var siteUrl by mutableStateOf(AppModule.siteSettings.siteUrlNow)
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loggedIn by mutableStateOf(AppModule.repository.isLoggedIn())

    fun login(onSuccess: () -> Unit) {
        if (siteUrl.isBlank() || email.isBlank() || password.isBlank()) {
            error = "请填写站点地址、账号和密码"
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                AppModule.siteSettings.setSite(siteUrl)
                val ok = repo.login(email.trim(), password)
                if (ok) {
                    loggedIn = true
                    onSuccess()
                } else {
                    error = "登录失败，请检查账号密码"
                }
            } catch (e: Exception) {
                error = "网络错误：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        repo.logout()
        loggedIn = false
    }
}

/** 网页首页专用数据：一次读取首页 HTML 中的三个区块，确保与网页排序一致。 */
class HomeViewModel : ViewModel() {
    var sections by mutableStateOf<Map<String, List<MovieItem>>>(emptyMap())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun load() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                sections = repo.getHome()
                if (sections.isEmpty()) error = "首页数据暂时不可用"
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally { isLoading = false }
        }
    }
}

/** 分类/搜索列表 */
class CatalogViewModel(private val dir: String, private val isSearch: Boolean = false) : ViewModel() {
    var page by mutableStateOf(1)
    var totalPages by mutableStateOf(1)
    var items by mutableStateOf<List<MovieItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searchQuery by mutableStateOf("")
    var searchType by mutableStateOf("")
    var searchMode by mutableStateOf("1")
    val filters = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /** 搜索页不应在空关键词时自动请求第一页。 */
    val isSearchMode: Boolean get() = isSearch

    fun load(reset: Boolean = false): Job {
        if (isLoading) return Job().also { it.complete() }
        return viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val result = if (isSearch) {
                    repo.search(searchQuery.trim(), page, searchType, searchMode)
                } else if (!filters["q"].isNullOrBlank()) {
                    // 分类页的搜索框对应网页搜索接口；/res/{dir}?q= 在站点端会被忽略。
                    val type = when (dir) { "mv" -> "1"; "tv" -> "2"; "ac" -> "3"; else -> "" }
                    repo.search(filters["q"].orEmpty(), page, type, "1")
                } else {
                    repo.getList(dir, page, filters.toMap())
                }
                totalPages = result.second
                items = if (reset) result.first else items + result.first
                if (items.isEmpty()) error = "暂无数据，请点击重试"
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun refresh(): Job {
        page = 1
        return load(reset = true)
    }

    fun nextPage() {
        if (page < totalPages && !isLoading) {
            page++
            load()
        }
    }

    fun setFilter(key: String, value: String) {
        if (value.isBlank()) filters.remove(key) else filters[key] = value
        refresh()
    }
}

/** 网页端独立热门排行。 */
class HotViewModel(private val dir: String) : ViewModel() {
    var items by mutableStateOf<List<MovieItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var period by mutableStateOf("day")

    fun load() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                // 热门 HTML 页面可能触发站点二次安全验证，不能让页面无限转圈。
                // 超时后立即使用同目录的接口排行作为可用降级数据。
                val hot = withTimeoutOrNull(12_000) { repo.getHot(dir, period) }.orEmpty()
                // 热门页是网页独立接口；部分站点节点不开放 HTML 时，用普通排行接口保证页面仍可用。
                items = hot.ifEmpty {
                    repo.getList(dir, 1, mapOf("sort" to "number")).first
                }
                if (items.isEmpty()) error = "热门数据暂时不可用"
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun updatePeriod(value: String) {
        period = value
        load()
    }
}

/** 详情 + 资源 */
class DetailViewModel(private val dir: String, private val id: String, private val title: String) : ViewModel() {
    var meta by mutableStateOf<DetailMeta?>(null)
    var resources by mutableStateOf<Resources?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var isFavorite by mutableStateOf(false)

    init {
        viewModelScope.launch { isFavorite = repo.isFavorite(id, dir) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val (m, r) = repo.getDetail(dir, id, title)
                meta = m
                resources = r
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleFavorite() {
        val current = meta ?: return
        val item = MovieItem(
            id = id, dir = dir, title = current.title, year = current.year,
            rating = null, imdb = null, quality = emptyList(), status = null,
        )
        viewModelScope.launch {
            if (isFavorite) {
                repo.delFavorite(item)
                isFavorite = false
            } else {
                repo.addFavorite(item)
                isFavorite = true
            }
        }
    }
}

/** 收藏 + 历史 */
class LibraryViewModel : ViewModel() {
    val favorites: StateFlow<List<FavEntry>> = repo.favorites.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    var history by mutableStateOf<List<HistoryItem>>(emptyList())
    var isLoading by mutableStateOf(false)

    fun loadHistory() {
        viewModelScope.launch {
            isLoading = true
            try {
                history = repo.getHistory()
            } catch (_: Exception) {
                history = emptyList()
            } finally {
                isLoading = false
            }
        }
    }
}

/** 站点地址设置：配置 / 切换站点（网站常换域名） */
class SettingsViewModel : ViewModel() {
    private val settings = AppModule.siteSettings

    var input by mutableStateOf(settings.siteUrlNow)
        private set

    val currentUrl: StateFlow<String> = settings.siteUrlFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), settings.siteUrlNow
    )
    val savedSites: StateFlow<List<String>> = settings.savedSitesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun updateInput(v: String) { input = v }

    /** 保存当前输入为站点地址并切换过去。 */
    fun save() {
        viewModelScope.launch { settings.setSite(input) }
    }

    /** 一键切换到某个已保存的地址。 */
    fun switchTo(url: String) {
        viewModelScope.launch {
            settings.setSite(url)
            input = url
        }
    }

    /** 从历史事件列表中移除某个地址。 */
    fun remove(url: String) {
        viewModelScope.launch { settings.removeSite(url) }
    }
}
