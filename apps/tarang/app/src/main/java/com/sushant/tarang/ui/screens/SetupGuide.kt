@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sushant.tarang.data.Folder
import com.sushant.tarang.data.ThemeMode
import com.sushant.tarang.data.plural
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.CookieShape
import com.sushant.tarang.ui.components.rememberHaptic
import com.sushant.tarang.ui.hasAudioPermission
import com.sushant.tarang.ui.theme.PageTitle
import com.sushant.tarang.ui.theme.RoundTitle
import kotlin.math.PI
import kotlin.math.sin

private enum class Step { WELCOME, MUSIC, NOTIFICATIONS, FOLDERS, THEME, DONE }

/**
 * First launch: a short opening animation, then one page per step — only what Tarang really needs.
 * Pages can't be skipped by swiping; Next goes on, and steps that need a permission wait for it.
 */
@Composable
fun SetupGuide(onDone: () -> Unit) {
    val vm = LocalVm.current
    val ctx = LocalContext.current
    val haptic = rememberHaptic()
    val steps = remember { Step.entries.filter { it != Step.NOTIFICATIONS || Build.VERSION.SDK_INT >= 33 } }
    var index by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val step = steps[index]
    var music by remember { mutableStateOf(hasAudioPermission(ctx)) }
    var notif by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var musicAsked by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { music = hasAudioPermission(ctx) }
    LaunchedEffect(music) { if (music) vm.onPermissionGranted() }
    val musicPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val musicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> music = ok; musicAsked = true; vm.prefs.ask.edit().putBoolean("audio_asked", true).apply() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> notif = ok }
    val canGoOn = when (step) { Step.MUSIC -> music; else -> true }
    BackHandlerCompat(index > 0) { forward = false; index-- }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            SetupBottomBar(index, steps.size, canGoOn, isLast = index == steps.lastIndex) {
                if (!canGoOn) { vm.snack.tryEmit("Allow music access first"); return@SetupBottomBar }
                haptic(HapticFeedbackType.Confirm)
                if (index == steps.lastIndex) onDone() else { forward = true; index++ }
            }
        },
        modifier = Modifier.testTag("setup"),
    ) { pad ->
        AnimatedContent(index, Modifier.fillMaxSize().padding(pad), transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 3 * dir } + fadeIn(tween(300, 80))) togetherWith
                (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 3 * dir } + fadeOut(tween(200)))
        }, label = "setupPage") { i ->
            when (steps[i]) {
                Step.WELCOME -> WelcomePage()
                Step.MUSIC -> StepPage("Music access", "Tarang plays the songs on your phone, so it needs to see your music and audio files. It doesn't look at photos or other files, and nothing leaves your phone.",
                    listOf(Icons.Rounded.MusicNote, Icons.Rounded.LibraryMusic, Icons.Rounded.Headphones, Icons.Rounded.Album, Icons.Rounded.GraphicEq)) {
                    val denied = musicAsked && !music && !(ctx as Activity).shouldShowRequestPermissionRationale(musicPerm)
                    if (denied) Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) },
                        Modifier.height(56.dp).testTag("open_settings")) { Text("Open Settings") }
                    else GrantButton(music, "Allow access to music", "allow_music") { musicLauncher.launch(musicPerm) }
                }
                Step.NOTIFICATIONS -> StepPage("Playback controls", "Show play, pause and next in the notification and on the lock screen. You can skip this.",
                    listOf(Icons.Rounded.Notifications, Icons.Rounded.PlayArrow, Icons.Rounded.SkipNext, Icons.Rounded.Lock, Icons.Rounded.Pause)) {
                    GrantButton(notif, "Allow notifications", "allow_notifications") { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                Step.FOLDERS -> FoldersPage()
                Step.THEME -> ThemePage()
                Step.DONE -> DonePage()
            }
        }
    }
}

@Composable
private fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

