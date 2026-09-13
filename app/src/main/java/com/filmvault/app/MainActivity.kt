package com.filmvault.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.filmvault.app.ui.navigation.AppNavHost
import com.filmvault.app.ui.theme.FilmVaultTheme

class MainActivity : ComponentActivity() {
    private fun applyImmersiveBackground() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让 Compose 背景延伸到系统栏，内容本身避开状态栏/导航栏，形成沉浸式融合效果。
        applyImmersiveBackground()
        setContent {
            FilmVaultTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 部分厂商从后台恢复时会重置系统栏颜色/Insets，重新应用避免出现白屏或白色系统栏。
        applyImmersiveBackground()
    }
}
