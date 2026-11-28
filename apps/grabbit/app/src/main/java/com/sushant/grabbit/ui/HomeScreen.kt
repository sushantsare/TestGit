@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sushant.grabbit.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import com.sushant.grabbit.ui.components.pressSquish
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import androidx.core.app.ActivityCompat
import coil.compose.AsyncImage
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.engine.Engine
import com.sushant.grabbit.engine.MediaInfo
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.ui.components.AppCard
import com.sushant.grabbit.ui.components.BrandMark
import com.sushant.grabbit.ui.components.ErrorCard
import com.sushant.grabbit.ui.components.ImageTag
import com.sushant.grabbit.ui.components.SectionLabel
import com.sushant.grabbit.ui.components.SkeletonBlock
import com.sushant.grabbit.ui.theme.ImageScrim
import com.sushant.grabbit.ui.theme.LocalSizes
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.PillShape
import com.sushant.grabbit.ui.theme.TabularNums

@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier) {
    val sp = LocalSpacing.current
    val engine by vm.engine.collectAsState()
    val focus = LocalFocusManager.current
    val go = { focus.clearFocus(); vm.fetch() }
    val ctx = LocalContext.current
    val list = rememberLazyListState()
    val loaded = vm.fetchState is FetchState.Loaded

    // Permissions are asked here, the first time the user downloads, never at app start.
    var pending by remember { mutableStateOf<Triple<MediaInfo, String, DlOpts>?>(null) }
    var explain by remember { mutableStateOf<List<String>>(emptyList()) }
    var storageBlocked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val p = pending
        pending = null
        val storageDenied = Build.VERSION.SDK_INT <= 28 && res[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (storageDenied) storageBlocked = true
        else if (p != null) vm.download(p.first, p.second, p.third)
    }
    val startDownload: (MediaInfo, String, DlOpts) -> Unit = { info, q, o ->
        val need = neededPermissions(ctx)
        if (need.isEmpty()) vm.download(info, q, o) else { pending = Triple(info, q, o); explain = need }
    }

    // When video details arrive, bring the media card (and its Download button) into view
    LaunchedEffect(loaded) { if (loaded) list.animateScrollToItem(0) }

    if (explain.isNotEmpty()) PermissionExplainDialog(
        need = explain,
        onContinue = {
            if (Manifest.permission.POST_NOTIFICATIONS in explain) Prefs.notifAsked = true
            permLauncher.launch(explain.toTypedArray()); explain = emptyList()
        },
        onSkip = {
            Prefs.notifAsked = true
            val p = pending
            pending = null
            val storageNeeded = explain.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            explain = emptyList()
            if (storageNeeded) storageBlocked = true else if (p != null) vm.download(p.first, p.second, p.third)
        },
    )
    if (storageBlocked) StorageBlockedDialog(ctx) { storageBlocked = false }

    LazyColumn(
        modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = sp.screen, end = sp.screen, bottom = sp.xl),
        verticalArrangement = Arrangement.spacedBy(sp.m),
    ) {
        item(key = "appbar") { AppBar(vm.playIntro) { vm.playIntro = false } }
        if (!loaded) item(key = "hero") { Hero() }
        if (engine !is Engine.State.Ready) item(key = "engine") { EngineBanner(engine) { vm.restartEngine() } }
        item(key = "input") {
            LinkInput(
                url = vm.url,
                onUrl = {
                    vm.url = it
                    if (vm.fetchState is FetchState.Loaded || vm.fetchState is FetchState.Failed) vm.resetFetch()
                },
                onClear = { vm.url = ""; vm.resetFetch() },
                showButton = !loaded,
                busy = vm.fetchState is FetchState.Loading,
                focus = vm.focusUrl, onFocused = { vm.focusUrl = false },
                onGo = go,
                onCancel = { vm.resetFetch() },
            )
        }
        when (val f = vm.fetchState) {
            FetchState.Idle -> item(key = "how") { HowItWorks() }
            FetchState.Loading -> item(key = "loading") { MediaSkeleton() }
            is FetchState.Failed -> item(key = "error") {
                ErrorCard("Couldn't get this video", f.message, f.raw, onRetry = { vm.fetch() },
                    modifier = Modifier.testTag("fetchError"))
            }
            is FetchState.Loaded -> item(key = "media") { MediaCard(f.info) { i, q, o -> startDownload(i, q, o) } }
        }
    }
}

