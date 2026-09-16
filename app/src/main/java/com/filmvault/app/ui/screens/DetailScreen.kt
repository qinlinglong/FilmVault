package com.filmvault.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@Composable
fun DetailScreen(nav: NavController, dir: String, id: String) {
    val vm: DetailViewModel = viewModel(key = "$dir/$id") { DetailViewModel(dir, id, "") }
    val meta = vm.meta
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 播放器返回后保留用户所在的资源 Tab，避免先显示详情顶部再跳回在线播放。
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val detailEntry = nav.currentBackStackEntry
    val detailScrollState = rememberScrollState(
        initial = detailEntry?.savedStateHandle?.get<Int>("detail_scroll_y") ?: 0,
    )
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var cacheSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedEpisodes by remember { mutableStateOf(setOf<String>()) }
    var cachingEpisodes by remember { mutableStateOf(false) }

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
                if (tab == 0) IconButton(
                    onClick = {
                        if (cacheSelectionMode) {
                            if (selectedEpisodes.isEmpty() || cachingEpisodes) return@IconButton
                            val selected = selectedEpisodes.toSet()
                            val lines = vm.resources?.playLines.orEmpty()
                            cachingEpisodes = true
                            scope.launch {
                                var success = 0
                                var failed = 0
                                selected.forEach { key ->
                                    val parts = key.split("/", limit = 2)
                                    val line = lines.firstOrNull { it.id == parts.getOrNull(0) }
                                    val episode = parts.getOrNull(1)?.toIntOrNull()
                                    if (line == null || episode == null) {
                                        failed++
                                        return@forEach
                                    }
                                    runCatching {
                                        val directUrl = AppModule.repository.resolvePlayUrl(line.id, episode)
                                            ?: error("未解析到播放地址")
                                        val referer = "${AppModule.siteSettings.siteUrlNow}/py/${line.id}/$episode"
                                        OfflineMediaStore.download(context, directUrl, referer)
                                    }.onSuccess { success++ }.onFailure { failed++ }
                                }
                                cachingEpisodes = false
                                cacheSelectionMode = false
                                selectedEpisodes = emptySet()
                                Toast.makeText(
                                    context,
                                    if (failed == 0) "已缓存 $success 个资源，可离线播放" else "已缓存 $success 个，失败 $failed 个",
                                    Toast.LENGTH_LONG,
                                ).show()
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
            val tabTitles = listOf(
                "在线播放 ${res?.playLines?.sumOf { it.episodes.size } ?: 0}",
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

            when (tab) {
                0 -> PlayList(
                    lines = vm.resources?.playLines ?: emptyList(),
                    resolvingKey = resolvingKey,
                    selectionMode = cacheSelectionMode,
                    selectedEpisodes = selectedEpisodes,
                    onToggleSelection = { line, episode ->
                        val key = "${line.id}/$episode"
                        selectedEpisodes = if (key in selectedEpisodes) selectedEpisodes - key else selectedEpisodes + key
                    },
                ) { line, episode ->
                    val key = "${line.id}/$episode"
                    if (resolvingKey != null) return@PlayList
                    tab = 0
                    detailEntry?.savedStateHandle?.set("detail_scroll_y", detailScrollState.value)
                    resolvingKey = key
                    scope.launch {
                        try {
                            AppModule.repository.recordHistory(
                                HistoryItem(id, dir, meta?.title ?: "未命名影片", episode, meta?.posterUrl),
                            )
                            val directUrl = runCatching {
                                AppModule.repository.resolvePlayUrl(line.id, episode)
                            }.getOrNull()
                            if (directUrl != null) {
                                nav.navigate(
                                    "player/${Uri.encode(directUrl)}" +
                                        "?lineId=${Uri.encode(line.id)}" +
                                        "&episode=$episode" +
                                        "&episodeCount=${line.episodes.size}" +
                                        "&lineName=${Uri.encode(line.name)}" +
                                        "&resourceTitle=${Uri.encode(meta?.title.orEmpty())}",
                                )
                            } else {
                                Toast.makeText(context, "未解析到直链，已尝试打开在线播放页", Toast.LENGTH_SHORT).show()
                                Playback.openUrl(context, "${AppModule.siteSettings.siteUrlNow}/py/${line.id}/$episode")
                            }
                        } finally {
                            resolvingKey = null
                        }
                    }
                }
                1 -> CloudList(context, vm.resources?.clouds ?: emptyList())
                2 -> MagnetList(context, vm.resources?.magnets ?: emptyList())
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
private fun ResourceRow(
    title: String,
    subtitle: String? = null,
    isLoading: Boolean = false,
    selectionEnabled: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
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
                onClick = {
                    if (selectionMode) onToggleSelection(line, idx + 1) else onPlay(line, idx + 1)
                },
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
