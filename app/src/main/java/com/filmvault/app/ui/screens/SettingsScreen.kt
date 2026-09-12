package com.filmvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.util.hostOf
import com.filmvault.app.viewmodel.SettingsViewModel
import com.filmvault.app.ui.components.MainBottomBar

@Composable
fun SettingsScreen(nav: NavController) {
    val vm: SettingsViewModel = viewModel()
    val currentUrl by vm.currentUrl.collectAsState()
    val saved by vm.savedSites.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("站点设置", style = MaterialTheme.typography.titleLarge)
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("当前生效站点", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(currentUrl, style = MaterialTheme.typography.bodyLarge)
                        Text("域名：${hostOf(currentUrl)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.updateInput(it) },
                    label = { Text("站点地址") },
                    placeholder = { Text("例如 你的仓库地址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    onClick = { vm.save(); nav.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("  保存并切换", modifier = Modifier.padding(start = 6.dp))
                }
                Text(
                    "切换后该站点会重新进行反爬验证与登录；不同站点的登录态互不干扰。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (saved.isNotEmpty()) {
                item {
                    Text("已保存的站点（点击切换）", style = MaterialTheme.typography.titleSmall)
                }
                items(saved, key = { it }) { url ->
                    SiteRow(
                        url = url,
                        isCurrent = url == currentUrl,
                        onClick = { vm.switchTo(url); nav.popBackStack() },
                        onRemove = { vm.remove(url) },
                    )
                }
            }

            item {
                Text(
                    "作者：qinlinglong",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("免责声明", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "本 App 是一个模块化影视仓库与信息聚合工具，不提供、存储、上传或分发任何影视资源。App 展示的影片信息、封面、播放线路及第三方链接均来自用户配置的站点或相关服务，版权归原权利人所有。用户应自行判断内容与链接的合法性，并承担使用第三方服务产生的全部责任。请勿将本 App 用于任何违反法律法规或侵犯他人权益的行为。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        MainBottomBar(nav, "settings")
    }
}

@Composable
private fun SiteRow(
    url: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(url, style = MaterialTheme.typography.bodyMedium)
                if (isCurrent) {
                    Text("使用中", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
