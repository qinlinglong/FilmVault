package com.filmvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.ui.components.MainBottomBar
import com.filmvault.app.ui.components.PosterGrid
import com.filmvault.app.viewmodel.HotViewModel

@Composable
fun HotScreen(nav: NavController) {
    val tabs = listOf("mv" to "电影", "tv" to "剧集", "ac" to "动漫")
    val periods = listOf("day" to "本日排行", "week" to "本周排行", "month" to "本月排行", "numbers" to "评分总数")
    var selected by remember { mutableStateOf("mv") }
    val vm: HotViewModel = viewModel(key = "hot-$selected") { HotViewModel(selected) }

    LaunchedEffect(selected) {
        if (vm.items.isEmpty()) vm.load()
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("热门", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { nav.navigate("search") }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            }
        }

        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs) { (dir, label) ->
                val isSelected = dir == selected
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { selected = dir },
                ) {
                    Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
            }
        }

        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(periods) { (value, label) ->
                TextButton(onClick = { vm.updatePeriod(value) }, enabled = !vm.isLoading) {
                    Text(label, color = if (vm.period == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.isLoading && vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (vm.items.isEmpty() && vm.error != null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Text(vm.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.load() }) { Text("重试") }
                }
            } else if (vm.items.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Text("暂无热门数据")
                    TextButton(onClick = { vm.load() }) { Text("重试") }
                }
            } else {
                PosterGrid(vm.items, { nav.navigate("detail/${it.dir}/${it.id}") }, Modifier.fillMaxSize().padding(horizontal = 12.dp))
            }
        }

        MainBottomBar(nav, "hot")
    }
}
