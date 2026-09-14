@file:androidx.media3.common.util.UnstableApi

package com.filmvault.app.ui.screens

import android.view.ViewGroup
import android.view.MotionEvent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.filmvault.app.di.AppModule
import android.widget.Toast

private fun applyPlayerImmersiveMode(activity: android.app.Activity?, view: android.view.View) {
    val window = activity?.window ?: return
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, view).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
    @Suppress("DEPRECATION")
    view.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
}

private data class PlayerTrackOption(
    val group: TrackGroup,
    val index: Int,
    val label: String,
    val selected: Boolean,
)

private fun trackOptions(tracks: Tracks, type: Int): List<PlayerTrackOption> =
    tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).map { index ->
            val format = group.getTrackFormat(index)
            val size = if (type == C.TRACK_TYPE_VIDEO && format.height > 0) {
                "${format.width}×${format.height}"
            } else null
            val language = format.language?.takeIf { it.isNotBlank() }
            val label = listOfNotNull(format.label?.toString(), language, size)
                .distinct()
                .joinToString(" / ")
                .ifBlank { "轨道 ${index + 1}" }
            PlayerTrackOption(group.getMediaTrackGroup(), index, label, group.isTrackSelected(index))
        }
    }

@Composable
private fun PlayerMenuItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF294B70) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFB9DCFF) else Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "已选择", tint = Color(0xFF90CAF9))
        }
    }
}

/**
 * 原生播放器（Media3 ExoPlayer）。用于直接视频直链（m3u8 / mp4 / dash）。
 * 在线路解析出直链后进入此原生播放器，全程不依赖 WebView。
 */
