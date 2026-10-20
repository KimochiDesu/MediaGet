package com.erdbot.mediagrab.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.erdbot.mediagrab.download.DownloadRecord
import com.erdbot.mediagrab.download.DownloadStatus
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.ui.theme.MediaLayout
import com.erdbot.mediagrab.ui.theme.Mg
import java.util.Calendar
import java.util.Locale

/** Everything the user has downloaded, newest first. Tap to open, long-press for more. */
@Composable
fun MediaTab(viewModel: MainViewModel, onOpen: (DownloadRecord) -> Unit) {
    val t = Mg
    val records by viewModel.downloads.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<Platform?>(null) }
    LaunchedEffect(Unit) { viewModel.pruneMissing() }

    // Frame numbers count up from the oldest download, like exposures on a roll.
    val numbers = records.mapIndexed { i, r -> r.id to String.format(Locale.US, "%03d", records.size - i) }.toMap()
    val inProgress = records.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
    val shown = records.filter { it !in inProgress && (filter == null || it.platform == filter) }
    val columns = when (t.mediaLayout) {
        MediaLayout.CONTACT_SHEET -> 3
        MediaLayout.POLAROIDS -> 2
        MediaLayout.CARDS, MediaLayout.LIST -> 1
    }
    val full = { _: Any -> GridItemSpan(columns) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (t.mediaLayout == MediaLayout.POLAROIDS) 16.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(
            when (t.mediaLayout) {
                MediaLayout.POLAROIDS -> 18.dp
                MediaLayout.CARDS -> 14.dp
                MediaLayout.LIST -> 0.dp
                MediaLayout.CONTACT_SHEET -> 4.dp
            },
        ),
    ) {
        item(span = full, key = "title") {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.mediaLayout == MediaLayout.CONTACT_SHEET) MgLabel("Contact sheet · ${records.size} frames")
                MgDisplay(t.mediaTab, if (t.mediaLayout == MediaLayout.LIST) 56.sp else 34.sp)
            }
        }
        item(span = full, key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MgChip("All", filter == null) { filter = null }
                Platform.entries.forEach { p -> MgChip(platformName(p), filter == p) { filter = p } }
            }
        }
        items(inProgress, key = { "run" + it.id }, span = { full(it) }) { r -> ProgressCard(r, Modifier.padding(bottom = 12.dp)) }

        if (shown.isEmpty()) {
            item(span = full, key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    MgBody("Nothing here yet.\nWhat you save will show up here.", color = t.muted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
            }
        }

        val groups = if (t.mediaLayout == MediaLayout.POLAROIDS || t.mediaLayout == MediaLayout.CARDS) {
            listOf("" to shown)
        } else {
            shown.groupBy { dayLabel(it.createdAt) }.toList()
        }
        groups.forEach { (day, list) ->
            if (day.isNotEmpty()) {
                item(span = full, key = "day$day") { MgLabel(day, Modifier.padding(top = 8.dp, bottom = 8.dp)) }
            }
            if (t.mediaLayout == MediaLayout.LIST) {
                item(span = full, key = "head$day") { ListHeader() }
            }
            items(list, key = { it.id }) { r ->
                MediaEntry(r, numbers[r.id].orEmpty(), onOpen = { onOpen(r) }, onRetry = { viewModel.retry(r) }, onDelete = { viewModel.delete(r) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaEntry(record: DownloadRecord, number: String, onOpen: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit) {
    val t = Mg
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val click = Modifier.combinedClickable(
        onClick = { if (record.status == DownloadStatus.DONE) onOpen() else menu = true },
        onLongClick = { menu = true },
    )
    Box {
        when (t.mediaLayout) {
            MediaLayout.CONTACT_SHEET -> Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(t.surfaceAlt).then(click)) {
                RecordThumb(record, Modifier.fillMaxSize())
                Text("#$number", Modifier.align(Alignment.TopStart).padding(5.dp),
                    style = TextStyle(fontFamily = t.mono, fontSize = 9.sp, color = Color(0xFFD0D3D9)))
                PlatformTag(record.platform, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp))
                if (record.quality.isNotBlank()) {
                    Text(record.quality, Modifier.align(Alignment.BottomEnd).padding(5.dp).background(Color(0xE60D0E10)).padding(horizontal = 4.dp, vertical = 2.dp),
                        style = TextStyle(fontFamily = t.mono, fontSize = 9.sp, color = Color.White))
                }
                StatusOverlay(record, Modifier.align(Alignment.Center))
            }

            MediaLayout.POLAROIDS -> {
                val tilt = if (number.lastOrNull()?.digitToIntOrNull()?.rem(2) == 0) 2f else -2f
                Column(
                    Modifier.rotate(tilt).background(t.surface).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp).then(click),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(t.surfaceAlt)) {
                        RecordThumb(record, Modifier.fillMaxSize())
                        StatusOverlay(record, Modifier.align(Alignment.Center))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.author.ifBlank { record.quality.ifBlank { "saved" } }, Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = t.hand ?: t.body, fontSize = 19.sp, color = t.text),
                        )
                        Text(platformName(record.platform), style = TextStyle(fontFamily = t.body, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = t.muted))
                    }
                }
            }

            MediaLayout.CARDS -> Row(Modifier.fillMaxWidth().height(92.dp).mgSurface(t).then(click)) {
                Box(Modifier.size(92.dp).background(t.surfaceAlt)) {
                    RecordThumb(record, Modifier.fillMaxSize())
                    StatusOverlay(record, Modifier.align(Alignment.Center))
                }
                Box(Modifier.width(t.borderWidth).fillMaxSize().background(t.outline))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    MgBody(titleOf(record), size = 15.sp, weight = FontWeight.Bold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatformTag(record.platform)
                        Text(metaOf(record).uppercase(), style = TextStyle(fontFamily = t.mono, fontSize = 11.sp, color = t.text))
                    }
                }
            }

            MediaLayout.LIST -> Column(click) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(number, Modifier.width(32.dp), style = TextStyle(fontFamily = t.mono, fontSize = 10.sp, color = t.muted))
                    Column(Modifier.weight(1f)) {
                        MgBody(titleOf(record), size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                        Text(
                            if (record.status == DownloadStatus.FAILED) "FAILED — TAP FOR OPTIONS" else metaOf(record).uppercase(),
                            style = TextStyle(fontFamily = t.mono, fontSize = 9.sp, color = if (record.status == DownloadStatus.FAILED) Color(0xFFD02A2A) else t.muted),
                        )
                    }
                    Text(platformShort(record.platform), Modifier.width(28.dp), style = TextStyle(fontFamily = t.mono, fontSize = 10.sp, color = t.text))
                }
                HorizontalDivider(color = t.outline.copy(alpha = 0.18f))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (record.status == DownloadStatus.DONE) {
                DropdownMenuItem(text = { Text("Play / view") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; share(context, record) })
                DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openExternally(context, record) })
            }
            if (record.status == DownloadStatus.FAILED) {
                DropdownMenuItem(text = { Text("Retry") }, onClick = { menu = false; onRetry() })
            }
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun ListHeader() {
    val t = Mg
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("NO." to 32.dp, "TITLE" to null, "SRC" to 28.dp).forEach { (label, w) ->
                Text(label, if (w != null) Modifier.width(w) else Modifier.weight(1f), style = TextStyle(fontFamily = t.mono, fontSize = 9.sp, color = t.muted))
            }
        }
        HorizontalDivider(color = t.outline, thickness = 1.dp)
    }
}

