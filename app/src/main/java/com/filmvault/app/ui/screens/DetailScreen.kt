package com.filmvault.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.data.model.MagnetItem
import com.filmvault.app.data.model.HistoryItem
import com.filmvault.app.di.AppModule
import com.filmvault.app.ui.components.PosterImage
import com.filmvault.app.util.Playback
import com.filmvault.app.util.OfflineMediaStore
import com.filmvault.app.viewmodel.DetailViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DetailScreen(nav: NavController, dir: String, id: String, localOnly: Boolean = false) {
    val vm: DetailViewModel = viewModel(key = "$dir/$id") { DetailViewModel(dir, id, "") }
    val meta = vm.meta
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 播放器返回后保留用户所在的资源 Tab，避免先显示详情顶部再跳回在线播放。
    var tab by rememberSaveable { mutableIntStateOf(if (localOnly) 1 else 0) }
    val detailEntry = nav.currentBackStackEntry
    val detailScrollState = rememberScrollState(
        initial = detailEntry?.savedStateHandle?.get<Int>("detail_scroll_y") ?: 0,
    )
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var cacheSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodes by remember { mutableStateOf(setOf<String>()) }
    var cachingEpisodes by remember { mutableStateOf(false) }
    var cacheProgress by remember { mutableFloatStateOf(0f) }
    var cacheDownloaded by remember { mutableStateOf(0L) }
    var cacheTotal by remember { mutableStateOf(0L) }
    var cacheStatusText by remember { mutableStateOf("") }
    var cacheRevision by remember { mutableIntStateOf(0) }
    var cacheJob by remember { mutableStateOf<Job?>(null) }
    var pausedEpisode by remember { mutableStateOf<Pair<com.filmvault.app.data.model.PlayLine, Int>?>(null) }
    var cacheEntries by remember { mutableStateOf(emptyMap<String, OfflineMediaStore.CacheEntry>()) }
    var liveProgress by remember { mutableStateOf(emptyMap<String, Pair<Long, Long>>()) }

    LaunchedEffect(Unit) {
        cacheEntries = OfflineMediaStore.list(context).associateBy { it.cacheKey }
    }
    LaunchedEffect(cachingEpisodes) {
        while (cachingEpisodes) {
            cacheEntries = OfflineMediaStore.list(context).associateBy { it.cacheKey }
            delay(500)
        }
    }

    fun resourceCacheKey(line: com.filmvault.app.data.model.PlayLine, episode: Int) =
        OfflineMediaStore.resourceKey(dir, id, line.id, episode)

    fun cacheSingle(line: com.filmvault.app.data.model.PlayLine, episode: Int) {
        if (cachingEpisodes) return
        cachingEpisodes = true
        cacheProgress = 0f
        cacheDownloaded = 0L
        cacheTotal = 0L
        cacheStatusText = "正在解析并缓存：${meta?.title.orEmpty()} 第${episode}集"
        Toast.makeText(context, cacheStatusText, Toast.LENGTH_SHORT).show()
        pausedEpisode = line to episode
        cacheJob = scope.launch {
            try {
                val directUrl = AppModule.repository.resolvePlayUrl(line.id, episode) ?: error("未解析到播放地址")
                val key = resourceCacheKey(line, episode)
                OfflineMediaStore.download(
                    context,
                    directUrl,
                    "${AppModule.siteSettings.siteUrlNow}/py/${line.id}/$episode",
                    "${meta?.title.orEmpty()} · ${line.name} · 第${episode}集",
                    key,
                    "detail/$dir/$id",
                ) { downloaded, total ->
                    liveProgress = liveProgress + (key to (downloaded to total))
                    cacheDownloaded = downloaded
                    cacheTotal = total
                    cacheProgress = if (total > 0L) downloaded.toFloat() / total else 0f
                }
                cacheRevision++
                cacheEntries = OfflineMediaStore.list(context).associateBy { it.cacheKey }
                cacheProgress = 1f
                cacheStatusText = "缓存完成：第${episode}集"
                Toast.makeText(context, "已缓存当前资源，可在本地播放", Toast.LENGTH_SHORT).show()
            } catch (_: CancellationException) {
                cacheStatusText = "已暂停，可继续缓存第${episode}集"
            } catch (error: Throwable) {
                cacheStatusText = "缓存失败：${error.message ?: "网络错误"}"
                Toast.makeText(context, cacheStatusText, Toast.LENGTH_LONG).show()
            } finally {
                cachingEpisodes = false
                cacheJob = null
            }
        }
    }

    LaunchedEffect(detailScrollState, detailEntry) {
        snapshotFlow { detailScrollState.value }.collect { scrollY ->
            detailEntry?.savedStateHandle?.set("detail_scroll_y", scrollY)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Surface(shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    if (cacheSelectionMode) "已选 ${selectedEpisodes.size} 个缓存项" else meta?.title ?: "详情",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.toggleFavorite() }) {
                    Icon(
                        if (vm.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (vm.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!localOnly && tab == 0) IconButton(
                    onClick = {
                        if (cacheSelectionMode) {
                            if (selectedEpisodes.isEmpty() || cachingEpisodes) return@IconButton
                            val selected = selectedEpisodes.toSet()
                            val lines = vm.resources?.playLines.orEmpty()
                            cachingEpisodes = true
                            cacheProgress = 0f
                            cacheDownloaded = 0L
                            cacheTotal = 0L
                            cacheStatusText = "正在解析并缓存 ${selected.size} 个资源…"
                            Toast.makeText(context, cacheStatusText, Toast.LENGTH_SHORT).show()
                            cacheJob = scope.launch {
                                var success = 0
                                var failed = 0
                                try {
                                    selected.forEachIndexed { selectedIndex, key ->
                                        val parts = key.split("/", limit = 2)
                                        val line = lines.firstOrNull { it.id == parts.getOrNull(0) }
                                        val episode = parts.getOrNull(1)?.toIntOrNull()
                                        if (line == null || episode == null) {
                                            failed++
                                            return@forEachIndexed
                                        }
                                        try {
                                            val directUrl = AppModule.repository.resolvePlayUrl(line.id, episode)
                                                ?: error("未解析到播放地址")
                                            val referer = "${AppModule.siteSettings.siteUrlNow}/py/${line.id}/$episode"
                                            pausedEpisode = line to episode
                                            cacheStatusText = "正在缓存 ${selectedIndex + 1}/${selected.size}：${meta?.title.orEmpty()} 第${episode}集"
                                            OfflineMediaStore.download(
                                                context,
                                                directUrl,
                                                referer,
                                                "${meta?.title.orEmpty()} · ${line.name} · 第${episode}集",
                                                resourceCacheKey(line, episode),
                                                "detail/$dir/$id",
                                            ) { downloaded, total ->
                                                liveProgress = liveProgress + (resourceCacheKey(line, episode) to (downloaded to total))
                                                cacheDownloaded = downloaded
                                                cacheTotal = total
                                                val itemProgress = if (total > 0L) downloaded.toFloat() / total else 0f
                                                cacheProgress = ((selectedIndex + itemProgress) / selected.size).coerceIn(0f, 1f)
                                            }
                                            success++
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Throwable) {
                                            failed++
                                        }
                                    }
                                    cacheProgress = if (failed == 0) 1f else cacheProgress
                                    cacheRevision++
                                    cacheEntries = OfflineMediaStore.list(context).associateBy { it.cacheKey }
                                    cacheStatusText = if (failed == 0) "缓存完成：$success 个资源" else "缓存完成：成功 $success 个，失败 $failed 个"
                                    cacheSelectionMode = false
                                    selectedEpisodes = emptySet()
                                    Toast.makeText(context, cacheStatusText, Toast.LENGTH_LONG).show()
                                } catch (_: CancellationException) {
                                    cacheStatusText = "已暂停，可在详情或缓存管理中继续"
                                } finally {
                                    cachingEpisodes = false
                                    cacheJob = null
                                }
                            }
                        } else {
                            cacheSelectionMode = true
                        }
                    },
                    enabled = !cachingEpisodes,
                ) {
                    Icon(
                        if (cacheSelectionMode && selectedEpisodes.isNotEmpty()) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = if (cacheSelectionMode) "缓存已选资源" else "选择缓存资源",
                    )
                }
            }
        }

        if (vm.isLoading && meta == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        BoxWithConstraints {
          val wide = maxWidth >= 600.dp
          Column(Modifier.verticalScroll(detailScrollState).padding(if (wide) 28.dp else 16.dp)) {
            // 头部：海报 + 信息
            Row(Modifier.fillMaxWidth()) {
                PosterImage(
                    url = meta?.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.width(if (wide) 180.dp else 110.dp).height(if (wide) 260.dp else 160.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    meta?.let { m ->
                        InfoLine("年份", m.year?.toString())
                        InfoLine("地区", m.region.joinToString())
                        InfoLine("语言", m.language.joinToString())
                        InfoLine("类型", m.genres.joinToString())
                        InfoLine("时长", m.duration)
                        InfoLine("上映", m.releaseDate)
                        m.rating?.let { InfoLine("评分", it.toString()) }
                    }
                }
            }

            meta?.summary?.let { sum ->
                Spacer(Modifier.height(12.dp))
                Text("简介", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(sum, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }

            meta?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                Spacer(Modifier.height(10.dp))
                Text("主演", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(cast.joinToString(" / "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // 资源标签
            val res = vm.resources
            val tabTitles = if (localOnly) listOf(
                "本地播放 ${res?.playLines?.sumOf { line -> line.episodes.withIndex().count { (index, _) -> OfflineMediaStore.isCached(context, resourceCacheKey(line, index + 1)) } } ?: 0}",
            ) else listOf(
                "在线播放 ${res?.playLines?.sumOf { it.episodes.size } ?: 0}",
                "本地播放 ${res?.playLines?.sumOf { line -> line.episodes.withIndex().count { (index, _) -> OfflineMediaStore.isCached(context, resourceCacheKey(line, index + 1)) } } ?: 0}",
                "网盘资源 ${res?.clouds?.size ?: 0}",
                "磁力资源 ${res?.magnets?.size ?: 0}",
            )
            TabRow(selectedTabIndex = tab) {
                tabTitles.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            if (i != 0) {
                                cacheSelectionMode = false
                                selectedEpisodes = emptySet()
                            }
                        },
                        text = { Text(t, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (cachingEpisodes || cacheStatusText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(cacheStatusText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (cachingEpisodes) {
                        IconButton(onClick = { cacheJob?.cancel() }) {
                            Icon(Icons.Filled.Pause, contentDescription = "暂停缓存")
                        }
                    } else if (pausedEpisode != null && cacheStatusText.startsWith("已暂停")) {
                        IconButton(onClick = { pausedEpisode?.let { cacheSingle(it.first, it.second) } }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "继续缓存")
                        }
                    }
                }
                if (cachingEpisodes) {
                    LinearProgressIndicator(progress = { cacheProgress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Text("已下载 ${formatCacheBytes(cacheDownloaded)} / ${if (cacheTotal > 0L) formatCacheBytes(cacheTotal) else "计算中"}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                0 -> if (localOnly) LocalPlayList(
                    context = context,
                    lines = vm.resources?.playLines.orEmpty(),
                    cacheRevision = cacheRevision,
                    cacheKey = { line, episode -> resourceCacheKey(line, episode) },
                    onPlay = { line, episode ->
                        val local = OfflineMediaStore.cachedUri(context, "", resourceCacheKey(line, episode))
                        if (local != null) nav.navigate("player/${Uri.encode(local.toString())}?lineId=${Uri.encode(line.id)}&episode=$episode&episodeCount=${line.episodes.size}&lineName=${Uri.encode(line.name)}&resourceTitle=${Uri.encode(meta?.title.orEmpty())}&cacheKey=${Uri.encode(resourceCacheKey(line, episode))}")
                    },
                ) else PlayList(
                    lines = vm.resources?.playLines ?: emptyList(),
                    resolvingKey = resolvingKey,
                    selectionMode = cacheSelectionMode,
                    selectedEpisodes = selectedEpisodes,
                    onToggleSelection = { line, episode ->
                        if (OfflineMediaStore.list(context).any { it.cacheKey == resourceCacheKey(line, episode) }) return@PlayList
                        val key = "${line.id}/$episode"
                        selectedEpisodes = if (key in selectedEpisodes) selectedEpisodes - key else selectedEpisodes + key
                    },
                    onLongCache = { line, episode ->
                        if (!cacheEntries.containsKey(resourceCacheKey(line, episode))) cacheSingle(line, episode)
                    },
                    isCached = { line, episode -> OfflineMediaStore.isCached(context, resourceCacheKey(line, episode)) },
                    cacheInfo = { line, episode ->
                        val key = resourceCacheKey(line, episode)
                        val entry = cacheEntries[key]
                        val progress = liveProgress[key]
                        if (progress != null) progress.first to progress.second else entry?.let { it.downloaded to it.total }
                    },
                ) { line, episode ->
                    val key = "${line.id}/$episode"
                    if (resolvingKey != null) return@PlayList
                    tab = 0
                    detailEntry?.savedStateHandle?.set("detail_scroll_y", detailScrollState.value)
                    resolvingKey = key
                    scope.launch {
                        try {
                            val cacheKey = resourceCacheKey(line, episode)
                            AppModule.repository.recordHistory(
                                HistoryItem(id, dir, meta?.title ?: "未命名影片", episode, meta?.posterUrl),
                            )
                            val localUri = OfflineMediaStore.cachedUri(context, "", cacheKey)
                            if (localUri != null) {
                                nav.navigate(
                                    "player/${Uri.encode(localUri.toString())}" +
                                        "?lineId=${Uri.encode(line.id)}" +
                                        "&episode=$episode" +
                                        "&episodeCount=${line.episodes.size}" +
                                        "&lineName=${Uri.encode(line.name)}" +
                                        "&resourceTitle=${Uri.encode(meta?.title.orEmpty())}" +
                                        "&cacheKey=${Uri.encode(cacheKey)}",
                                )
                            } else {
                                val directUrl = runCatching { AppModule.repository.resolvePlayUrl(line.id, episode) }.getOrNull()
                                if (directUrl != null) {
                                    nav.navigate(
                                        "player/${Uri.encode(directUrl)}" +
                                            "?lineId=${Uri.encode(line.id)}" +
                                            "&episode=$episode" +
                                            "&episodeCount=${line.episodes.size}" +
                                            "&lineName=${Uri.encode(line.name)}" +
                                            "&resourceTitle=${Uri.encode(meta?.title.orEmpty())}" +
                                            "&cacheKey=${Uri.encode(cacheKey)}",
                                    )
                                } else {
                                    Toast.makeText(context, "未解析到直链，已尝试打开在线播放页", Toast.LENGTH_SHORT).show()
                                    Playback.openUrl(context, "${AppModule.siteSettings.siteUrlNow}/py/${line.id}/$episode")
                                }
                            }
                        } finally {
                            resolvingKey = null
                        }
                    }
                }
                1 -> LocalPlayList(
                    context = context,
                    lines = vm.resources?.playLines.orEmpty(),
                    cacheRevision = cacheRevision,
                    cacheKey = { line, episode -> resourceCacheKey(line, episode) },
                    onPlay = { line, episode ->
                        val local = OfflineMediaStore.cachedUri(context, "", resourceCacheKey(line, episode))
                        if (local != null) nav.navigate(
                            "player/${Uri.encode(local.toString())}?lineId=${Uri.encode(line.id)}&episode=$episode&episodeCount=${line.episodes.size}&lineName=${Uri.encode(line.name)}&resourceTitle=${Uri.encode(meta?.title.orEmpty())}&cacheKey=${Uri.encode(resourceCacheKey(line, episode))}",
                        )
                    },
                )
                2 -> CloudList(context, vm.resources?.clouds ?: emptyList())
                3 -> MagnetList(context, vm.resources?.magnets ?: emptyList())
            }
          }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ResourceRow(
    title: String,
    subtitle: String? = null,
    isLoading: Boolean = false,
    selectionEnabled: Boolean = false,
    selected: Boolean = false,
    cached: Boolean = false,
    cacheProgress: Pair<Long, Long>? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionEnabled) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            cacheProgress?.let { (downloaded, total) ->
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        if (total > 0L) "${formatCacheBytes(downloaded)} / ${formatCacheBytes(total)}" else "正在缓存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (total > 0L) LinearProgressIndicator(
                        progress = { (downloaded.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.width(92.dp).padding(top = 3.dp),
                    )
                }
            }
            if (cacheProgress == null && cached) Text("已缓存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (isLoading) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MagnetList(context: android.content.Context, items: List<MagnetItem>) {
    if (items.isEmpty()) EmptyHint("暂无磁力资源")
    items.forEach { m ->
        ResourceRow(
            title = m.fileName,
            subtitle = buildString {
                append("大小 ${m.size}  ")
                append("做种 ${m.seeds}  ")
                m.qualityLabel?.let { append("画质 $it  ") }
                if (m.added.isNotBlank()) append("· ${m.added}")
            },
            onClick = { Playback.openMagnet(context, m.magnetUri) },
        )
    }
}

@Composable
private fun CloudList(context: android.content.Context, items: List<com.filmvault.app.data.model.CloudItem>) {
    if (items.isEmpty()) EmptyHint("暂无网盘资源")
    items.forEach { c ->
        ResourceRow(
            title = c.name,
            subtitle = listOfNotNull(c.category, c.user).joinToString(" · ").ifBlank { null },
            onClick = { Playback.openUrl(context, c.url) },
        )
    }
}

@Composable
private fun PlayList(
    lines: List<com.filmvault.app.data.model.PlayLine>,
    resolvingKey: String?,
    selectionMode: Boolean,
    selectedEpisodes: Set<String>,
    onToggleSelection: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Unit,
    onLongCache: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Unit,
    isCached: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Boolean,
    cacheInfo: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Pair<Long, Long>?,
    onPlay: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Unit,
) {
    if (lines.isEmpty()) EmptyHint("暂无在线播放线路")
    lines.forEach { line ->
        Text(line.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        line.episodes.forEachIndexed { idx, ep ->
            ResourceRow(
                title = if (resolvingKey == "${line.id}/${idx + 1}") "正在解析并准备播放器…" else ep.ifBlank { "第 ${idx + 1} 集" },
                isLoading = resolvingKey == "${line.id}/${idx + 1}",
                selectionEnabled = selectionMode,
                selected = "${line.id}/${idx + 1}" in selectedEpisodes,
                cached = isCached(line, idx + 1),
                cacheProgress = cacheInfo(line, idx + 1),
                onClick = {
                    if (selectionMode) onToggleSelection(line, idx + 1) else onPlay(line, idx + 1)
                },
                onLongClick = { onLongCache(line, idx + 1) },
            )
        }
    }
}

@Composable
private fun LocalPlayList(
    context: android.content.Context,
    lines: List<com.filmvault.app.data.model.PlayLine>,
    cacheRevision: Int,
    cacheKey: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> String,
    onPlay: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") val revision = cacheRevision
    val cachedLines = lines.filter { line -> line.episodes.withIndex().any { (index, _) -> OfflineMediaStore.isCached(context, cacheKey(line, index + 1)) } }
    if (cachedLines.isEmpty()) EmptyHint("暂无本地缓存资源")
    cachedLines.forEach { line ->
        Text(line.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        line.episodes.forEachIndexed { index, episode ->
            if (OfflineMediaStore.isCached(context, cacheKey(line, index + 1))) {
                ResourceRow(title = episode.ifBlank { "本地资源" }, cached = true, onClick = { onPlay(line, index + 1) })
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

private fun formatCacheBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
