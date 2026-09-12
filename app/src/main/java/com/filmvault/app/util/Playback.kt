package com.filmvault.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 播放/资源外链处理。
 *
 * 站点“在线播放”走第三方解析页（/py/...）、网盘走各家 App、磁力走 BT 客户端——
 * 这些本质上都依赖外部应用，原生 App 通过标准 Android Intent 交给系统处理，
 * 而不是用 WebView 套壳。仅当拿到直链（m3u8/mp4）时才用内置 ExoPlayer 原生播放。
 */
object Playback {

    fun openUrl(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "无法打开链接：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMagnet(context: Context, magnet: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            copy(context, magnet)
            Toast.makeText(context, "已复制磁力链接，请粘贴到 BT 客户端", Toast.LENGTH_LONG).show()
        }
    }

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("FilmVault", text))
    }
}
