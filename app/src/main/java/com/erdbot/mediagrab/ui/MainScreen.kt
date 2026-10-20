package com.erdbot.mediagrab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.erdbot.mediagrab.download.DownloadRecord
import com.erdbot.mediagrab.download.DownloadStatus
import com.erdbot.mediagrab.extract.MediaItem
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Variant
import com.erdbot.mediagrab.ui.theme.Mg
import com.erdbot.mediagrab.ui.theme.ThemeId
import com.erdbot.mediagrab.update.AppUpdate

/** Root UI: Grab and Media tabs, plus the Settings screen over them. */
@Composable
fun AppScreen(viewModel: MainViewModel, tab: Int, onTab: (Int) -> Unit, onLogin: (site: String) -> Unit) {
    val t = Mg
    val state by viewModel.state.collectAsStateWithLifecycle()
    val records by viewModel.downloads.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var viewerId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val openViewer = { r: DownloadRecord -> viewerId = r.id }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(t.background)) {
        if (settingsOpen) {
            SettingsScreen(viewModel, state, onBack = { settingsOpen = false }, onLogin = onLogin)
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    if (tab == 0) {
                        GrabTab(viewModel, state, records, onOpenSettings = { settingsOpen = true }, onOpenItem = openViewer, onOpenMedia = { onTab(1) }, onLogin = onLogin)
                    } else {
                        MediaTab(viewModel, onOpen = openViewer)
                    }
                }
                val active = records.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
                BottomBar(tab, onTab, active)
            }
        }
        viewerId?.let { start ->
            ViewerScreen(
                records = records.filter { it.status == DownloadStatus.DONE },
                startId = start,
                onClose = { viewerId = null },
                onShare = { share(context, it) },
                onOpenWith = { openExternally(context, it) },
                onDelete = { viewModel.delete(it) },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp))
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, activeDownloads: Int) {
    val t = Mg
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.background)
            .then(if (t.borderWidth >= 2.dp) Modifier.border(t.borderWidth, t.outline) else Modifier)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
    ) {
        BarItem(t.grabTab, Icons.Default.Download, tab == 0, 0, Modifier.weight(1f)) { onTab(0) }
        BarItem(t.mediaTab, Icons.Default.PhotoLibrary, tab == 1, activeDownloads, Modifier.weight(1f)) { onTab(1) }
    }
}

@Composable
private fun BarItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val t = Mg
    Column(
        modifier.clickable(role = Role.Tab, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .clip(RoundedCornerShape(if (t.radius > 0.dp) 16.dp else 0.dp))
                    .background(if (selected) t.accentSoft else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) (if (t.id == ThemeId.LOUD) t.text else t.accent) else t.muted)
            }
            if (badge > 0) {
                Text(
                    "$badge",
                    Modifier.align(Alignment.TopEnd).clip(CircleShape).background(t.accent).padding(horizontal = 6.dp),
                    style = TextStyle(fontFamily = t.body, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = t.onAccent),
                )
            }
        }
        Text(
            t.caps(label),
            style = TextStyle(fontFamily = t.body, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) t.text else t.muted),
        )
    }
}

@Composable
private fun GrabTab(
    viewModel: MainViewModel,
    state: UiState,
    records: List<DownloadRecord>,
    onOpenSettings: () -> Unit,
    onOpenItem: (DownloadRecord) -> Unit,
    onOpenMedia: () -> Unit,
    onLogin: (String) -> Unit,
) {
    val t = Mg
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Header(loggedIn = state.instagramLoggedIn || state.facebookLoggedIn || state.xLoggedIn || state.youtubeLoggedIn, onOpenSettings) }
        state.update?.let { update ->
            item(key = "update") { UpdateCard(update, state.updateProgress, viewModel::installUpdate, viewModel::dismissUpdate) }
        }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t.id == ThemeId.NIGHT_SHIFT) MgLabel("● Ready", color = t.accent)
                MgDisplay(t.headline, if (t.id == ThemeId.INDEX) 32.sp else 44.sp)
            }
        }
        item { LinkCard(viewModel, state) }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = t.accent)
                }
            }
        }
        state.error?.let { error ->
            item {
                val site = state.loginSite
                if (site != null) PrivateCard(site, error, onLogin) else ErrorCard(error)
            }
        }
        state.post?.takeIf { it.items.isNotEmpty() }?.let { post ->
            item { PostHeader(post) }
            if (post.items.size > 1) {
                item { MgButton("Save all ${post.items.size}", viewModel::downloadAll, Modifier.fillMaxWidth()) }
            }
            post.items.forEachIndexed { index, mediaItem ->
                item(key = "item$index") {
                    ResultCard(
                        item = mediaItem,
                        selected = state.selected[index] ?: mediaItem.best,
                        onSelect = { viewModel.select(index, it) },
                        onSave = { viewModel.download(index) },
                    )
                }
            }
        }

        val recent = records.filter { it.status == DownloadStatus.DONE }.take(8)
        if (recent.isNotEmpty() && state.post == null) {
            item { LastRoll(recent, onOpenItem, onOpenMedia) }
        }
    }
}

