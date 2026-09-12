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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.filmvault.app.di.AppModule
import com.filmvault.app.ui.components.PosterImage
import com.filmvault.app.util.Playback
import com.filmvault.app.viewmodel.DetailViewModel
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(nav: NavController, dir: String, id: String) {
    val vm: DetailViewModel = viewModel(key = "$dir/$id") { DetailViewModel(dir, id, "") }
    val meta = vm.meta
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var resolvingKey by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Surface(shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(meta?.title ?: "详情", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.toggleFavorite() }) {
                    Icon(
                        if (vm.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (vm.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
          Column(Modifier.verticalScroll(rememberScrollState()).padding(if (wide) 28.dp else 16.dp)) {
            // 头部：海报 + 信息
            Row(Modifier.fillMaxWidth()) {
                PosterImage(
                    url = meta?.posterUrl ?: "/img/$dir/$id/256.webp",
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
                "磁力资源 ${res?.magnets?.size ?: 0}",
                "网盘资源 ${res?.clouds?.size ?: 0}",
                "在线播放 ${res?.playLines?.sumOf { it.episodes.size } ?: 0}",
            )
            TabRow(selectedTabIndex = tab) {
                tabTitles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = MaterialTheme.typography.labelSmall.fontSize) }) }
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                0 -> MagnetList(context, vm.resources?.magnets ?: emptyList())
                1 -> CloudList(context, vm.resources?.clouds ?: emptyList())
                2 -> PlayList(vm.resources?.playLines ?: emptyList(), resolvingKey) { line, episode ->
                    val key = "${line.id}/$episode"
                    if (resolvingKey != null) return@PlayList
                    resolvingKey = key
                    scope.launch {
                        try {
                            val directUrl = runCatching {
                                AppModule.repository.resolvePlayUrl(line.id, episode)
                            }.getOrNull()
                            if (directUrl != null) {
                                nav.navigate("player/${Uri.encode(directUrl)}")
                            } else {
                                Toast.makeText(context, "未解析到直链，已尝试打开在线播放页", Toast.LENGTH_SHORT).show()
                                Playback.openUrl(context, "${AppModule.siteSettings.siteUrlNow}/py/${line.id}/$episode")
                            }
                        } finally {
                            resolvingKey = null
                        }
                    }
                }
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
private fun ResourceRow(title: String, subtitle: String? = null, isLoading: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
    onPlay: (line: com.filmvault.app.data.model.PlayLine, episode: Int) -> Unit,
) {
    if (lines.isEmpty()) EmptyHint("暂无在线播放线路")
    lines.forEach { line ->
        Text(line.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        line.episodes.forEachIndexed { idx, ep ->
            ResourceRow(
                title = if (resolvingKey == "${line.id}/${idx + 1}") "正在解析并准备播放器…" else ep.ifBlank { "第 ${idx + 1} 集" },
                isLoading = resolvingKey == "${line.id}/${idx + 1}",
                onClick = { onPlay(line, idx + 1) },
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
