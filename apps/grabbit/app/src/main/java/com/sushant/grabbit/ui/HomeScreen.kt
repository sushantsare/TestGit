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
        item(key = "appbar") { AppBar() }
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
                onGo = go,
            )
        }
        when (val f = vm.fetchState) {
            FetchState.Idle -> item(key = "how") { HowItWorks() }
            FetchState.Loading -> item(key = "loading") { MediaSkeleton() }
            is FetchState.Failed -> item(key = "error") {
                ErrorCard("Couldn't get this video", f.message, f.raw, onRetry = { vm.fetch() },
                    modifier = Modifier.testTag("fetchError"))
            }
            is FetchState.Loaded -> item(key = "media") { MediaCard(f.info) { q, o -> startDownload(f.info, q, o) } }
        }
    }
}

@Composable
private fun AppBar() {
    val sp = LocalSpacing.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Spacer(Modifier.width(sp.m))
        Text("Grabbit", style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
    }
}

@Composable
private fun Hero() {
    val sp = LocalSpacing.current
    Column(Modifier.padding(top = sp.xs, bottom = sp.s)) {
        Text("Paste a link.\nGet the video.", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(sp.s))
        Text(
            "YouTube, Instagram, Threads, X, Facebook and 1,000+ more sites",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkInput(
    url: String, onUrl: (String) -> Unit, onClear: () -> Unit,
    showButton: Boolean, busy: Boolean, onGo: () -> Unit,
) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    AppCard {
        TextField(
            value = url,
            onValueChange = onUrl,
            modifier = Modifier.fillMaxWidth().testTag("urlField"),
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
                Button(
                    onClick = onGo,
                    modifier = Modifier.fillMaxWidth().height(sz.button).testTag("grabBtn"),
                    enabled = !busy && url.isNotBlank(),
                    shape = PillShape,
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(sp.s))
                    Text(if (busy) "Getting video…" else "Grab video", style = MaterialTheme.typography.titleMedium)
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
            Step(2, "Paste it above", "Then tap Grab video")
            Step(3, "Pick a quality", "Video or MP3, saved to your gallery")
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
    val sp = LocalSpacing.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(sp.s)) {
        options.forEach { o ->
            val sel = selected == o.key
            FilterChip(
                selected = sel,
                onClick = { onSelect(o.key) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.label, style = MaterialTheme.typography.labelLarge)
                        val s = fmtSize(o.size)
                        if (s.isNotEmpty()) Text(" · $s", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = if (sel) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("q_${o.key}"),
            )
        }
    }
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
internal fun MediaCard(info: MediaInfo, framed: Boolean = true, onDownload: (String, DlOpts) -> Unit) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    val opts = remember(info) { videoOptions(info) }
    val startAudio = !info.hasVideo || Prefs.defaultQuality == "audio"
    var audio by rememberSaveable(info.url) { mutableStateOf(startAudio) }
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
            Text(info.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.8f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !audio, onClick = { audio = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { SegmentedButtonDefaults.Icon(!audio) { Icon(Icons.Rounded.Movie, null, Modifier.size(sz.iconSmall)) } },
                    ) { Text("Video") }
                    SegmentedButton(
                        selected = audio, onClick = { audio = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { SegmentedButtonDefaults.Icon(audio) { Icon(Icons.Rounded.MusicNote, null, Modifier.size(sz.iconSmall)) } },
                    ) { Text("Audio") }
                }
            }
            Spacer(Modifier.height(sp.m))
            if (audio) QualityChips(audioOpts, aud) { aud = it }
            else if (info.hasVideo) QualityChips(opts, video) { video = it }
            Spacer(Modifier.height(sp.m))
            DownloadOptionsRow(info, audio, st)
            Spacer(Modifier.height(sp.l))
            val fmtName = AUDIO_FORMAT_LABELS.firstOrNull { it.first == st.audioFormat }?.second ?: "MP3"
            val label = when {
                info.isPlaylist -> "Download all (${info.count})"
                audio && (chosenAudio == null || chosenAudio.key == "audio") -> "Download $fmtName · best"
                audio -> "Download $fmtName · ${chosenAudio!!.label}" + fmtSize((chosenAudio.size * st.keptShare).toLong()).let { if (it.isEmpty()) "" else " · $it" }
                chosen == null || chosen.key == "best" -> if (st.trimmed) "Download best quality · trimmed" else "Download best quality"
                else -> "Download ${chosen.label}" + fmtSize((chosen.size * st.keptShare).toLong()).let { if (it.isEmpty()) "" else " · $it" }
            }
            Button(
                onClick = { onDownload(if (audio) aud else video, st.toOpts(audio)) },
                modifier = Modifier.fillMaxWidth().height(sz.button).testTag("downloadBtn"),
                shape = PillShape,
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(sp.s))
                Text(label, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNums),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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
