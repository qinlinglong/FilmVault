package com.filmvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.ui.components.PosterGrid
import com.filmvault.app.util.Constants
import com.filmvault.app.viewmodel.CatalogViewModel

data class FilterOption(val label: String, val value: String, val param: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(nav: NavController, dir: String, label: String) {
    val vm: CatalogViewModel = viewModel(key = dir) { CatalogViewModel(dir) }
    var query by remember { mutableStateOf("") }

    val sortOpts = Constants.SORT_OPTIONS.map { (v, l) -> FilterOption(l, v, "sort") }
    val qualityOpts = Constants.QUALITY_OPTIONS.map { (v, l) -> FilterOption(l, v, "quality") }
    val statusOpts = listOf(
        FilterOption("全部状态", "", "status"),
        FilterOption("预告", "预告", "status"),
        if (dir == "mv") FilterOption("枪版", "抢先版", "status") else FilterOption("连载", "连载", "status"),
        if (dir != "mv") FilterOption("完结", "完结", "status") else FilterOption("全部", "", "status"),
    ).distinctBy { it.label }
    val resourceOpts = Constants.RESOURCE_OPTIONS.map { (v, l) -> FilterOption(l, v, "res") }
    val yearOpts = Constants.YEAR_OPTIONS.map { (v, l) -> FilterOption(l, v, "year") }
    val regionOpts = Constants.REGION_OPTIONS.map { (v, l) -> FilterOption(l, v, "region") }
    val languageOpts = Constants.LANGUAGE_OPTIONS.map { (v, l) -> FilterOption(l, v, "lang") }

    LaunchedEffect(Unit) { if (vm.items.isEmpty()) vm.refresh() }
    LaunchedEffect(query) {
        delay(450)
        val normalized = query.trim()
        if (vm.filters["q"].orEmpty() != normalized) vm.setFilter("q", normalized)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(label, style = MaterialTheme.typography.titleLarge)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索${label} / 演员") },
                trailingIcon = {
                    IconButton(onClick = { vm.setFilter("q", query.trim()) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.setFilter("q", query.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterDropdown("排序", sortOpts, vm) }
            item { FilterDropdown("画质", qualityOpts, vm) }
            item { FilterDropdown("状态", statusOpts, vm) }
            item { FilterDropdown("资源", resourceOpts, vm) }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterDropdown("年代", yearOpts, vm) }
            item { FilterDropdown("地区", regionOpts, vm) }
            item { FilterDropdown("语言", languageOpts, vm) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.isLoading && vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                PosterGrid(
                    items = vm.items,
                    onItemClick = { nav.navigate("detail/${it.dir}/${it.id}") },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                )
                if (vm.error != null) {
                    Text(vm.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (vm.page < vm.totalPages) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = { vm.nextPage() }, enabled = !vm.isLoading) {
                    Text(if (vm.isLoading) "加载中…" else "加载更多")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(label: String, options: List<FilterOption>, vm: CatalogViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(options[0]) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)) {
            Text("${label}: ${selected.label}")
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        selected = opt
                        expanded = false
                        vm.setFilter(opt.param, opt.value)
                    },
                )
            }
        }
    }
}
