package com.filmvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.data.model.MovieItem
import com.filmvault.app.di.AppModule
import com.filmvault.app.ui.components.MainBottomBar
import com.filmvault.app.ui.components.MovieCard
import com.filmvault.app.ui.components.PosterPrefetch
import com.filmvault.app.viewmodel.HomeViewModel

/** 与网页首页一致：分别展示最近更新的电影、剧集和动漫。 */
@Composable
fun HomeScreen(nav: NavController) {
    val vm: HomeViewModel = viewModel(key = "home")
    val siteUrl by AppModule.siteSettings.siteUrlFlow.collectAsState(initial = AppModule.siteSettings.siteUrlNow)

    LaunchedEffect(siteUrl) {
        vm.restoreCache(siteUrl)
        vm.load(siteUrl)
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 标题没有业务点击动作，主动消费连续点击，避免事件落到页面层造成
                // 首页状态异常或出现只有背景色的空屏。
                Text(
                    "影库",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp).pointerInput(Unit) {
                        detectTapGestures { }
                    },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Default.Search, contentDescription = "搜索") }
                IconButton(onClick = { nav.navigate("library") }) { Icon(Icons.Default.Star, contentDescription = "收藏/历史") }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "站点设置") }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeSection("最近更新的电影", "mv", vm.sections["mv"].orEmpty(), vm.isLoading, vm.error, nav, siteUrl) { vm.load(siteUrl) } }
            item { HomeSection("最近更新的剧集", "tv", vm.sections["tv"].orEmpty(), vm.isLoading, vm.error, nav, siteUrl) { vm.load(siteUrl) } }
            item { HomeSection("最近更新的动漫", "ac", vm.sections["ac"].orEmpty(), vm.isLoading, vm.error, nav, siteUrl) { vm.load(siteUrl) } }
        }
        MainBottomBar(nav, "home")
    }
}

@Composable
private fun HomeSection(title: String, dir: String, items: List<MovieItem>, isLoading: Boolean, error: String?, nav: NavController, posterBaseUrl: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { nav.navigate("catalog/$dir?sort=uptime&year=3") }) { Text("更多 ❯") }
        }
        if (isLoading && items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (items.isEmpty() && error != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("重试") }
            }
        } else if (items.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("暂无内容", modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("刷新") }
            }
        } else {
            PosterPrefetch(items.take(12), posterBaseUrl)
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.take(12), key = { "${it.dir}/${it.id}" }) { item ->
                    MovieCard(item, posterBaseUrl = posterBaseUrl, modifier = Modifier.width(110.dp)) { nav.navigate("detail/${item.dir}/${item.id}") }
                }
            }
        }
    }
}