@Composable
private fun Header(loggedIn: Boolean, onOpenSettings: () -> Unit) {
    val t = Mg
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).rotate(-6f).clip(RoundedCornerShape(if (t.radius > 0.dp) 9.dp else 0.dp)).background(t.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = t.onAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            t.caps("MediaGrab"),
            Modifier.weight(1f),
            style = TextStyle(fontFamily = t.display, fontWeight = t.displayWeight, fontSize = 21.sp, color = t.text),
        )
        Box(
            Modifier.size(44.dp).mgSurface(t, shadow = false).clickable(role = Role.Button, onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PersonOutline, contentDescription = "Settings and accounts", tint = t.text)
            if (loggedIn) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(9.dp).clip(CircleShape).background(t.accent))
            }
        }
    }
}

@Composable
private fun LinkCard(viewModel: MainViewModel, state: UiState) {
    val t = Mg
    val clipboard = LocalClipboardManager.current
    MgCard {
        MgLabel("Link")
        Row(
            Modifier.fillMaxWidth().height(54.dp).mgSurface(t, fill = t.background, shadow = false).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (state.input.isEmpty()) MgBody("Paste a post, reel or video link", color = t.muted, maxLines = 1)
                BasicTextField(
                    value = state.input,
                    onValueChange = viewModel::onInput,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = t.body, fontSize = 15.sp, color = t.text),
                    cursorBrush = SolidColor(t.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { viewModel.fetch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(44.dp).clickable(role = Role.Button) { clipboard.getText()?.text?.let(viewModel::onInput) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = t.text)
            }
        }

        state.clipSuggestion?.let { link ->
            Row(
                Modifier.fillMaxWidth().mgSurface(t, fill = t.accentSoft, shadow = false, border = t.accentSoft).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MgBody("Copied a link — use it?", size = 13.sp, weight = FontWeight.SemiBold)
                    Text(
                        link.removePrefix("https://").removePrefix("www."), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = t.mono, fontSize = 11.sp, color = t.muted),
                    )
                }
                MgButton("Use", viewModel::useClipSuggestion, height = 40.dp, kind = ButtonKind.PRIMARY)
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = viewModel::dismissClipSuggestion), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = t.muted, modifier = Modifier.size(18.dp))
                }
            }
        }

        MgButton(
            if (state.loading) "Fetching…" else t.grabButton,
            viewModel::fetch,
            Modifier.fillMaxWidth(),
            enabled = !state.loading,
        )
        Text(
            "X · INSTAGRAM · FACEBOOK · YOUTUBE · TIKTOK",
            Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = TextStyle(fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 1.sp, color = t.muted),
        )
    }
}

/** "This one's private" — explains why and offers the login that fixes it. */
@Composable
private fun PrivateCard(site: String, message: String, onLogin: (String) -> Unit) {
    val t = Mg
    val siteName = when (site) {
        LoginActivity.SITE_FACEBOOK -> "Facebook"
        LoginActivity.SITE_X -> "X"
        LoginActivity.SITE_YOUTUBE -> "YouTube"
        else -> "Instagram"
    }
    MgCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(if (t.radius > 0.dp) 12.dp else 0.dp)).background(t.text),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = t.background)
            }
            MgDisplay("This one's private.", 24.sp)
        }
        MgBody(message, size = 14.sp, color = t.muted)
        MgButton("Log in to $siteName", { onLogin(site) }, Modifier.fillMaxWidth())
        MgLabel("Your password goes to ${if (site == LoginActivity.SITE_YOUTUBE) "Google" else siteName}, not us")
    }
}

/** "Version x is out" with release notes; installs from the GitHub release. */
@Composable
private fun UpdateCard(update: AppUpdate, progress: Float?, onUpdate: () -> Unit, onLater: () -> Unit) {
    val t = Mg
    MgCard {
        MgLabel("Update available", color = t.accent)
        MgDisplay("MediaGrab ${update.version}", 24.sp)
        val notes = update.notes.lineSequence().map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        if (notes.isNotEmpty()) MgBody(notes, size = 13.sp, color = t.muted, maxLines = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val label = when {
                progress == null -> "Update"
                progress < 0f -> "Downloading…"
                else -> "Downloading ${(progress * 100).toInt()}%"
            }
            MgButton(label, onUpdate, Modifier.weight(1f), enabled = progress == null)
            if (progress == null) MgButton("Later", onLater, kind = ButtonKind.GHOST)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val t = Mg
    Column(
        Modifier.fillMaxWidth().mgSurface(t, shadow = false, border = MaterialErrorColor).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MgLabel("Couldn't grab that", color = MaterialErrorColor)
        MgBody(message, size = 14.sp)
    }
}

private val MaterialErrorColor = Color(0xFFE5484D)

@Composable
private fun PostHeader(post: MediaPost) {
    val t = Mg
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            listOf(platformName(post.platform), post.author.takeIf { it.isNotBlank() }?.let { "@$it" }).filterNotNull().joinToString(" · "),
            style = TextStyle(fontFamily = t.mono, fontSize = 12.sp, color = t.muted),
        )
        if (post.caption.isNotBlank()) {
            Text(
                post.caption, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = t.display, fontWeight = if (t.displayWeight == FontWeight.Normal) FontWeight.Normal else FontWeight.Bold,
                    fontSize = 20.sp, lineHeight = 24.sp, color = t.text),
            )
        }
    }
}

