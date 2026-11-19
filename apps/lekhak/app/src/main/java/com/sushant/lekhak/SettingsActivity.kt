package com.sushant.lekhak

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.lekhak.core.LekhakServiceManager as M
import com.sushant.lekhak.core.Palette
import com.sushant.lekhak.core.toast
import com.sushant.lekhak.service.FloatingBubbleService
import com.sushant.lekhak.service.LekhakAccessibilityService
import com.sushant.lekhak.ui.LekhakTheme
import com.sushant.lekhak.ui.LocalUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        M.initialize(this)
        // Returning users: silently bring the bubble back.
        val first = M.prefs(this).getBoolean("is_first_launch", true)
        if (!first && M.serviceEnabled && Settings.canDrawOverlays(this)) FloatingBubbleService.start(this)
        setContent {
            var theme by rememberSaveable { mutableStateOf(M.themeMode) }
            val palette = Palette.resolve(this, theme)
            LaunchedEffect(palette.isDark) {
                val style = if (palette.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LekhakTheme(palette) {
                SettingsScreen(
                    activity = this,
                    theme = theme,
                    onTheme = { theme = it; M.setTheme(it) },
                )
            }
        }
    }

    fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    fun emailDeveloper() {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, "Lekhak feedback")
        try { startActivity(i) } catch (_: Exception) { toast("No email app found") }
    }
}

