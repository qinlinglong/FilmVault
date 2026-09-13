@file:androidx.media3.common.util.UnstableApi

package com.filmvault.app.ui.screens

import android.view.ViewGroup
import android.view.MotionEvent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.navigation.NavController

/**
 * 原生播放器（Media3 ExoPlayer）。用于直接视频直链（m3u8 / mp4 / dash）。
 * 在线路解析出直链后进入此原生播放器，全程不依赖 WebView。
 */
@Composable
fun PlayerScreen(nav: NavController, url: String) {
    val context = LocalContext.current
    val view = LocalView.current
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    val gestureScope = rememberCoroutineScope()
    val activity = context as? android.app.Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val trackSelector = remember { DefaultTrackSelector(context) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    val player = remember {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(value: Tracks) { tracks = value }
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        controller?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
            player.release()
        }
    }

    BackHandler { nav.popBackStack() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    controllerShowTimeoutMs = 3500

                    // 手势直接挂在 PlayerView 上，并返回 false，让普通点击继续交给
                    // Media3 控制栏处理（播放/暂停、进度拖动、快进/快退等）。
                    var startX = 0f
                    var startY = 0f
                    setOnTouchListener { playerView, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.x
                                startY = event.y
                            }
                            MotionEvent.ACTION_UP -> {
                                val deltaY = startY - event.y
                                val isVerticalGesture = kotlin.math.abs(deltaY) >= 80f &&
                                    kotlin.math.abs(deltaY) > kotlin.math.abs(startX - event.x) * 1.2f
                                if (isVerticalGesture) {
                                    if (startX < playerView.width / 2f) {
                                        val window = activity?.window
                                        val current = window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                                        val next = (current + deltaY / 900f).coerceIn(0.05f, 1f)
                                        window?.let { it.attributes = it.attributes.apply { screenBrightness = next } }
                                        gestureHint = "亮度 ${(next * 100).toInt()}%"
                                    } else {
                                        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                        val next = (current + deltaY / 900f * max).toInt().coerceIn(0, max)
                                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                        gestureHint = "音量 ${(next * 100 / max.coerceAtLeast(1))}%"
                                    }
                                    gestureScope.launch {
                                        delay(900)
                                        gestureHint = null
                                    }
                                } else {
                                    playerView.performClick()
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                startX = 0f
                                startY = 0f
                            }
                        }
                        false
                    }
                }
            },
        )

        // 锁定后拦截播放器区域触摸，只保留解锁按钮，防止误触暂停、拖动进度或切换控制栏。
        if (locked) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
            )
        }
        if (!locked) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 20.dp, start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.seekBack() }) {
                    Icon(Icons.Default.Replay10, contentDescription = "后退 10 秒", tint = Color.White)
                }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { player.seekForward() }) {
                    Icon(Icons.Default.Forward10, contentDescription = "前进 10 秒", tint = Color.White)
                }
            }
            if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
                IconButton(
                    onClick = { TrackSelectionDialogBuilder(context, "选择字幕", player, C.TRACK_TYPE_TEXT).build().show() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 208.dp),
                ) { Icon(Icons.Default.Subtitles, contentDescription = "字幕", tint = Color.White) }
            }
            if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
                IconButton(
                    onClick = { TrackSelectionDialogBuilder(context, "选择音轨", player, C.TRACK_TYPE_AUDIO).build().show() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 160.dp),
                ) { Icon(Icons.Default.MusicNote, contentDescription = "音轨", tint = Color.White) }
            }
        }
        if (!locked) IconButton(
            onClick = {
                fullscreen = !fullscreen
                activity?.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
                if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 64.dp),
        ) {
            Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = if (fullscreen) "退出全屏" else "全屏", tint = Color.White)
        }
        IconButton(
            onClick = { locked = !locked },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 16.dp),
        ) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (locked) "已锁定，点击解锁" else "未锁定，点击锁定",
                tint = Color.White,
            )
        }
        gestureHint?.let { hint ->
            Row(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (hint.startsWith("亮度")) Icons.Default.Brightness6 else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                )
                Text(hint, color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