/** One photo/video of the post: preview, quality tiles and the save button. */
@Composable
private fun ResultCard(item: MediaItem, selected: Variant, onSelect: (Variant) -> Unit, onSave: () -> Unit) {
    val t = Mg
    MgCard(padding = 12.dp) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(if (t.radius > 0.dp) 12.dp else 0.dp)).background(t.surfaceAlt)) {
            AsyncImage(item.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.type != MediaType.IMAGE) {
                Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color(0xE60D0E10)), contentAlignment = Alignment.Center) {
                    Icon(if (item.type == MediaType.AUDIO) Icons.Default.GraphicEq else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        }
        if (item.variants.size > 1) {
            MgLabel("Quality")
            item.variants.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { v -> QualityTile(v, v == selected, Modifier.weight(1f)) { onSelect(v) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            MgBody(
                when {
                    selected.mimeType.startsWith("audio") -> "Sound only — good for music and podcasts."
                    selected.audioUrl != null -> "Picture and sound download separately, then merge on your phone."
                    else -> "Ready-made file, fastest to save."
                },
                size = 12.sp, color = t.muted,
            )
        }
        MgButton(
            when {
                selected.mimeType.startsWith("audio") -> "Save audio"
                item.type == MediaType.IMAGE -> "Save photo"
                selected.height > 0 -> "Save ${selected.label} video"
                else -> "Save video"
            },
            onSave,
            Modifier.fillMaxWidth(),
            leading = { Icon(Icons.Default.Download, contentDescription = null, tint = t.onAccent, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun QualityTile(v: Variant, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = Mg
    Column(
        modifier
            .height(60.dp)
            .mgSurface(t, fill = if (selected) t.accentSoft else t.background, shadow = false, border = if (selected) t.accent else t.outline)
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (v.mimeType.startsWith("audio")) "Audio" else v.label,
            style = TextStyle(fontFamily = t.display, fontWeight = if (t.displayWeight == FontWeight.Normal) FontWeight.Normal else FontWeight.Bold, fontSize = 16.sp, color = t.text),
        )
        Text(
            when {
                v.mimeType.startsWith("audio") -> "M4A"
                v.audioUrl != null -> "MERGE"
                else -> "READY"
            },
            style = TextStyle(fontFamily = t.mono, fontSize = 9.sp, color = t.muted),
        )
    }
}

/** Recent saves; on Night Shift drawn as a strip of film with sprocket holes. */
@Composable
private fun LastRoll(recent: List<DownloadRecord>, onOpenItem: (DownloadRecord) -> Unit, onOpenMedia: () -> Unit) {
    val t = Mg
    val film = t.id == ThemeId.NIGHT_SHIFT
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MgLabel(if (film) "Last roll" else "Recent", Modifier.weight(1f))
            Text(
                t.caps("Open ${t.mediaTab}"),
                Modifier.clickable(role = Role.Button, onClick = onOpenMedia).padding(8.dp),
                style = TextStyle(fontFamily = t.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (t.id == ThemeId.LOUD) t.text else t.accent),
            )
        }
        Column(
            Modifier.fillMaxWidth().then(if (film) Modifier.background(Color(0xFF050506)).padding(vertical = 8.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (film) Sprockets()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = if (film) 10.dp else 0.dp)) {
                items(recent, key = { it.id }) { r ->
                    Box(
                        Modifier.size(width = 116.dp, height = 86.dp)
                            .then(if (film) Modifier else Modifier.mgSurface(t, shadow = false))
                            .background(t.surfaceAlt)
                            .clickable { onOpenItem(r) },
                    ) {
                        RecordThumb(r, Modifier.fillMaxSize())
                        PlatformTag(r.platform, r.quality, Modifier.align(Alignment.BottomStart).padding(5.dp))
                    }
                }
            }
            if (film) Sprockets()
        }
    }
}

@Composable
private fun Sprockets() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(18) { Box(Modifier.size(width = 8.dp, height = 5.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF2A2D34))) }
    }
}
