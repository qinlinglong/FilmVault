package com.filmvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.ui.components.PosterGrid
import com.filmvault.app.viewmodel.CatalogViewModel
import com.filmvault.app.util.Constants

@Composable
fun SearchScreen(nav: NavController) {
    val vm: CatalogViewModel = viewModel(key = "search") { CatalogViewModel("", isSearch = true) }
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(Constants.SEARCH_CATEGORIES.first()) }
    var selectedMode by remember { mutableStateOf(Constants.SEARCH_MODES.first()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索影片 / 演员") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        vm.searchQuery = query
                        vm.searchType = selectedType.first
                        vm.searchMode = selectedMode.first
                        vm.refresh()
                    }) { Icon(Icons.Default.Search, contentDescription = "搜索") }
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Constants.SEARCH_CATEGORIES.forEach { option ->
                FilterChip(
                    selected = selectedType.first == option.first,
                    onClick = { selectedType = option },
                    label = { Text(option.second) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Constants.SEARCH_MODES.forEach { option ->
                FilterChip(
                    selected = selectedMode.first == option.first,
                    onClick = { selectedMode = option },
                    label = { Text("${option.second}搜索") },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.isLoading && vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                PosterGrid(
                    items = vm.items,
                    onItemClick = { nav.navigate("detail/${it.dir}/${it.id}") },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                )
            }
        }
    }
}
