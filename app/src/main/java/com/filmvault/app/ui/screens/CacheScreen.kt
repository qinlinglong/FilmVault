package com.filmvault.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavController
import com.filmvault.app.di.AppModule
import com.filmvault.app.ui.components.PosterImage
import com.filmvault.app.util.OfflineMediaStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CacheScreen(nav: NavController) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(emptyList<OfflineMediaStore.CacheEntry>()) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    val history by AppModule.repository.history.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    suspend fun refresh() { entries = withContext(Dispatchers.IO) { OfflineMediaStore.list(context) } }
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(500)
        }
    }
    val groups = entries.groupBy { it.label.substringBefore(" · ").ifBlank { "未命名资源" } }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                Column(Modifier.weight(1f)) {
                    Text("缓存管理", style = MaterialTheme.typography.titleLarge)
                    Text("${formatBytes(OfflineMediaStore.cacheSize(context))} · ${entries.size} 个资源", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (entries.isEmpty()) {
            Text("暂无缓存资源", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(groups.entries.toList(), key = { it.key }) { (title, groupEntries) ->
                    val expanded = title in expandedGroups
                    val poster = groupEntries.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
                        ?: history.firstOrNull { it.title == title }?.posterUrl
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { expandedGroups = if (expanded) expandedGroups - title else expandedGroups + title },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosterImage(
                                    url = poster,
                                    contentDescription = title,
                                    modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${groupEntries.size} 个资源 · ${formatBytes(groupEntries.sumOf { it.bytes })} · " +
                                            if (groupEntries.all { it.completed }) "已完成" else "有未完成任务",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    )
                                }
                                Text(
                                    "查看详情",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        groupEntries.firstOrNull()?.detailRoute?.takeIf { it.isNotBlank() }?.let { nav.navigate("$it?localOnly=true") }
                                    },
                                )
                            }
                            if (expanded) groupEntries.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 12.dp).clickable {
                                        val local = OfflineMediaStore.cachedUri(context, "", entry.cacheKey)
                                        if (local != null) {
                                            val parts = entry.cacheKey.split('/')
                                            val lineId = parts.getOrNull(3).orEmpty()
                                            val episode = parts.getOrNull(4)?.toIntOrNull() ?: 1
                                            nav.navigate(
                                                "player/${Uri.encode(local.toString())}?lineId=${Uri.encode(lineId)}&episode=$episode&episodeCount=1&lineName=&resourceTitle=${Uri.encode(title)}&cacheKey=${Uri.encode(entry.cacheKey)}",
                                            )
                                        }
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.label.removePrefix("$title · "),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            "${when {
                                                entry.completed -> "已完成"
                                                OfflineMediaStore.isActive(entry.cacheKey) -> "下载中"
                                                entry.state == "failed" -> "下载失败，可继续"
                                                else -> "已暂停，可继续"
                                            }} · ${formatBytes(entry.bytes)}" +
                                                (if (entry.total > 0L) " / ${formatBytes(entry.total)}" else "") +
                                                " · ${entry.file.extension.uppercase()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        )
                                        if (!entry.completed && entry.total > 0L) {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { entry.progress },
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            )
                                        }
                                    }
                                    if (!entry.completed) {
                                        IconButton(onClick = {
                                            if (OfflineMediaStore.isActive(entry.cacheKey)) {
                                                OfflineMediaStore.pause(entry.cacheKey)
                                                scope.launch { refresh() }
                                            } else if (entry.sourceUrl.isNotBlank()) {
                                                scope.launch {
                                                    runCatching {
                                                        OfflineMediaStore.download(
                                                            context,
                                                            entry.sourceUrl,
                                                            entry.referer,
                                                            entry.label,
                                                            entry.cacheKey,
                                                            entry.detailRoute,
                                                        )
                                                    }
                                                    refresh()
                                                }
                                            }
                                        }) {
                                            Icon(
                                                if (OfflineMediaStore.isActive(entry.cacheKey)) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (OfflineMediaStore.isActive(entry.cacheKey)) "暂停缓存" else "继续缓存",
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        OfflineMediaStore.delete(entry)
                                        scope.launch { refresh() }
                                    }) { Icon(Icons.Filled.Delete, contentDescription = "删除缓存") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
