package com.vapkit.demo

import android.media.MediaPlayer
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vapkit.VapPlaybackState
import com.vapkit.VapPlayer
import com.vapkit.VapTextureView
import kotlinx.coroutines.launch

private val Gold = Color(1f, 0.78f, 0.24f)
private val Panel = Color(0f, 0f, 0f, 0.72f)

@Composable
fun LiveRoomScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { VapPlayer().apply { loop = false } }
    var selected by remember { mutableStateOf(GiftCatalog.defaultSelection) }
    var loadedId by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(true) }
    var following by remember { mutableStateOf(false) }
    var coins by remember { mutableIntStateOf(8888) }
    var likes by remember { mutableIntStateOf(1284) }
    var comments by remember {
        mutableStateOf(
            listOf(
                "晚风  这首也太好听了",
                "阿年  滤镜好可爱",
                "小北  来了来了",
            ),
        )
    }
    var playingGift by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        player.onStateChanged = { state ->
            playingGift = state == VapPlaybackState.Playing
        }
        onDispose {
            player.onStateChanged = null
            player.release()
        }
    }

    LaunchedEffect(selected.id) {
        val asset = selected.assetName ?: return@LaunchedEffect
        runCatching {
            context.assets.openFd(asset).use { fd ->
                player.load(fd, context.cacheDir, asset.substringAfterLast('/'))
            }
            loadedId = selected.id
            sendError = null
        }.onFailure {
            sendError = "礼物加载失败"
        }
    }

    Box(modifier.fillMaxSize()) {
        LoopingBackgroundVideo(Modifier.fillMaxSize())
        if (playingGift) {
            GiftOverlay(player, Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues()),
        ) {
            TopBar(
                following = following,
                panelOpen = panelOpen,
                onFollow = { following = !following },
                onClosePanel = { panelOpen = false },
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                CommentList(comments.takeLast(4), Modifier.weight(1f))
                if (!panelOpen) {
                    SideActions(
                        likes = likes,
                        onLike = { likes += 1 },
                        onOpenGifts = { panelOpen = true },
                    )
                }
            }
            if (!panelOpen) {
                ComposerBar(onOpenGifts = { panelOpen = true })
            }
        }

        if (panelOpen) {
            GiftPanel(
                selected = selected,
                coins = coins,
                sendError = sendError,
                onSelect = { selected = it },
                onSend = {
                    scope.launch {
                        val gift = selected
                        if (!gift.isReady) return@launch
                        if (coins < gift.price) {
                            sendError = "金币不足"
                            return@launch
                        }
                        if (loadedId != gift.id) {
                            val asset = gift.assetName ?: return@launch
                            context.assets.openFd(asset).use { fd ->
                                player.load(fd, context.cacheDir, asset.substringAfterLast('/'))
                            }
                            loadedId = gift.id
                        }
                        coins -= gift.price
                        player.loop = false
                        player.play()
                        comments = comments + "我  送出 ${gift.name}"
                        sendError = null
                        panelOpen = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun LoopingBackgroundVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            TextureView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                var player: MediaPlayer? = null
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        val media = MediaPlayer()
                        context.assets.openFd("background/dong_qu_chun_lai.mp4").use { fd ->
                            media.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                        }
                        media.isLooping = true
                        media.setSurface(android.view.Surface(surface))
                        media.setOnPreparedListener { it.start() }
                        media.prepareAsync()
                        player = media
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit
                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        player?.release()
                        player = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                }
            }
        },
    )
}

@Composable
private fun GiftOverlay(player: VapPlayer, modifier: Modifier = Modifier) {
    val info = player.manifest?.info
    val aspect = if (info != null && info.height > 0) info.width.toFloat() / info.height else 9f / 16f
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = min(maxWidth, maxHeight * aspect)
        val height = width / aspect
        AndroidView(
            modifier = Modifier
                .width(width)
                .height(height),
            factory = { context ->
                VapTextureView(context).apply {
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { it.player = player },
        )
    }
}

@Composable
private fun TopBar(
    following: Boolean,
    panelOpen: Boolean,
    onFollow: () -> Unit,
    onClosePanel: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0f, 0f, 0f, 0.36f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("👤", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("冬去春来", color = Color.White, fontSize = 14.sp)
                Text("1.2万人气", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (following) "已关注" else "关注",
                color = if (following) Color.White else Color.Black,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (following) Color.White.copy(alpha = 0.24f) else Color.White)
                    .clickable(onClick = onFollow)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (panelOpen) {
            IconButton(onClick = onClosePanel) {
                Icon(Icons.Default.Close, contentDescription = "关闭礼物栏", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CommentList(comments: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        comments.forEach { line ->
            Text(
                line,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0f, 0f, 0f, 0.36f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SideActions(likes: Int, onLike: () -> Unit, onOpenGifts: () -> Unit) {
    Column(
        modifier = Modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircleAction(Icons.Default.Favorite, compactCount(likes), onLike)
        CircleAction(Icons.Default.Share, "分享") {}
        CircleAction(Icons.Default.CardGiftcard, "礼物", onOpenGifts)
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .background(Color(0f, 0f, 0f, 0.36f), CircleShape),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.wrapContentWidth(unbounded = true, align = Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ComposerBar(onOpenGifts: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "说点什么…",
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0f, 0f, 0f, 0.36f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.width(12.dp))
        IconButton(
            onClick = onOpenGifts,
            modifier = Modifier
                .size(44.dp)
                .background(Color(0f, 0f, 0f, 0.36f), CircleShape),
        ) {
            Icon(Icons.Default.CardGiftcard, contentDescription = "打开礼物栏", tint = Gold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GiftPanel(
    selected: GiftItem,
    coins: Int,
    sendError: String?,
    onSelect: (GiftItem) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(bottom = bottomInset),
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .align(Alignment.CenterHorizontally)
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("礼物", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("✦ $coins", color = Gold, fontSize = 14.sp)
        }
        FlowRow(
            Modifier.padding(horizontal = 12.dp),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GiftCatalog.all.forEach { gift ->
                GiftCell(
                    gift = gift,
                    selected = gift.id == selected.id,
                    onClick = { onSelect(gift) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (sendError != null) {
            Text(sendError, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(16.dp, 8.dp))
        }
        val enabled = selected.isReady && coins >= selected.price
        Button(
            onClick = onSend,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.24f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp)
                .height(48.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                when {
                    !selected.isReady -> "待上架"
                    coins < selected.price -> "金币不足"
                    else -> "送给冬去春来"
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GiftCell(
    gift: GiftItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .height(108.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (gift.isReady) gift.emoji else "🔒", fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(gift.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(
            if (gift.isReady) "${gift.price}" else "待上架",
            color = Gold.copy(alpha = if (gift.isReady) 1f else 0.55f),
            fontSize = 11.sp,
        )
    }
}

private fun compactCount(value: Int): String {
    return if (value >= 10_000) String.format("%.1fw", value / 10_000.0) else value.toString()
}
