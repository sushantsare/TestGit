package com.sushant.pageone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sushant.pageone.ui.MainVm
import com.sushant.pageone.ui.components.*
import com.sushant.pageone.ui.theme.ThemeMode
import kotlinx.coroutines.delay

private data class Shapelet(val kind: ExpressiveKind, val icon: ImageVector, val size: Dp, val x: Dp, val y: Dp, val rot: Float, val primary: Int)

/** First launch: a short opening animation, then a 4-step setup guide (fresh installs only). */
@Composable
fun SetupScreen(vm: MainVm, onDone: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val access by vm.access.collectAsState()
    var intro by rememberSaveable { mutableStateOf(true) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshAccess() } }
    LaunchedEffect(Unit) { delay(1100); intro = false }
    BackHandler(step > 0) { step-- }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("setup")) {
        AnimatedVisibility(intro, enter = fadeIn(), exit = fadeOut()) { OpeningAnimation() }
        AnimatedVisibility(!intro, enter = fadeIn(tween(400)), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                AnimatedContent(step, transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                }, modifier = Modifier.weight(1f), label = "setup") { st ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 40.dp)) {
                        Box(Modifier.fillMaxWidth().height(280.dp)) { Art(st) }
                        Spacer(Modifier.height(20.dp))
                        Text(titles[st], style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))
                        Text(bodies[st], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (st) {
                            1 -> Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (access) AssistChip({}, { Text("Access allowed") }, leadingIcon = { Icon(Icons.Rounded.Check, null) })
                                else {
                                    Button(onClick = { openAllFilesSettings(context) }, modifier = Modifier.testTag("allow_access")) { Text("Allow access") }
                                    OutlinedButton(onClick = { step = 2 }, modifier = Modifier.testTag("skip")) { Text("Skip") }
                                }
                            }
                            2 -> {
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(Triple(ThemeMode.SYSTEM, Icons.Rounded.BrightnessAuto, "System"), Triple(ThemeMode.LIGHT, Icons.Rounded.LightMode, "Light"),
                                        Triple(ThemeMode.DARK, Icons.Rounded.DarkMode, "Dark")).forEach { (m, ic, l) ->
                                        val on = s.themeMode == m
                                        Surface(onClick = { vm.update { it.copy(themeMode = m) } }, shape = RoundedCornerShape(20.dp),
                                            color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.weight(1f).testTag("theme_${m.name.lowercase()}")) {
                                            Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(ic, null); Spacer(Modifier.height(4.dp)); Text(l, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Group { SettingSwitch(Icons.Rounded.DarkMode, "Pure black", "Darker dark mode for OLED screens", s.pureBlack, { v -> vm.update { it.copy(pureBlack = v) } }, 0, 1, "setup_pure_black") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 28.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(step, label = "stepText", transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }) {
                        Text("Step ${it + 1} of 4", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    MorphButton(step) { if (step < 3) step++ else onDone() }
                }
            }
        }
    }
}

private val titles = listOf("Welcome to PageOne", "Find all your PDFs", "Pick your look", "You’re all set")
private val bodies = listOf(
    "Open, read and mark up every PDF on your phone. Fast, offline and private.",
    "Allow “All files access” so PageOne can list PDFs from WhatsApp, Telegram and Downloads. Nothing leaves your phone.",
    "Use your wallpaper colours, light or dark, and pure black for OLED screens.",
    "Tap any PDF to start reading. Use + on Home to merge PDFs and photos, split pages or compress.",
)

@Composable
private fun Art(step: Int) {
    val c = MaterialTheme.colorScheme
    val items = when (step) {
        0 -> listOf(Shapelet(ExpressiveKind.COOKIE, Icons.Rounded.PictureAsPdf, 150.dp, 10.dp, 40.dp, -8f, 0), Shapelet(ExpressiveKind.CLOVER, Icons.Rounded.Bookmark, 100.dp, 180.dp, 10.dp, 12f, 2), Shapelet(ExpressiveKind.SUNNY, Icons.Rounded.Search, 90.dp, 200.dp, 160.dp, -10f, 1))
        1 -> listOf(Shapelet(ExpressiveKind.FLOWER, Icons.Rounded.FolderOpen, 160.dp, 90.dp, 50.dp, 6f, 0), Shapelet(ExpressiveKind.BURST, Icons.Rounded.LockOpen, 80.dp, 10.dp, 180.dp, -12f, 2))
        2 -> listOf(Shapelet(ExpressiveKind.CLOVER, Icons.Rounded.LightMode, 110.dp, 10.dp, 60.dp, -6f, 1), Shapelet(ExpressiveKind.COOKIE, Icons.Rounded.DarkMode, 110.dp, 170.dp, 90.dp, 8f, 0))
        else -> listOf(Shapelet(ExpressiveKind.BURST, Icons.Rounded.Check, 170.dp, 80.dp, 50.dp, 0f, 0))
    }
    val spin = rememberInfiniteTransition(label = "art")
    val wobble by spin.animateFloat(-4f, 4f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w")
    items.forEach { sh ->
        val (bg, fg) = when (sh.primary) { 0 -> c.primaryContainer to c.onPrimaryContainer; 1 -> c.secondaryContainer to c.onSecondaryContainer; else -> c.tertiaryContainer to c.onTertiaryContainer }
        Box(Modifier.offset(sh.x, sh.y).rotate(sh.rot + wobble)) { ShapedIcon(sh.icon, sh.kind, sh.size, bg, fg, iconSize = sh.size * 0.32f) }
    }
}

/** Next button: circle → rounded square → cookie → tick, spinning a little on every step. */
@Composable
private fun MorphButton(step: Int, onClick: () -> Unit) {
    val rot by animateFloatAsState(step * 90f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "rot")
    val shape: Shape = when (step) {
        0 -> CircleShape
        1 -> RoundedCornerShape(24.dp)
        2 -> ExpressiveShape(ExpressiveKind.COOKIE)
        else -> RoundedCornerShape(24.dp)
    }
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(72.dp).rotate(if (step == 3) 0f else rot).testTag("setup_next")) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (step == 3) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, if (step == 3) "Done" else "Next",
                Modifier.size(30.dp).rotate(if (step == 3) 0f else -rot))
        }
    }
}

