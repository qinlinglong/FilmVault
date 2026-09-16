package com.filmvault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.filmvault.app.data.model.MovieItem

@Composable
fun PosterGrid(
    items: List<MovieItem>,
    onItemClick: (MovieItem) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    onNearEnd: (() -> Unit)? = null,
) {
    PosterPrefetch(items)
    LaunchedEffect(state, items.size, onNearEnd) {
        if (onNearEnd == null) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastIndex ->
                if (lastIndex >= items.lastIndex - 4) onNearEnd()
            }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        state = state,
        modifier = modifier.fillMaxWidth(),
    ) {
        // 分页接口偶尔会在相邻页返回同一资源；不能只用资源 id 作为 key，
        // 否则 Compose LazyGrid 会因重复 key 在翻页时直接抛异常退出。
        itemsIndexed(items, key = { index, item -> "${item.dir}/${item.id}#$index" }) { _, item ->
            MovieCard(item = item, modifier = Modifier.fillMaxWidth(), onClick = { onItemClick(item) })
        }
    }
}

/**
 * Lazy 网格/横向列表只会组合当前可见卡片，导致海报逐张进入组合树后才开始请求。
 * 列表数据到达后提前把海报交给 Coil 队列，滚动到卡片时直接从缓存读取，减少逐张出现。
 */
@Composable
fun PosterPrefetch(items: List<MovieItem>) {
    val context = LocalContext.current
    // 只预取首屏附近的内容，避免一次性占满连接和解码队列。
    val urls = items.mapNotNull { it.posterUrl }.distinct().take(12)
    LaunchedEffect(urls) {
        urls.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(256)
                    .build(),
            )
        }
    }
}

@Composable
fun MovieCard(
    item: MovieItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .then(modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Box {
            PosterImage(
                url = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentScale = ContentScale.Crop,
            )
            // 评分角标
            item.rating?.let {
                Box(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = it.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                item.year?.let {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
                if (item.quality.isNotEmpty()) {
                    Text(
                        text = " · " + item.quality.first(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** 使用站点原始海报地址，交给 Coil 默认请求链路处理缓存与 TLS 协商。 */
@Composable
fun PosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(256)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = contentScale,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
    )
}
