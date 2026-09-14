package com.filmvault.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.filmvault.app.R
import com.filmvault.app.viewmodel.AuthViewModel
import coil.compose.AsyncImage

@Composable
fun LoginScreen(nav: NavController, vm: AuthViewModel = viewModel()) {
    var captchaWidthPx by remember { mutableIntStateOf(350) }
    val density = LocalDensity.current
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

        OutlinedTextField(
            value = vm.siteUrl,
            onValueChange = { vm.siteUrl = it },
            label = { Text("模块化仓库地址") },
            placeholder = { Text("输入你的模块化仓库地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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

        if (vm.captchaRequired) {
            if (vm.captchaTarget.isNotBlank()) {
                Text("请按顺序点击：${vm.captchaTarget}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
            vm.captchaImage?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = "验证码图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .onSizeChanged { captchaWidthPx = it.width.coerceAtLeast(1) }
                        .pointerInput(vm.captchaImage, vm.captchaTarget) {
                            detectTapGestures { offset ->
                                vm.addCaptchaTap(
                                    (offset.x / captchaWidthPx * 350f).toInt(),
                                    (offset.y / with(density) { 200.dp.toPx() } * 200f).toInt(),
                                )
                            }
                        },
                )
            }
            OutlinedTextField(
                value = vm.captcha,
                onValueChange = { vm.captcha = it },
                label = { Text("验证码") },
                placeholder = { Text("站点要求时填写") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.refreshCaptcha() }) { Text("看不清，换一张") }
            Spacer(Modifier.height(16.dp))
        }

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
}