@Composable
fun PlayerScreen(
    nav: NavController,
    url: String,
    lineId: String = "",
    startEpisode: Int = 1,
    episodeCount: Int = 1,
    lineName: String = "",
) {
    val context = LocalContext.current
    val view = LocalView.current
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(1L) }
    var bufferedPositionMs by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    val gestureScope = rememberCoroutineScope()
    val activity = context as? android.app.Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val trackSelector = remember { DefaultTrackSelector(context) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var currentEpisode by remember { mutableStateOf(startEpisode.coerceIn(1, episodeCount.coerceAtLeast(1))) }
    var playlistExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var trackMenuType by remember { mutableStateOf<Int?>(null) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var switchingEpisode by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    val episodeTotal = episodeCount.coerceAtLeast(1)
    val videoTrackCount = tracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .sumOf { it.length }
    val hasQualityOptions = videoTrackCount > 1
    val playerScope = rememberCoroutineScope()
    val currentEpisodeState by rememberUpdatedState(currentEpisode)
    val switchingEpisodeState by rememberUpdatedState(switchingEpisode)
    val scrubbingState by rememberUpdatedState(scrubbing)
    val previousOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val previousSoftInputMode = remember {
        activity?.window?.attributes?.softInputMode
            ?: android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
    }
    val player = remember {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    fun selectTrack(option: PlayerTrackOption, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group, option.index))
            .build()
        trackMenuType = null
    }

    fun disableTrackType(type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, true)
            .build()
        trackMenuType = null
    }

    fun switchEpisode(episode: Int) {
        if (switchingEpisodeState || episode !in 1..episodeTotal || lineId.isBlank()) return
        switchingEpisode = true
        playerScope.launch {
            try {
                val nextUrl = AppModule.repository.resolvePlayUrl(lineId, episode)
                if (nextUrl.isNullOrBlank()) {
                    Toast.makeText(context, "第${episode}集暂未解析到播放地址", Toast.LENGTH_SHORT).show()
                } else {
                    currentEpisode = episode
                    player.setMediaItem(MediaItem.fromUri(nextUrl), true)
                    player.prepare()
                    player.playWhenReady = true
                }
            } finally {
                switchingEpisode = false
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onTracksChanged(value: Tracks) { tracks = value }
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED && currentEpisodeState < episodeTotal && lineId.isNotBlank()) {
                    switchEpisode(currentEpisodeState + 1)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 自定义控制器的进度状态。进度条、时间和底部操作栏属于同一个 Compose 面板，
    // 避免 Media3 默认控制栏与自定义按钮分层、错位或重复显示。
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            if (!scrubbingState) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0L } ?: 1L
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
            // 华为横屏沉浸式下，adjustResize 会在点击工具栏时因导航栏 Insets
            // 短暂变化而压缩窗口，导致底部控制面板向上跳动。播放器固定使用
            // adjustNothing，退出时恢复 Activity 原本的输入模式。
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applyPlayerImmersiveMode(activity, view)
        // 华为设备在弹出工具菜单时可能重新派发系统栏 Insets。播放器不使用这些
        // Insets 做布局，因此直接消费，确保底部控制面板仍锚定在同一窗口坐标。
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, _ -> WindowInsetsCompat.CONSUMED }
        view.requestApplyInsets()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            // 恢复进入播放器前的方向，避免手机/平板返回时因方向重建出现空白页。
            activity?.requestedOrientation = previousOrientation
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                window.setSoftInputMode(previousSoftInputMode)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            player.release()
        }
    }

    fun preparePlayerExit() {
        val window = activity?.window ?: return
        // 先恢复播放器进入前的系统栏和方向，再切回详情页，避免详情页先按横屏
        // Insets 绘制一帧后才旋转回去，造成返回时的上下抖动。
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = previousOrientation
        window.setSoftInputMode(previousSoftInputMode)
    }

    fun exitPlayer() {
        if (exiting) return
        exiting = true
        // 先撤掉播放器交互层，再执行导航，避免快速连点继续命中旧页面的返回控件。
        locked = true
        controlsVisible = false
        playlistExpanded = false
        speedMenuExpanded = false
        trackMenuType = null
        preparePlayerExit()
        nav.popBackStack()
    }

    BackHandler(enabled = !exiting) { exitPlayer() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    // 控制器完全由下面的 Compose 面板统一绘制，避免默认控制栏与自定义
                    // 工具栏分离。PlayerView 这里只负责视频画面。
                    useController = false

                    // 手势直接挂在 PlayerView 上。普通点击切换统一控制面板，竖向滑动
                    // 保留亮度/音量调节。
                    var startX = 0f
                    var startY = 0f
                    var startBrightness = 0.5f
                    var startVolume = 0

                    fun updateGestureValue(deltaY: Float, touchX: Float, viewWidth: Int) {
                        if (touchX < viewWidth / 2f) {
                            val next = (startBrightness + deltaY / 900f).coerceIn(0.05f, 1f)
                            activity?.window?.let { window ->
                                window.attributes = window.attributes.apply { screenBrightness = next }
                            }
                            gestureHint = "亮度 ${(next * 100).toInt()}%"
                        } else {
                            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                            val next = (startVolume + deltaY / 900f * max).toInt().coerceIn(0, max)
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                            gestureHint = "音量 ${(next * 100 / max.coerceAtLeast(1))}%"
                        }
                    }

                    setOnTouchListener { playerView, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.x
                                startY = event.y
                                startBrightness = activity?.window?.attributes?.screenBrightness
                                    ?.takeIf { it >= 0f } ?: 0.5f
                                startVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaY = startY - event.y
                                val isVerticalGesture = kotlin.math.abs(deltaY) >= 30f &&
                                    kotlin.math.abs(deltaY) > kotlin.math.abs(startX - event.x) * 1.2f
                                if (isVerticalGesture) {
                                    // 移动过程中实时更新系统值和提示，不再等到抬手后才显示结果。
                                    updateGestureValue(deltaY, startX, playerView.width)
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                val deltaY = startY - event.y
                                val isVerticalGesture = kotlin.math.abs(deltaY) >= 80f &&
                                    kotlin.math.abs(deltaY) > kotlin.math.abs(startX - event.x) * 1.2f
                                if (isVerticalGesture) {
                                    updateGestureValue(deltaY, startX, playerView.width)
                                    gestureScope.launch {
                                        delay(900)
                                        gestureHint = null
                                    }
                                } else if (!locked) {
                                    controlsVisible = !controlsVisible
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                startX = 0f
                                startY = 0f
                            }
                        }
                        // 背景点击/手势由本监听器消费，控制面板上的 Compose 子控件仍可
                        // 正常接收自己的点击和拖动事件。
                        true
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
        if (!locked && controlsVisible) {
            // 进度条与所有工具共用同一个面板：进度条在上、工具栏在下，布局稳定且
            // 不会再出现进度条和设置按钮属于不同控制层的问题。
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(positionMs), color = Color.White)
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = {
                            scrubbing = true
                            positionMs = it.toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(positionMs.coerceIn(0L, durationMs))
                            scrubbing = false
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatDuration(durationMs), color = Color.White)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    // 设置类操作与全屏按钮位于同一行，保留选集、字幕、音轨等完整功能。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (episodeTotal > 1 && lineId.isNotBlank()) {
                            Box {
                                IconButton(onClick = {
                    playlistExpanded = true
                    speedMenuExpanded = false
                    trackMenuType = null
                                }) {
                                    Icon(
                                        Icons.Default.PlaylistPlay,
                                        contentDescription = if (lineName.isBlank()) "播放清单" else "播放清单：$lineName",
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    // 倍速与其它播放器操作保持在同一个底部控制面板内。
                    Box {
                        IconButton(onClick = {
                            speedMenuExpanded = true
                            playlistExpanded = false
                            trackMenuType = null
                        }) {
                            Text("${playbackSpeed}x", color = Color.White)
                        }
                    }
                    // 只有存在多个视频轨道/分辨率时显示清晰度入口，单一画质资源不显示
                    // 没有实际作用的按钮。
                    if (hasQualityOptions) {
                        IconButton(
                            onClick = {
                                trackMenuType = C.TRACK_TYPE_VIDEO
                                playlistExpanded = false
                                speedMenuExpanded = false
                            },
                        ) {
                            Text("清晰度", color = Color.White)
                        }
                    }
                    if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
                        IconButton(
                            onClick = {
                                trackMenuType = C.TRACK_TYPE_TEXT
                                playlistExpanded = false
                                speedMenuExpanded = false
                            },
                        ) { Icon(Icons.Default.Subtitles, contentDescription = "字幕设置", tint = Color.White) }
                    }
                    if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
                        IconButton(
                            onClick = {
                                trackMenuType = C.TRACK_TYPE_AUDIO
                                playlistExpanded = false
                                speedMenuExpanded = false
                            },
                        ) { Icon(Icons.Default.MusicNote, contentDescription = "音轨设置", tint = Color.White) }
                    }
                        IconButton(
                            onClick = {
                                fullscreen = !fullscreen
                                activity?.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
                                if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
                            },
                        ) {
                            Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = if (fullscreen) "退出全屏" else "全屏", tint = Color.White)
                        }
                    }
                }
            }
        }
        // 所有菜单均绘制在播放器窗口内，不使用独立 Popup/Dialog，避免华为设备关闭
        // 弹框时触发系统栏 Insets 重排导致底部控制面板抖动。
        if (!locked && controlsVisible && (playlistExpanded || speedMenuExpanded || trackMenuType != null)) {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(playlistExpanded, speedMenuExpanded, trackMenuType) {
                    detectTapGestures {
                        playlistExpanded = false
                        speedMenuExpanded = false
                        trackMenuType = null
                    }
                },
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 128.dp)
                    .widthIn(min = 190.dp, max = 300.dp)
                    .heightIn(max = 420.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF17191D),
                shadowElevation = 12.dp,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(6.dp),
                ) {
                val menuTitle = when {
                    playlistExpanded -> "播放清单"
                    speedMenuExpanded -> "播放速度"
                    trackMenuType == C.TRACK_TYPE_VIDEO -> "清晰度"
                    trackMenuType == C.TRACK_TYPE_TEXT -> "字幕"
                    else -> "音轨"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = menuTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        playlistExpanded = false
                        speedMenuExpanded = false
                        trackMenuType = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.75f))
                    }
                }
                when {
                    playlistExpanded -> (1..episodeTotal).forEach { episode ->
                        PlayerMenuItem(
                            label = if (episode == currentEpisode) "第${episode}集（播放中）" else "第${episode}集",
                            selected = episode == currentEpisode,
                            onClick = {
                                playlistExpanded = false
                                if (episode != currentEpisode) switchEpisode(episode)
                            },
                        )
                    }
                    speedMenuExpanded -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        PlayerMenuItem(
                            label = "${speed}x",
                            selected = speed == playbackSpeed,
                            onClick = {
                                playbackSpeed = speed
                                player.setPlaybackSpeed(speed)
                                speedMenuExpanded = false
                            },
                        )
                    }
                    else -> {
                        val type = trackMenuType ?: C.TRACK_TYPE_AUDIO
                        if (type == C.TRACK_TYPE_TEXT) {
                            PlayerMenuItem(
                                label = "关闭字幕",
                                onClick = { disableTrackType(type) },
                            )
                        }
                        trackOptions(tracks, type).forEach { option ->
                            PlayerMenuItem(
                                label = option.label,
                                selected = option.selected,
                                onClick = { selectTrack(option, type) },
                            )
                        }
                    }
                }
                }
            }
        }
        // 锁定按钮独立放在右侧中央，避免和底部工具、进度条重叠。
        IconButton(
            onClick = {
                locked = !locked
                if (locked) {
                    playlistExpanded = false
                    speedMenuExpanded = false
                    trackMenuType = null
                    controlsVisible = false
                } else {
                    controlsVisible = true
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        ) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (locked) "已锁定，点击解锁" else "未锁定，点击锁定",
                tint = Color.White,
            )
        }
        // 返回按钮固定在左上角，符合横屏播放器的常见布局，锁定时仍可退出播放器。
        IconButton(
            onClick = { exitPlayer() },
            enabled = !exiting,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
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

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
