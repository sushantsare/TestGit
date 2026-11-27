@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sushant.grabbit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sushant.grabbit.data.Folders
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.data.Sites
import com.sushant.grabbit.ui.components.GrabbitMark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fresh install only: an update keeps going straight to the app. */
fun shouldShowOnboarding(ctx: Context): Boolean {
    if (Prefs.onboarded) return false
    val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
    if (!fresh) Prefs.onboarded = true
    return fresh
}

private const val PAGES = 7

/** First-launch animation, then the setup guide (Skip on every page). [onDone] gets true when the user wants to log in to Instagram. */
@Composable
fun Onboarding(onDone: (loginInstagram: Boolean) -> Unit) {
    var intro by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1500); intro = false }
    AnimatedContent(intro, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "intro") { showIntro ->
        if (showIntro) IntroAnimation() else Guide(onDone)
    }
}

@Composable
private fun IntroAnimation() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(500); shown = true }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("introAnim"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GrabbitMark(104.dp, animate = true)
            Spacer(Modifier.height(20.dp))
            androidx.compose.animation.AnimatedVisibility(shown, enter = fadeIn()) {
                Text("Grabbit", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp))
            }
        }
    }
}

@Composable
private fun Guide(onDone: (Boolean) -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val finish = { login: Boolean -> Prefs.onboarded = true; onDone(login) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().testTag("onboarding")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GrabbitMark(28.dp, modifier = Modifier.padding(start = 12.dp))
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < PAGES - 1) TextButton(onClick = { finish(false) }, modifier = Modifier.testTag("obSkip")) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).testTag("obPage$page"),
                horizontalAlignment = Alignment.Start,
            ) {
                when (page) {
                    0 -> Welcome()
                    1 -> TwoWays()
                    2 -> NotificationsPage()
                    3 -> BatteryPage()
                    4 -> FolderPage()
                    5 -> LoginPage { finish(true) }
                    else -> ReadyPage { finish(false) }
                }
            }
        }
        // Dots + Back / Next
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                modifier = Modifier.testTag("obBack")) { Text("Back") }
            else Spacer(Modifier.width(64.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(PAGES) { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 22.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot")
                    Box(Modifier.padding(3.dp).height(8.dp).width(w).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            if (pager.currentPage < PAGES - 1) Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.testTag("obNext")) { Text("Next") }
            else Spacer(Modifier.width(64.dp))
        }
    }
}

/** Big icon in an Expressive shape. */
@Composable
private fun Badge(icon: ImageVector, shape: androidx.compose.ui.graphics.Shape) {
    Box(Modifier.padding(top = 24.dp, bottom = 24.dp).size(112.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Done(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Welcome() {
    Badge(Icons.Rounded.WavingHand, MaterialShapes.Cookie9Sided.toShape())
    Heading("Grab videos, audio and photos", "Save from your favourite sites to your phone — in the quality you pick, in the folder you pick.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("YouTube", "Instagram", "X", "Facebook", "Threads", "Reddit", "Dailymotion", "+1,000 more").forEach {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(it, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TwoWays() {
    Badge(Icons.Rounded.Link, MaterialShapes.Clover4Leaf.toShape())
    Heading("Two ways to grab", "Use whichever is quicker.")
    WayCard(Icons.Rounded.Link, "Paste a link", "Copy a link in any app, paste it on the Grab screen, tap Grab.")
    Spacer(Modifier.height(8.dp))
    WayCard(Icons.Rounded.Share, "Share to Grabbit", "In YouTube, Instagram or any app: tap Share, then Grabbit. A small sheet opens right there.")
}

@Composable
private fun WayCard(icon: ImageVector, title: String, body: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotificationsPage() {
    val ctx = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var on by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { on = it; Prefs.notifAsked = true }
    Badge(Icons.Rounded.Notifications, MaterialShapes.Sunny.toShape())
    Heading("See progress, know when it's done", "Grabbit shows download progress in your notifications and tells you when a file is ready. Optional — downloads work without it.")
    if (on) Done("Notifications are on")
    else Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.testTag("obNotif")) { Text("Allow notifications") }
}

@Composable
private fun BatteryPage() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(BatteryAccess.allowed(ctx)) }
    LifecycleResumeEffect(Unit) { on = BatteryAccess.allowed(ctx); onPauseOrDispose { } }
    Badge(Icons.Rounded.BatteryChargingFull, MaterialShapes.Pill.toShape())
    Heading("Download with the screen off", "Android slows apps down in the background to save battery. Allow Grabbit so big downloads keep full speed when your screen is off.")
    if (on) Done("Allowed")
    else Button(onClick = { BatteryAccess.request(ctx) }, modifier = Modifier.testTag("obBattery")) { Text("Allow background downloads") }
}

@Composable
private fun FolderPage() {
    val ctx = LocalContext.current
    val current by Folders.current.collectAsState()
    val recent by Folders.recent.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) Folders.add(ctx, uri) }
    Badge(Icons.Rounded.Folder, MaterialShapes.Gem.toShape())
    Heading("Where should downloads go?", "Default keeps things tidy: videos in Movies/Grabbit, audio in Music/Grabbit, photos in Pictures/Grabbit. You can pick another folder any time when you download.")
    val chosen = recent.firstOrNull { it.uri == current }
    if (chosen != null) Done("Saving to ${chosen.name}") else Done("Using the Default folders")
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chosen != null) OutlinedButton(onClick = { Folders.select("") }) { Text("Use Default") }
        FilledTonalButton(onClick = { picker.launch(null) }, modifier = Modifier.testTag("obFolder")) { Text("Choose a folder") }
    }
}

@Composable
private fun LoginPage(onLogin: () -> Unit) {
    Badge(Icons.Rounded.Lock, MaterialShapes.Square.toShape())
    Heading("Stories and private posts", "Instagram stories and posts from private accounts need your Instagram login. It stays on this phone only and never goes into backups. You can also do this later in Settings → Accounts.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onLogin, modifier = Modifier.testTag("obLogin")) { Text("Log in to Instagram") }
    }
    Spacer(Modifier.height(8.dp))
    Text("Or tap Next to do it later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReadyPage(onStart: () -> Unit) {
    Badge(Icons.Rounded.RocketLaunch, MaterialShapes.Burst.toShape())
    Heading("You're all set", "Copy a link from any app and paste it on the next screen — or share straight to Grabbit.")
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("obStart")) {
        Text("Paste your first link", style = MaterialTheme.typography.titleMedium)
    }
}
