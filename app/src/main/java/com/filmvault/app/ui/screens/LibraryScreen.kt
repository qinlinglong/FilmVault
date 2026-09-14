package com.filmvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.data.model.HistoryItem
import com.filmvault.app.di.AppModule
import com.filmvault.app.util.FavEntry
import com.filmvault.app.util.posterUrl
import com.filmvault.app.viewmodel.LibraryViewModel
import com.filmvault.app.ui.components.MainBottomBar
import com.filmvault.app.ui.components.PosterImage

@Composable
fun LibraryScreen(nav: NavController) {
    val vm: LibraryViewModel = viewModel()
    val favs by vm.favorites.collectAsState(initial = emptyList())
    val history by vm.history.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { vm.loadHistory() }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("我的", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Filled.Settings, contentDescription = "站点设置")
                }
            }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("收藏") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("观看历史") })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
          when (tab) {
            0 -> {
                val list = favs
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有收藏", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { "${it.dir}/${it.id}" }) { FavRow(it) { nav.navigate("detail/${it.dir}/${it.id}") } }
                    }
                }
            }
            1 -> {
                if (vm.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (history.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无观看历史", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { "${it.dir}/${it.id}/${it.episode ?: 0}" }) { HistRow(it) { nav.navigate("detail/${it.dir}/${it.id}") } }
                    }
                }
            }
          }
        }
        MainBottomBar(nav, "library")
    }
}

@Composable
private fun FavRow(item: FavEntry, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterImage(
                url = posterUrl(AppModule.siteSettings.siteUrlNow, item.dir, item.id),
                contentDescription = item.title,
                modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                item.year?.let { Text("$it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }
        }
    }
}

@Composable
private fun HistRow(item: HistoryItem, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterImage(
                url = posterUrl(AppModule.siteSettings.siteUrlNow, item.dir, item.id),
                contentDescription = item.title,
                modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                item.episode?.let { Text("第${it}集", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }
        }
    }
}
