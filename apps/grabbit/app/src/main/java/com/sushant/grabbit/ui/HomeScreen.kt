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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sushant.grabbit.R
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.engine.Engine
import com.sushant.grabbit.engine.MediaInfo
import androidx.core.app.ActivityCompat

@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier) {
    val engine by vm.engine.collectAsState()
    val focus = LocalFocusManager.current
    val go = { focus.clearFocus(); vm.fetch() }
    val ctx = LocalContext.current
    val list = rememberLazyListState()

    // Permissions are asked here, the first time the user downloads, never at app start.
    var pending by remember { mutableStateOf<Pair<MediaInfo, String>?>(null) }
    var explain by remember { mutableStateOf<List<String>>(emptyList()) }
    var storageBlocked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val p = pending
        pending = null
        val storageDenied = Build.VERSION.SDK_INT <= 28 && res[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (storageDenied) storageBlocked = true
        else if (p != null) vm.download(p.first, p.second)
    }
    val startDownload: (MediaInfo, String) -> Unit = { info, q ->
        val need = neededPermissions(ctx)
        if (need.isEmpty()) vm.download(info, q) else { pending = info to q; explain = need }
    }

    // When video details arrive, bring the card (and its Download button) into view
    LaunchedEffect(vm.fetchState) {
        if (vm.fetchState is FetchState.Loaded) list.animateScrollToItem(if (engine !is Engine.State.Ready) 3 else 2)
    }

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
            if (storageNeeded) storageBlocked = true else if (p != null) vm.download(p.first, p.second)
        },
    )
    if (storageBlocked) StorageBlockedDialog(ctx) { storageBlocked = false }

    LazyColumn(
        modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header() }
        if (engine !is Engine.State.Ready) item { EngineBanner(engine) { vm.restartEngine() } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vm.url,
                    onValueChange = { vm.url = it },
                    modifier = Modifier.fillMaxWidth().testTag("urlField"),
                    placeholder = { Text("Paste a video link here") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        if (vm.url.isNotEmpty()) IconButton(onClick = { vm.url = ""; vm.resetFetch() }) {
                            Icon(Icons.Rounded.Close, "Clear")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                )
                Button(
                    onClick = go,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("grabBtn"),
                    shape = RoundedCornerShape(16.dp),
                    enabled = vm.fetchState !is FetchState.Loading && vm.url.isNotBlank(),
                ) {
                    Icon(Icons.Rounded.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grab", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
        when (val f = vm.fetchState) {
            FetchState.Idle -> item { HowToCard() }
            FetchState.Loading -> item { LoadingCard() }
            is FetchState.Failed -> item { FetchErrorCard(f.message, f.raw) { vm.fetch() } }
            is FetchState.Loaded -> item { InfoCard(f.info) { q -> startDownload(f.info, q) } }
        }
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(brandGradient())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Grabbit", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Grab videos from anywhere", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EngineBanner(state: Engine.State, onRetry: () -> Unit) {
    Card(border = cardBorder(), 
        colors = CardDefaults.cardColors(
            containerColor = if (state is Engine.State.Failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state is Engine.State.Failed) {
                Icon(Icons.Rounded.ErrorOutline, null)
                Spacer(Modifier.width(12.dp))
                Text("Engine couldn't start: ${state.message}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetry) { Text("Retry") }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setting up the download engine…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                    Text("First launch only. Takes a few seconds.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HowToCard() {
    Card(border = cardBorder(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How to use", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Step("1", "In YouTube, Instagram, Facebook or any app, tap Share → Grabbit.")
            Step("2", "Or copy the link and paste it in the box above (long-press → Paste).")
            Step("3", "Pick a quality or MP3, and tap Download.")
            Text(
                "Works with YouTube, Instagram, Facebook, X, Reddit, TikTok, Pinterest, Vimeo, Dailymotion, ShareChat, Moj, Threads, SoundCloud and 1000+ more sites.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(brandGradient()),
            contentAlignment = Alignment.Center,
        ) { Text(n, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingCard() {
    Card(border = cardBorder(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Fetching video info…", fontWeight = FontWeight.SemiBold)
                Text("This can take a few seconds", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FetchErrorCard(message: String, raw: String, onRetry: () -> Unit) {
    var showRaw by remember { mutableStateOf(false) }
    Card(border = cardBorder(), 
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.testTag("fetchError"),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null)
                Spacer(Modifier.width(10.dp))
                Text("Couldn't get this video", fontWeight = FontWeight.Bold)
            }
            Text(message)
            if (showRaw && raw.isNotBlank()) {
                Text(raw.takeLast(1500), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Retry")
                }
                Spacer(Modifier.width(8.dp))
                if (raw.isNotBlank()) TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide details" else "Show details")
                }
            }
        }
    }
}

private data class QOpt(val key: String, val label: String, val size: String)

private fun qualityOptions(info: MediaInfo): List<QOpt> = buildList {
    if (info.hasVideo) {
        add(QOpt("best", "Best", ""))
        info.heights.forEach { h -> add(QOpt("h$h", qualityLabel(h), fmtSize(info.sizes[h] ?: 0))) }
    }
    add(QOpt("audio", "MP3", ""))
}

private fun defaultChoice(opts: List<QOpt>): String {
    val pref = Prefs.defaultQuality
    if (opts.any { it.key == pref }) return pref
    val want = pref.removePrefix("h").toIntOrNull()
    if (want != null) {
        opts.filter { it.key.startsWith("h") }
            .mapNotNull { it.key.drop(1).toIntOrNull() }
            .filter { it <= want }.maxOrNull()?.let { return "h$it" }
    }
    return opts.first().key
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(info: MediaInfo, onDownload: (String) -> Unit) {
    val opts = remember(info) { qualityOptions(info) }
    var selected by remember(info) { mutableStateOf(defaultChoice(opts)) }
    val audioIcon: @Composable () -> Unit = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)) }

    Card(border = cardBorder(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box {
            AsyncImage(
                model = info.thumbnail.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Pill(info.site, Modifier.align(Alignment.TopStart).padding(10.dp))
            val d = fmtDuration(info.duration)
            if (d.isNotEmpty()) Pill(d, Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (info.uploader.isNotBlank()) Text(info.uploader, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (info.isPlaylist) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Playlist · ${info.count} videos", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Text("Quality", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opts.forEach { o ->
                    FilterChip(
                        selected = selected == o.key,
                        onClick = { selected = o.key },
                        label = { Text(if (o.size.isNotEmpty()) "${o.label} · ${o.size}" else o.label) },
                        leadingIcon = if (o.key == "audio") audioIcon else null,
                    )
                }
            }
            Button(
                onClick = { onDownload(selected) },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("downloadBtn"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (info.isPlaylist) "Download all (${info.count})" else "Download",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

/** Permissions still needed before a download: notifications (asked once, optional) and storage on Android 9 and older. */
private fun neededPermissions(ctx: android.content.Context): List<String> = buildList {
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    if (Build.VERSION.SDK_INT >= 33 && !Prefs.notifAsked && !granted(Manifest.permission.POST_NOTIFICATIONS))
        add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT <= 28 && !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

@Composable
private fun PermissionExplainDialog(need: List<String>, onContinue: () -> Unit, onSkip: () -> Unit) {
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
private fun StorageBlockedDialog(ctx: android.content.Context, onClose: () -> Unit) {
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