/** Running download, pinned above the grid. */
@Composable
private fun ProgressCard(record: DownloadRecord, modifier: Modifier = Modifier) {
    val t = Mg
    Row(
        modifier.fillMaxWidth().mgSurface(t).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(if (t.radius > 0.dp) 10.dp else 0.dp)).background(t.surfaceAlt)) {
            RecordThumb(record, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                MgBody(titleOf(record), Modifier.weight(1f), size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    if (record.status == DownloadStatus.RUNNING && record.progress >= 0) "${(record.progress * 100).toInt()}%" else "…",
                    style = TextStyle(fontFamily = t.mono, fontSize = 12.sp, color = if (t.id.name == "LOUD") t.text else t.accent),
                )
            }
            if (record.status == DownloadStatus.RUNNING && record.progress in 0f..1f) {
                LinearProgressIndicator(progress = { record.progress }, Modifier.fillMaxWidth(), color = t.accent, trackColor = t.surfaceAlt)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.accent, trackColor = t.surfaceAlt)
            }
            MgLabel(if (record.status == DownloadStatus.QUEUED) "Waiting" else "Downloading · ${platformName(record.platform)} ${record.quality}")
        }
    }
}

@Composable
private fun StatusOverlay(record: DownloadRecord, modifier: Modifier) {
    val icon = when {
        record.status == DownloadStatus.FAILED -> Icons.Default.ErrorOutline
        record.mediaType == MediaType.AUDIO -> Icons.Default.GraphicEq
        record.mediaType != MediaType.IMAGE -> Icons.Default.PlayArrow
        else -> null
    } ?: return
    Box(modifier.size(36.dp).clip(CircleShape).background(Color(0xCC0D0E10)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = if (record.status == DownloadStatus.FAILED) "Failed" else null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Thumbnail from the saved file itself (10% in, since videos often open on black), else the post's preview. */
@Composable
fun RecordThumb(record: DownloadRecord, modifier: Modifier) {
    val context = LocalContext.current
    val fileUri = record.contentUri?.takeIf { record.status == DownloadStatus.DONE && record.mediaType != MediaType.AUDIO }
    AsyncImage(
        model = if (fileUri != null) {
            ImageRequest.Builder(context).data(Uri.parse(fileUri)).videoFramePercent(0.1).build()
        } else {
            record.thumbnailUrl
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

private fun titleOf(r: DownloadRecord) = r.caption.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
    ?: r.author.takeIf { it.isNotBlank() }?.let { "@$it" }
    ?: r.fileName

private fun metaOf(r: DownloadRecord) = listOf(
    r.quality.takeIf { it.isNotBlank() },
    r.sizeBytes.takeIf { it > 0 }?.let(::formatSize),
).filterNotNull().joinToString(" · ").ifEmpty { platformName(r.platform) }

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}

private fun dayLabel(millis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(day, today)) return "Yesterday"
    return "Earlier"
}

/** Hands the file to another app (the viewer's "Open with" and the long-press menu). */
fun openExternally(context: Context, record: DownloadRecord) {
    val uri = record.contentUri?.let(Uri::parse) ?: return
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, record.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(view, "Open with"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
    }
}

fun share(context: Context, record: DownloadRecord) {
    val uri = record.contentUri?.let(Uri::parse) ?: return
    val send = Intent(Intent.ACTION_SEND).setType(record.mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