@Composable
private fun SettingsScreen(activity: SettingsActivity, theme: String, onTheme: (String) -> Unit) {
    val ui = LocalUi.current
    val prefs = remember { M.prefs(activity) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(activity)) }
    var access by remember { mutableStateOf(LekhakAccessibilityService.isEnabled(activity)) }
    var bubbleOn by remember { mutableStateOf(M.isBubbleServiceActive) }
    var firstLaunch by remember { mutableStateOf(prefs.getBoolean("is_first_launch", true)) }
    var useGemini by remember { mutableStateOf(M.useGemini) }
    var aiReady by remember { mutableStateOf(M.isAiReady()) }

    // Key fields (drafts survive rotation / process death)
    var geminiKey by rememberSaveable { mutableStateOf(M.geminiKey) }
    var geminiModel by rememberSaveable { mutableStateOf(M.geminiModel) }
    var openAiKey by rememberSaveable { mutableStateOf(M.customOpenAiKey) }
    var openAiUrl by rememberSaveable { mutableStateOf(M.customOpenAiUrl) }
    var openAiModel by rememberSaveable { mutableStateOf(M.customOpenAiModel) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var accessDialogDismissed by rememberSaveable { mutableStateOf(false) }
    var overlayPrompted by rememberSaveable { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    var permY by remember { mutableIntStateOf(0) }
    var keyY by remember { mutableIntStateOf(0) }

    val dirty = if (useGemini) geminiKey.trim() != M.geminiKey || geminiModel.trim() != M.geminiModel
    else openAiKey.trim() != M.customOpenAiKey || openAiUrl.trim() != M.customOpenAiUrl || openAiModel.trim() != M.customOpenAiModel

    fun leave() { if (dirty) showDiscard = true else activity.finish() }
    BackHandler { leave() }

    // Poll permission + service state every 1.5 s.
    LaunchedEffect(Unit) {
        while (true) {
            overlay = Settings.canDrawOverlays(activity)
            access = LekhakAccessibilityService.isEnabled(activity)
            bubbleOn = M.isBubbleServiceActive
            aiReady = M.isAiReady()
            delay(1500)
        }
    }

    // Step 1: overlay permission
    LaunchedEffect(Unit) {
        if (firstLaunch && !Settings.canDrawOverlays(activity) && !overlayPrompted) {
            delay(800)
            overlayPrompted = true
            activity.toast("Please approve Display Over Other Apps first!")
            activity.openOverlaySettings()
        }
    }

    // Step 3: highlight the key field
    val needKey = firstLaunch && overlay && access && !aiReady
    LaunchedEffect(needKey) {
        if (needKey) { delay(300); scroll.animateScrollTo((keyY - 40).coerceAtLeast(0)) }
    }

    // Step 4: ready
    LaunchedEffect(firstLaunch, overlay, access, aiReady) {
        if (firstLaunch && overlay && access && aiReady) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
            firstLaunch = false
            M.setServiceEnabled(true)
            FloatingBubbleService.start(activity)
            activity.toast("Lekhak is ready")
            activity.moveTaskToBack(true)
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulseAlpha"
    )

    Box(Modifier.fillMaxSize().background(ui.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .testTag("settings_list")
        ) {
            // Back arrow
            Box(
                Modifier.padding(top = 4.dp).size(44.dp).clip(RoundedCornerShape(22.dp))
                    .clickable { leave() }.testTag("back"),
                contentAlignment = Alignment.Center
            ) { Text("←", color = ui.text, fontSize = 24.sp) }

            // Revoked banner
            if (!firstLaunch && (!overlay || !access)) {
                Text(
                    "⚠️ A required permission was removed. Tap to fix.",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(ui.banner).clickable { scope.launch { scroll.animateScrollTo(permY) } }
                        .padding(14.dp).testTag("revoked_banner")
                )
            }

            // Header
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✍️", fontSize = 40.sp)
                Text("Lekhak", color = ui.brand, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your AI writing assistant", color = ui.textSecondary, fontSize = 13.sp)
            }

            // Permissions
            SectionTitle("REQUIRED PERMISSIONS", Modifier.onGloballyPositioned { permY = it.positionInParent().y.toInt() })
            PermissionCard("♿", "Accessibility Service", "Required to read and write in WhatsApp", access, "perm_accessibility") {
                activity.openAccessibilitySettings()
            }
            if (!access && Build.VERSION.SDK_INT >= 33) {
                Text(
                    "Switch greyed out? Open App info → ⋮ (top right) → Allow restricted settings, then try again.",
                    color = ui.textSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { activity.openAppInfo() }.padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("restricted_help")
                )
                Text(
                    "Open App info", color = ui.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { activity.openAppInfo() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            PermissionCard("🥞", "Display Over Other Apps", "Required to show the floating bubble", overlay, "perm_overlay") {
                activity.openOverlaySettings()
            }

            // Provider
            SectionTitle("AI MODEL PROVIDER")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProviderCard(
                    "♊", "Lekhak Premium",
                    if (M.hasBuiltInGeminiKey()) "Built-in (Zero Setup)" else "Gemini · free key",
                    useGemini, Modifier.weight(1f).testTag("provider_gemini")
                ) { useGemini = true; M.setProvider(true); aiReady = M.isAiReady() }
                ProviderCard("🧠", "Custom OpenAI", "Requires custom key", !useGemini, Modifier.weight(1f).testTag("provider_openai")) {
                    useGemini = false; M.setProvider(false); aiReady = M.isAiReady()
                }
            }

            // Keys
            val highlight = needKey
            Column(
                Modifier.onGloballyPositioned { keyY = it.positionInParent().y.toInt() }
            ) {
                SectionTitle(if (useGemini) "GEMINI API KEY" else "OPENAI API KEY")
                if (highlight) {
                    Text(
                        if (useGemini) "Add your Gemini key to get started" else "Add your OpenAI key to get started",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
                            .background(ui.brand).padding(horizontal = 12.dp, vertical = 8.dp).testTag("key_tooltip")
                    )
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.card)
                        .border(
                            if (highlight) BorderStroke(2.dp, ui.brand.copy(alpha = pulse.value))
                            else BorderStroke(1.dp, ui.cardBorder),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    val keyValue = if (useGemini) geminiKey else openAiKey
                    if (useGemini) {
                        Text(
                            if (M.hasBuiltInGeminiKey()) "The built-in key is active. Paste your own key only if you want to use it instead."
                            else "Get a free key at aistudio.google.com → Get API key, then paste it here.",
                            color = ui.textSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = keyValue,
                        onValueChange = { if (useGemini) geminiKey = it else openAiKey = it },
                        placeholder = { Text("Paste your API key here", color = ui.textSecondary) },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Text(
                                if (showKey) "👁️" else "🔒", fontSize = 18.sp,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { showKey = !showKey }
                                    .padding(10.dp).testTag("toggle_key")
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("key_field")
                    )
                    Text(
                        "Your key is stored only on this device", color = ui.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    )
                    if (useGemini) {
                        LabeledField("Model", geminiModel, { geminiModel = it }, "model_field")
                    } else {
                        LabeledField("API URL", openAiUrl, { openAiUrl = it }, "url_field")
                        Spacer(Modifier.height(8.dp))
                        LabeledField("Model", openAiModel, { openAiModel = it }, "model_field")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            focus.clearFocus()
                            if (useGemini) {
                                M.saveGemini(geminiKey, geminiModel)
                                geminiKey = M.geminiKey; geminiModel = M.geminiModel
                            } else {
                                if (openAiKey.isBlank()) { activity.toast("Please paste your API key first"); return@Button }
                                M.saveOpenAi(openAiKey, openAiUrl, openAiModel)
                                openAiKey = M.customOpenAiKey; openAiUrl = M.customOpenAiUrl; openAiModel = M.customOpenAiModel
                            }
                            aiReady = M.isAiReady()
                            activity.toast("API key saved")
                        },
                        enabled = dirty,
                        colors = ButtonDefaults.buttonColors(containerColor = ui.brand, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_key")
                    ) { Text("Save API Key", fontWeight = FontWeight.Bold) }
                }
            }

            // Appearance
            SectionTitle("APPEARANCE")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.card)
                    .border(1.dp, ui.cardBorder, RoundedCornerShape(16.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(M.THEME_SYSTEM to "System", M.THEME_LIGHT to "Light", M.THEME_DARK to "Dark").forEach { (key, label) ->
                    val on = theme == key
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) ui.chip else Color.Transparent)
                            .clickable { onTheme(key) }.testTag("theme_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (on) Color.White else ui.textSecondary,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                    }
                }
            }
            Text("Also used for the bubble sheet inside WhatsApp.", color = ui.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp))

            // Service
            SectionTitle("SERVICE")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.card)
                    .border(1.dp, ui.cardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Lekhak Service", color = ui.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Keep this running for the bubble to appear", color = ui.textSecondary, fontSize = 13.sp)
                }
                Switch(
                    checked = bubbleOn,
                    onCheckedChange = { on ->
                        if (on) {
                            if (!Settings.canDrawOverlays(activity)) {
                                activity.toast("Please approve Display Over Other Apps first!")
                                activity.openOverlaySettings()
                            } else {
                                M.setServiceEnabled(true)
                                FloatingBubbleService.start(activity)
                                bubbleOn = true
                            }
                        } else {
                            M.setServiceEnabled(false)
                            FloatingBubbleService.stop(activity)
                            bubbleOn = false
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = ui.brand, checkedThumbColor = Color.White),
                    modifier = Modifier.testTag("service_switch")
                )
            }

            // About
            SectionTitle("ABOUT")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.card)
                    .border(1.dp, ui.cardBorder, RoundedCornerShape(16.dp)).padding(16.dp)
            ) {
                AboutRow("Version", "Version ${BuildConfig.VERSION_NAME}")
                Spacer(Modifier.height(12.dp))
                AboutRow("Developed by", "Sushant Kumar")
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { activity.emailDeveloper() }
                        .padding(vertical = 2.dp).testTag("about_email"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Email", color = ui.textSecondary, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                    Text("sushant.sare@gmail.com", color = ui.brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // Step 2: accessibility dialog
    if (firstLaunch && overlay && !access && !accessDialogDismissed) {
        AlertDialog(
            onDismissRequest = { accessDialogDismissed = true },
            containerColor = ui.card,
            title = { Text("Enable Accessibility", color = ui.text, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Find Lekhak in the list and turn it on. This allows Lekhak to read and write in WhatsApp.",
                        color = ui.textSecondary)
                    if (Build.VERSION.SDK_INT >= 33) {
                        Spacer(Modifier.height(10.dp))
                        Text("If the switch is greyed out: App info → ⋮ → Allow restricted settings.",
                            color = ui.textSecondary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { accessDialogDismissed = true; activity.openAccessibilitySettings() },
                    modifier = Modifier.testTag("dialog_open_settings")) {
                    Text("Open Settings", color = ui.brand, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { accessDialogDismissed = true; activity.openAppInfo() }) {
                    Text("App info", color = ui.textSecondary)
                }
            }
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor = ui.card,
            title = { Text("Unsaved changes", color = ui.text, fontWeight = FontWeight.Bold) },
            text = { Text("Your API key changes are not saved yet.", color = ui.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    if (useGemini) M.saveGemini(geminiKey, geminiModel)
                    else if (openAiKey.isNotBlank()) M.saveOpenAi(openAiKey, openAiUrl, openAiModel)
                    showDiscard = false
                    activity.finish()
                }) { Text("Save", color = ui.brand, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false; activity.finish() }) { Text("Discard", color = ui.error) }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Text(
        text, color = ui.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun PermissionCard(emoji: String, title: String, sub: String, granted: Boolean, tag: String, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ui.card)
            .border(1.dp, ui.cardBorder, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(16.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ui.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = ui.textSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (granted) "Granted" else "Not Granted",
            color = if (granted) ui.granted else ui.notGranted,
            fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
    }
}

@Composable
private fun ProviderCard(emoji: String, title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) ui.selectedCard else ui.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) ui.brand else ui.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = ui.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = ui.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit, tag: String) {
    val ui = LocalUi.current
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label, color = ui.textSecondary) },
        shape = RoundedCornerShape(12.dp), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun fieldColors() = LocalUi.current.let { ui ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ui.input, unfocusedContainerColor = ui.input,
        focusedBorderColor = ui.brand, unfocusedBorderColor = ui.border,
        focusedTextColor = ui.text, unfocusedTextColor = ui.text, cursorColor = ui.brand,
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ui.textSecondary, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Text(value, color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
