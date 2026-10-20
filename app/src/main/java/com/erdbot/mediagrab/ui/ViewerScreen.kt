package com.erdbot.mediagrab.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.erdbot.mediagrab.download.DownloadRecord
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.ui.theme.Mg

/**
 * Full-screen viewer for saved files: swipe between them, videos and audio play in-app,
 * photos pinch- or double-tap-zoom. Always black, whatever the theme, so media looks right.
 */
@OptIn(UnstableApi::class)
@Composable
fun ViewerScreen(
    records: List<DownloadRecord>,
    startId: String,
    onClose: () -> Unit,
    onShare: (DownloadRecord) -> Unit,
    onOpenWith: (DownloadRecord) -> Unit,
    onDelete: (DownloadRecord) -> Unit,
) {
    val t = Mg
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    if (records.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(
        initialPage = records.indexOfFirst { it.id == startId }.coerceAtLeast(0),
        pageCount = { records.size },
    )
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Only the page on screen plays; swiping away stops it.
    val current = records.getOrNull(pager.currentPage)
    LaunchedEffect(current?.id) {
        player.stop()
        player.clearMediaItems()
        if (current != null && current.mediaType != MediaType.IMAGE && current.contentUri != null) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(current.contentUri)))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { records[it].id }) { page ->
            val record = records[page]
            when {
                record.mediaType == MediaType.IMAGE -> ZoomableImage(record)
                page == pager.currentPage -> Box(Modifier.fillMaxSize()) {
                    if (record.mediaType == MediaType.AUDIO) {
                        AsyncImage(record.thumbnailUrl, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                if (record.mediaType == MediaType.AUDIO) {
                                    controllerShowTimeoutMs = 0
                                    controllerHideOnTouch = false
                                }
                            }
                        },
                        update = { it.player = player },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    )
                }
                else -> RecordThumb(record, Modifier.fillMaxSize())
            }
        }

        // Top bar over the media.
        Row(
            Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerButton(Icons.Default.Close, "Close", onClose)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    "${pager.currentPage + 1} / ${records.size}",
                    style = TextStyle(fontFamily = t.mono, fontSize = 11.sp, color = t.accent.takeIf { t.dark } ?: Color(0xFFC6F432)),
                )
                current?.let {
                    Text(
                        listOf(platformName(it.platform), it.author.takeIf { a -> a.isNotBlank() }?.let { a -> "@$a" }, it.quality.takeIf { q -> q.isNotBlank() })
                            .filterNotNull().joinToString(" · "),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = t.body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                    )
                }
            }
            current?.let { r ->
                ViewerButton(Icons.Default.Share, "Share") { onShare(r) }
                ViewerButton(Icons.AutoMirrored.Filled.OpenInNew, "Open with another app") { onOpenWith(r) }
                ViewerButton(Icons.Default.Delete, "Delete") { onDelete(r) }
            }
        }
    }
}

@Composable
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

/** Pinch to zoom, drag to pan while zoomed (so the pager still swipes at 1×), double-tap to toggle. */
@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // transformable(canPan)
@Composable
private fun ZoomableImage(record: DownloadRecord) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = record.contentUri?.let(Uri::parse) ?: record.thumbnailUrl,
        contentDescription = record.caption.ifBlank { record.fileName },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(record.id) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