@Composable
private fun AppBar(playIntro: Boolean, onPlayed: () -> Unit) {
    val sp = LocalSpacing.current
    // The ring-and-arrow mark animates in once each time the app is opened
    val animate = remember { playIntro }
    LaunchedEffect(Unit) { if (playIntro) onPlayed() }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        com.sushant.grabbit.ui.components.GrabbitMark(32.dp, animate = animate, modifier = Modifier.testTag("grabbitMark"))
        Spacer(Modifier.width(sp.m))
        Text("Grabbit", style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
    }
}

@Composable
private fun Hero() {
    val sp = LocalSpacing.current
    Column(Modifier.padding(top = sp.xs, bottom = sp.s)) {
        Text("Grab anything.", style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = (-1).sp))
    }
}

@Composable
private fun LinkInput(
    url: String, onUrl: (String) -> Unit, onClear: () -> Unit,
    showButton: Boolean, busy: Boolean, onGo: () -> Unit, onCancel: () -> Unit = {},
    focus: Boolean = false, onFocused: () -> Unit = {},
) {
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focus) { if (focus) { runCatching { fr.requestFocus() }; onFocused() } }
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    AppCard {
        TextField(
            value = url,
            onValueChange = onUrl,
            modifier = Modifier.fillMaxWidth().focusRequester(fr).testTag("urlField"),
            placeholder = { Text("Paste a video link") },
            leadingIcon = { Icon(Icons.Rounded.Link, null) },
            trailingIcon = {
                if (url.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Clear link") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
        )
        AnimatedVisibility(showButton) {
            Column {
                Spacer(Modifier.height(sp.m))
                val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Button(
                    onClick = if (busy) onCancel else onGo,
                    modifier = Modifier.fillMaxWidth().height(sz.button).testTag("grabBtn")
                        .pressSquish(src, 0.97f),
                    enabled = busy || url.isNotBlank(),
                    shape = PillShape,
                    interactionSource = src,
                    colors = if (busy) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) else ButtonDefaults.buttonColors(),
                ) {
                    if (busy) {
                        // Expressive loading shape: keeps morphing between soft polygons while the link is read
                        androidx.compose.material3.LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(sp.s))
                        Text("Fetching…", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(sp.s))
                        Text("tap to cancel", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    } else Text("Grab", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun EngineBanner(state: Engine.State, onRetry: () -> Unit) {
    val sp = LocalSpacing.current
    if (state is Engine.State.Failed) {
        ErrorCard("Download engine didn't start", state.message, "", onRetry = onRetry)
        return
    }
    AppCard {
        Text("Setting up the download engine…", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(sp.s))
        LinearProgressIndicator(Modifier.fillMaxWidth().height(LocalSizes.current.progress), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        Spacer(Modifier.height(sp.s))
        Text("First launch only. Takes a few seconds.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HowItWorks() {
    val sp = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(sp.m)) {
        SectionLabel("How it works")
        AppCard(padding = sp.s) {
            Step(1, "Copy the link", "From any app: Share → Copy link")
            Step(2, "Paste it above", "Then tap Grab")
            Step(3, "Pick what you want", "Video, audio or photos — saved where you choose")
        }
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Share, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(sp.l))
                Text(
                    "Faster: tap Share in any app and pick Grabbit",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, sub: String) {
    val sp = LocalSpacing.current
    Row(Modifier.fillMaxWidth().padding(sp.s), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(LocalSizes.current.stepBadge)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(sp.l))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaSkeleton() {
    val sp = LocalSpacing.current
    AppCard(padding = sp.m) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.shapes.large)
        Spacer(Modifier.height(sp.l))
        SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(18.dp))
        Spacer(Modifier.height(sp.s))
        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(18.dp))
        Spacer(Modifier.height(sp.l))
        Text("Getting video details…", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = sp.xs))
    }
}

private data class QOpt(val key: String, val label: String, val size: Long)

private fun videoOptions(info: MediaInfo): List<QOpt> = buildList {
    add(QOpt("best", "Best", 0))
    info.heights.forEach { h -> add(QOpt("h$h", qualityLabel(h), info.sizes[h] ?: 0)) }
}

/** Best + each audio bitrate the site offers; size is the estimated MP3 at that bitrate. */
private fun audioOptions(info: MediaInfo): List<QOpt> = buildList {
    add(QOpt("audio", "Best", 0))
    info.audios.forEach { a ->
        val est = if (info.duration > 0) info.duration * a.kbps * 1000L / 8 else a.size
        add(QOpt("a${a.kbps}", "${a.kbps} kbps", est))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityChips(options: List<QOpt>, selected: String, onSelect: (String) -> Unit) {
    // Connected, sideways-scrolling row: equal widths, the chosen one fills and widens (Expressive)
    if (options.isEmpty()) return
    com.sushant.grabbit.ui.components.ConnectedGroup(
        options.map { com.sushant.grabbit.ui.components.SegOption(it.key, it.label, sub = fmtSize(it.size), tag = "q_${it.key}") },
        selected, onSelect, height = 56.dp, scroll = options.size > 4, itemWidth = 84.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, onColor = MaterialTheme.colorScheme.onSurface,
    )
}

private fun defaultVideoChoice(opts: List<QOpt>): String {
    val pref = Prefs.defaultQuality
    if (opts.any { it.key == pref }) return pref
    val want = pref.removePrefix("h").toIntOrNull()
    if (want != null) {
        opts.filter { it.key.startsWith("h") }.mapNotNull { it.key.drop(1).toIntOrNull() }
            .filter { it <= want }.maxOrNull()?.let { return "h$it" }
    }
    return opts.first().key
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun MediaCard(info: MediaInfo, framed: Boolean = true, onDownload: (MediaInfo, String, DlOpts) -> Unit) {
    // Photo posts (carousels, tweet photos, image links) get the photo picker instead
    if (info.gallery.isNotEmpty()) {
        GalleryCard(info, framed, onDownload)
        return
    }
    // Playlists get the video picker
    if (info.isPlaylist && info.entries.isNotEmpty()) {
        PlaylistCard(info, framed, onDownload)
        return
    }
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    val opts = remember(info) { videoOptions(info) }
    val startAudio = !info.hasVideo || Prefs.defaultQuality == "audio"
    var audio by rememberSaveable(info.url) { mutableStateOf(startAudio) }
    /** Image tab: save the video's cover image. */
    var image by rememberSaveable(info.url) { mutableStateOf(false) }
    var imgQuality by rememberSaveable(info.url) { mutableStateOf(Prefs.imageFormat) }
    var video by rememberSaveable(info.url) { mutableStateOf(defaultVideoChoice(opts)) }
    val audioOpts = remember(info) { audioOptions(info) }
    var aud by rememberSaveable(info.url) {
        mutableStateOf(Prefs.defaultQuality.takeIf { q -> audioOpts.any { it.key == q } } ?: "audio")
    }
    val chosenAudio = audioOpts.firstOrNull { it.key == aud }
    val st = remember(info) { OptsState(info) }
    val chosen = opts.firstOrNull { it.key == video }

    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            AsyncImage(info.thumbnail.ifBlank { null }, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to ImageScrim)))
            ImageTag(info.site, modifier = Modifier.align(Alignment.TopStart).padding(sp.m))
            val d = fmtDuration(info.duration)
            if (d.isNotEmpty()) ImageTag(d, modifier = Modifier.align(Alignment.BottomEnd).padding(sp.m))
        }
        Column(Modifier.padding(horizontal = sp.xs).padding(top = sp.l, bottom = sp.xs)) {
            EditableName(st.name, { st.name = it }) { m ->
                Text(st.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.8f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = m.padding(top = sp.s))
            }
            if (info.uploader.isNotBlank()) Text(info.uploader, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (info.isPlaylist) Row(Modifier.padding(top = sp.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(sp.s))
                Text("Playlist · ${info.count} videos", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }

            if (info.hasVideo) {
                Spacer(Modifier.height(sp.l))
                val tabs = buildList {
                    add(com.sushant.grabbit.ui.components.SegOption("video", "Video", Icons.Rounded.Movie, tag = "tab_video"))
                    add(com.sushant.grabbit.ui.components.SegOption("audio", "Audio", Icons.Rounded.MusicNote, tag = "tab_audio"))
                    if (info.thumbnail.isNotBlank()) add(com.sushant.grabbit.ui.components.SegOption("image", "Image", Icons.Rounded.Image, tag = "imageTab"))
                }
                com.sushant.grabbit.ui.components.ConnectedGroup(
                    tabs, if (image) "image" else if (audio) "audio" else "video",
                    { k -> image = k == "image"; audio = k == "audio" },
                )
            }
            Spacer(Modifier.height(sp.m))
            if (image) CoverImageOptions(info, imgQuality, { imgQuality = it }, st, onDownload)
            else {
                if (audio) QualityChips(audioOpts, aud) { aud = it }
                else if (info.hasVideo) QualityChips(opts, video) { video = it }
                Spacer(Modifier.height(sp.m))
                DownloadOptionsRow(info, audio, st)
                Spacer(Modifier.height(sp.l))
                SaveToSection(st.folder, { st.folder = it }, audio)
                Spacer(Modifier.height(sp.l))
                val fmtName = AUDIO_FORMAT_LABELS.firstOrNull { it.first == st.audioFormat }?.second ?: "MP3"
                val label = when {
                    info.isPlaylist -> "Download all (${info.count})"
                    audio && (chosenAudio == null || chosenAudio.key == "audio") -> "Download $fmtName · best"
                    audio -> "Download $fmtName · ${chosenAudio!!.label}" + fmtSize((chosenAudio.size * st.keptShare).toLong()).let { if (it.isEmpty()) "" else " · $it" }
                    chosen == null || chosen.key == "best" -> if (st.trimmed) "Download best quality · trimmed" else "Download best quality"
                    else -> "Download ${chosen.label}" + fmtSize((chosen.size * st.keptShare).toLong()).let { if (it.isEmpty()) "" else " · $it" }
                }
                // Split button: the big part downloads, the small part picks the format for this download
                com.sushant.grabbit.ui.components.SplitButton(
                    label = {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.width(sp.s))
                        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNums),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    onClick = { onDownload(info, if (audio) aud else video, st.toOpts(audio)) },
                    menuDescription = if (audio) "Audio format" else "Video format",
                ) { close -> FormatMenu(audio, st, close) }

            }
        }
    }
    if (framed) AppCard(padding = sp.m) { body() } else Column { body() }
}

/** Permissions still needed before a download: notifications (asked once, optional) and storage on Android 9 and older. */
internal fun neededPermissions(ctx: android.content.Context): List<String> = buildList {
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    if (Build.VERSION.SDK_INT >= 33 && !Prefs.notifAsked && !granted(Manifest.permission.POST_NOTIFICATIONS))
        add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT <= 28 && !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

@Composable
internal fun PermissionExplainDialog(need: List<String>, onContinue: () -> Unit, onSkip: () -> Unit) {
    val storage = Manifest.permission.WRITE_EXTERNAL_STORAGE in need
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Before your first download") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Build.VERSION.SDK_INT >= 33 && Manifest.permission.POST_NOTIFICATIONS in need)
                    Text("Notifications: see download progress and know when a video is ready. Optional — downloads work without it.")
                if (storage)
                    Text("Storage: on this Android version, Grabbit needs it to save videos to Movies/Grabbit and Music/Grabbit.")
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onSkip) { Text(if (storage) "Cancel" else "Not now") } },
    )
}

@Composable
internal fun StorageBlockedDialog(ctx: android.content.Context, onClose: () -> Unit) {
    val permanently = (ctx as? Activity)?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } ?: false
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Storage access needed") },
        text = {
            Text(
                "Grabbit can't save videos on this Android version without storage access." +
                    if (permanently) " Turn it on in Settings → Permissions → Storage." else " Tap Download again to allow it."
            )
        },
        confirmButton = {
            if (permanently) TextButton(onClick = {
                onClose()
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("Open Settings") } else TextButton(onClick = onClose) { Text("OK") }
        },
        dismissButton = { if (permanently) TextButton(onClick = onClose) { Text("Cancel") } },
    )
}


/** The split button's menu: audio format (MP3, M4A…) or video format (most compatible / highest quality) for this download. */
@Composable
internal fun FormatMenu(audio: Boolean, st: OptsState, close: () -> Unit) {
    Text(if (audio) "Audio format" else "Video format", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    val items = if (audio) AUDIO_FORMAT_LABELS.map { (k, l) -> Triple("af_$k", l, st.audioFormat == k) to { st.audioFormat = k } }
    else listOf(
        Triple("vf_0", "Most compatible (MP4)", st.videoFormat == 0) to { st.videoFormat = 0 },
        Triple("vf_1", "Highest quality", st.videoFormat == 1) to { st.videoFormat = 1 },
    )
    items.forEach { (t, pick) ->
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(t.second) },
            onClick = { pick(); close() },
            leadingIcon = { if (t.third) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
            modifier = Modifier.testTag(t.first),
        )
    }
}
