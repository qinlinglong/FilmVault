package com.filmvault.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.R
import com.filmvault.app.di.AppModule
import com.filmvault.app.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(nav: NavController, vm: AuthViewModel = viewModel()) {
    val savedSites by AppModule.siteSettings.savedSitesFlow.collectAsState(initial = emptyList())
    var siteMenuExpanded by remember { mutableStateOf(false) }
    var captchaWidthPx by remember { mutableIntStateOf(350) }
    var captchaHeightPx by remember { mutableIntStateOf(200) }
    val isCompactCaptcha = LocalConfiguration.current.screenWidthDp <= 600
    val captchaCoordinateWidth = if (isCompactCaptcha) 315 else 350
    val captchaCoordinateHeight = if (isCompactCaptcha) 180 else 200
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("影库 FilmVault", style = MaterialTheme.typography.headlineSmall)
        Text("原生安卓客户端", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(28.dp))

        ExposedDropdownMenuBox(
            expanded = siteMenuExpanded && savedSites.isNotEmpty(),
            onExpandedChange = { siteMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = vm.siteUrl,
                onValueChange = { vm.siteUrl = it },
                label = { Text("模块化仓库地址") },
                placeholder = { Text("输入你的模块化仓库地址") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = siteMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(
                expanded = siteMenuExpanded && savedSites.isNotEmpty(),
                onDismissRequest = { siteMenuExpanded = false },
            ) {
                savedSites.forEach { url ->
                    DropdownMenuItem(
                        text = { Text(url) },
                        onClick = {
                            vm.siteUrl = url
                            siteMenuExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.email,
            onValueChange = { vm.email = it },
            label = { Text("账号 / 邮箱") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.password,
            onValueChange = { vm.password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (vm.error != null) {
            Text(vm.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { vm.login { nav.navigate("home") { popUpTo("login") { inclusive = true } } } },
            enabled = !vm.isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.isLoading) "登录中…" else "登 录", fontSize = 16.sp)
        }

    }

    if (vm.captchaRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("安全验证") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.captchaTarget.isNotBlank()) Text("请按顺序点击：${vm.captchaTarget}")
                    vm.captchaImage?.let { dataUri ->
                        val bitmap = remember(dataUri) {
                            runCatching {
                                val bytes = Base64.decode(dataUri.substringAfter(","), Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1.75f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .onSizeChanged {
                                        captchaWidthPx = it.width.coerceAtLeast(1)
                                        captchaHeightPx = it.height.coerceAtLeast(1)
                                    }
                                    .pointerInput(dataUri, vm.captchaTarget, captchaWidthPx, captchaHeightPx) {
                                        detectTapGestures { offset ->
                                            vm.addCaptchaTap(
                                                (offset.x / captchaWidthPx * captchaCoordinateWidth).toInt(),
                                                (offset.y / captchaHeightPx * captchaCoordinateHeight).toInt(),
                                            )
                                        }
                                    },
                            ) {
                                Image(bitmap, "验证码图片", Modifier.fillMaxSize())
                                Canvas(Modifier.fillMaxSize()) {
                                    vm.captchaPoints.forEachIndexed { index, point ->
                                        val center = Offset(
                                            point.first / captchaCoordinateWidth.toFloat() * size.width,
                                            point.second / captchaCoordinateHeight.toFloat() * size.height,
                                        )
                                        drawCircle(Color(0xFF2E7D32), 15.dp.toPx(), center)
                                        drawLine(Color.White, center + Offset(-7f, 0f), center + Offset(-2f, 6f), 3.dp.toPx())
                                        drawLine(Color.White, center + Offset(-2f, 6f), center + Offset(8f, -7f), 3.dp.toPx())
                                    }
                                }
                            }
                        } else Text("验证码图片加载失败，请点击换一张")
                    }
                    Button(onClick = { vm.refreshCaptcha() }, modifier = Modifier.fillMaxWidth()) {
                        Text("换一张验证码")
                    }
                    Button(
                        onClick = { vm.verifyCaptcha(captchaCoordinateWidth, captchaCoordinateHeight) { nav.navigate("home") { popUpTo("login") { inclusive = true } } } },
                        enabled = !vm.captchaVerified && vm.captchaPoints.size >= vm.captchaTarget.length,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (vm.captchaVerified) "正在登录…" else "校验并登录")
                    }
                    if (vm.error != null) {
                        Text(vm.error!!, color = if (vm.captchaVerified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
        )
    }
}