@Composable
private fun OpeningAnimation() {
    val scale = remember { Animatable(0.4f) }
    val turn = remember { Animatable(-90f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            kotlinx.coroutines.launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            kotlinx.coroutines.launch { turn.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(140.dp).scale(scale.value).rotate(turn.value).clip(ExpressiveShape(ExpressiveKind.COOKIE)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(56.dp).rotate(-turn.value))
        }
    }
}

// ======================= APP LOCK =======================

const val UNLOCK_AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun canUnlock(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(UNLOCK_AUTH) == BiometricManager.BIOMETRIC_SUCCESS

/** Android's own unlock sheet: face, fingerprint or phone PIN/pattern, whichever you use first. */
fun authenticate(activity: FragmentActivity, title: String, onOk: () -> Unit, onFail: (String?) -> Unit = {}) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("Use face, fingerprint or screen lock")
        .setAllowedAuthenticators(UNLOCK_AUTH)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val ask = { authenticate(activity, "Unlock PageOne", onUnlocked) }
    LaunchedEffect(Unit) { delay(250); ask() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).clickable(remember { MutableInteractionSource() }, null) {}.testTag("lock_screen"),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShapedIcon(Icons.Rounded.Lock, ExpressiveKind.COOKIE, 88.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 36.dp)
            Spacer(Modifier.height(18.dp))
            Text("PageOne is locked", style = MaterialTheme.typography.headlineSmall)
            Text("Unlock with face, fingerprint or your phone PIN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(28.dp))
            FilledTonalButton(onClick = ask, modifier = Modifier.testTag("unlock")) { Icon(Icons.Rounded.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Unlock") }
        }
    }
}