/** Opening animation: the app icon pops in, a wave runs under it, then the words rise in. */
@Composable
private fun WelcomePage() {
    val cs = MaterialTheme.colorScheme
    val pop = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.55f, 220f)); words.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    val phase by rememberInfiniteTransition(label = "w").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).testTag("welcome"), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1f - pop.value) * -40f }) { AppIcon(150.dp) }
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.fillMaxWidth(0.7f).height(28.dp).graphicsLayer { alpha = pop.value }) {
            val p = Path(); val mid = size.height / 2
            var x = 0f
            p.moveTo(0f, mid)
            while (x < size.width) { x += 3f; p.lineTo(x, mid + 7.dp.toPx() * sin(x / 26.dp.toPx() * 2 * PI.toFloat() + phase)) }
            drawPath(p, cs.primary, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(26.dp))
        Column(Modifier.graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 40.dp.toPx() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome to", style = PageTitle.copy(fontSize = 30.sp), color = cs.onBackground)
            Text("Tarang", style = PageTitle.copy(fontSize = 48.sp, lineHeight = 54.sp), color = cs.primary)
            Spacer(Modifier.height(14.dp))
            Text("Your offline music player. Let's get everything set up for you.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** One step: big rounded title, one line of why, a playful group of tilted shapes, and one main button. */
@Composable
private fun StepPage(title: String, body: String, icons: List<ImageVector>, action: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(title, style = PageTitle.copy(fontSize = 32.sp), color = cs.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ShapeCollage(icons) }
        action()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ShapeCollage(icons: List<ImageVector>) {
    val cs = MaterialTheme.colorScheme
    val float by rememberInfiniteTransition(label = "f").animateFloat(-1f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fl")
    data class Bit(val size: Int, val x: Int, val y: Int, val rot: Float, val shape: Shape, val bg: androidx.compose.ui.graphics.Color, val fg: androidx.compose.ui.graphics.Color)
    val bits = listOf(
        Bit(150, 0, 0, -12f, RoundedCornerShape(40.dp), cs.primaryContainer, cs.onPrimaryContainer),
        Bit(72, -100, -80, 14f, CircleShape, cs.secondaryContainer, cs.onSecondaryContainer),
        Bit(80, 104, 76, 6f, CircleShape, cs.primary, cs.onPrimary),
        Bit(84, 100, -84, -18f, RoundedCornerShape(26.dp), cs.surfaceContainerHighest, cs.onSurfaceVariant),
        Bit(70, -96, 84, 10f, CookieShape(8, 0.1f), cs.tertiaryContainer, cs.onTertiaryContainer),
    )
    Box(Modifier.size(300.dp, 260.dp), contentAlignment = Alignment.Center) {
        bits.forEachIndexed { i, b ->
            Box(Modifier.offset(b.x.dp, (b.y + float * (if (i % 2 == 0) 4 else -4)).dp).size(b.size.dp).graphicsLayer { rotationZ = b.rot }
                .shadow(if (i == 0) 6.dp else 0.dp, b.shape).clip(b.shape).background(b.bg), contentAlignment = Alignment.Center) {
                Icon(icons.getOrElse(i) { Icons.Rounded.MusicNote }, null, tint = b.fg, modifier = Modifier.size((b.size * 0.42f).dp))
            }
        }
    }
}

@Composable
private fun GrantButton(granted: Boolean, label: String, tag: String, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !granted, modifier = Modifier.height(56.dp).testTag(tag), contentPadding = PaddingValues(horizontal = 32.dp)) {
        AnimatedContent(granted, label = "grant") { g ->
            if (g) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Allowed") }
            else Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FoldersPage() {
    val vm = LocalVm.current
    val cs = MaterialTheme.colorScheme
    val lib by vm.library.collectAsState()
    val s by vm.settings.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val all = remember(lib.all) { lib.all.groupBy { it.folderPath }.map { (p, l) -> Folder(p, l.first().folderName, l) }.sortedBy { it.name.lowercase() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Folders to skip", style = PageTitle.copy(fontSize = 32.sp), color = cs.onBackground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Voice notes, call recordings and ringtones are already off. Switch off any other folder you don't want in Tarang. You can change this later in Settings.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (all.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(if (scanning || !lib.loaded) "Looking for your music…" else "No folders with music yet", color = cs.onSurfaceVariant)
        } else LazyColumn(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).testTag("setup_folders")) {
            items(all, key = { it.path }) { f ->
                val on = vm.repo.isFolderVisible(f.path, s)
                Row(Modifier.fillMaxWidth().clickable { toggleFolder(vm, f.path, on, all) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodyLarge)
                        Text(plural(f.songs.size, "file"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(on, { toggleFolder(vm, f.path, on, all) })
                }
            }
        }
    }
}

@Composable
private fun ThemePage() {
    val vm = LocalVm.current
    val cs = MaterialTheme.colorScheme
    val s by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text("Pick your look", style = PageTitle.copy(fontSize = 32.sp), color = cs.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("The player always takes its colours from the song's cover.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        val opts = listOf(Triple(ThemeMode.SYSTEM, Icons.Rounded.BrightnessAuto, "Follow phone"), Triple(ThemeMode.LIGHT, Icons.Rounded.LightMode, "Light"), Triple(ThemeMode.DARK, Icons.Rounded.DarkMode, "Dark"))
        opts.forEachIndexed { i, (m, icon, label) ->
            val on = s.themeMode == m
            val first = i == 0; val last = i == opts.lastIndex
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)
                .clip(RoundedCornerShape(if (first) 24.dp else 6.dp, if (first) 24.dp else 6.dp, if (last) 24.dp else 6.dp, if (last) 24.dp else 6.dp))
                .background(if (on) cs.primaryContainer else cs.surfaceContainer).clickable { vm.setSettings { it.copy(themeMode = m) } }
                .padding(horizontal = 18.dp, vertical = 16.dp).testTag("setup_theme_${m.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, color = if (on) cs.onPrimaryContainer else cs.onSurface, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Rounded.CheckCircle, null, tint = cs.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).clickable { vm.setSettings { it.copy(pureBlack = !it.pureBlack) } }
            .padding(horizontal = 18.dp, vertical = 12.dp).testTag("setup_pure_black"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Contrast, null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Pure black", style = MaterialTheme.typography.bodyLarge)
                Text("Darker dark mode for OLED screens", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(s.pureBlack, { v -> vm.setSettings { it.copy(pureBlack = v) } })
        }
    }
}

@Composable
private fun DonePage() {
    StepPage("All set!", "You're ready to enjoy your music. Tap a song to play it, swipe the cover to change songs, and pull the player down to close it.",
        listOf(Icons.Rounded.CheckCircle, Icons.Rounded.Favorite, Icons.Rounded.Celebration, Icons.Rounded.MusicNote, Icons.Rounded.AutoAwesome)) {}
}

/**
 * Raised bar at the bottom: "Let's go!" / "Step 2 of 5" (slides up on each step) and a big next button that
 * spins a full turn and changes shape (circle → rounded square → leaf) on every step; a tick on the last.
 */
@Composable
private fun SetupBottomBar(index: Int, count: Int, enabled: Boolean, isLast: Boolean, onNext: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shapes = listOf(listOf(50, 50, 50, 50), listOf(28, 28, 28, 28), listOf(18, 50, 18, 50))
    val target = shapes[index % 3]
    val spec = tween<Float>(600, easing = FastOutSlowInEasing)
    val a by animateFloatAsState(target[0].toFloat(), spec, label = "a")
    val b by animateFloatAsState(target[1].toFloat(), spec, label = "b")
    val c by animateFloatAsState(target[2].toFloat(), spec, label = "c")
    val d by animateFloatAsState(target[3].toFloat(), spec, label = "d")
    val rot by animateFloatAsState(index * 360f, tween(900, easing = FastOutSlowInEasing), label = "rot")
    Surface(color = cs.surfaceContainer, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 28.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(index, Modifier.weight(1f), transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            }, label = "stepText") { i ->
                if (i == 0) Text("Let's go!", style = RoundTitle, fontSize = 22.sp, color = cs.primary)
                else Text("Step $i of ${count - 1}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.testTag("setup_step"))
            }
            Box(Modifier.size(80.dp).graphicsLayer { rotationZ = rot }
                .clip(RoundedCornerShape(a.toInt(), b.toInt(), c.toInt(), d.toInt()))
                .background(if (enabled) cs.primaryContainer else cs.surfaceContainerHighest)
                .clickable(onClickLabel = if (isLast) "Finish" else "Next", onClick = onNext).testTag("setup_next"), contentAlignment = Alignment.Center) {
                AnimatedContent(isLast, Modifier.graphicsLayer { rotationZ = -rot }, label = "nextIcon") { last ->
                    Icon(if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, if (last) "Finish" else "Next",
                        tint = if (enabled) cs.onPrimaryContainer else cs.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

