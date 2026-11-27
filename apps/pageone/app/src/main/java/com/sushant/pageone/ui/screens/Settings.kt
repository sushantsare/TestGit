package com.sushant.pageone.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.sushant.pageone.BuildConfig
import com.sushant.pageone.R
import com.sushant.pageone.data.Format
import com.sushant.pageone.ui.MainVm
import com.sushant.pageone.ui.Routes
import com.sushant.pageone.ui.components.*
import com.sushant.pageone.ui.theme.LocalIsDark
import com.sushant.pageone.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainVm, nav: NavHostController) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val access by vm.access.collectAsState()
    val pdfs by vm.lib.pdfs.collectAsState()
    val scan by vm.lib.scan.collectAsState()
    val dark = LocalIsDark.current
    var themeDialog by remember { mutableStateOf(false) }
    var scrollDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dupGroups = remember(pdfs) { pdfs.filter { it.fingerprint.isNotEmpty() }.groupBy { it.fingerprint }.count { it.value.size > 1 } }

    Column(Modifier.fillMaxSize()) {
        SmallTopBar("Settings", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("settings_list").padding(bottom = 32.dp)) {
            Label("Appearance")
            Group {
                val n = if (dark) 3 else 2
                SettingNav(Icons.Rounded.Contrast, "Theme", when (s.themeMode) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" },
                    { themeDialog = true }, 0, n, "set_theme")
                SettingSwitch(Icons.Rounded.Wallpaper, "Wallpaper colours", "Match your phone’s colours", s.wallpaperColors, { v -> vm.update { it.copy(wallpaperColors = v) } }, 1, n, "set_wallpaper")
                if (dark) SettingSwitch(Icons.Rounded.DarkMode, "Pure black", "Darker dark mode for OLED screens", s.pureBlack, { v -> vm.update { it.copy(pureBlack = v) } }, 2, n, "set_pure_black")
            }
            Label("PDF rows show")
            Group {
                SettingSwitch(Icons.Rounded.Image, "First-page thumbnail", null, s.rowThumb, { v -> vm.update { it.copy(rowThumb = v) } }, 0, 4, "set_row_thumb")
                SettingSwitch(Icons.Rounded.Description, "Page count", null, s.rowPages, { v -> vm.update { it.copy(rowPages = v) } }, 1, 4, "set_row_pages")
                SettingSwitch(Icons.Rounded.Event, "Size and date", null, s.rowSizeDate, { v -> vm.update { it.copy(rowSizeDate = v) } }, 2, 4, "set_row_size")
                SettingSwitch(Icons.Rounded.Percent, "Reading progress", null, s.rowProgress, { v -> vm.update { it.copy(rowProgress = v) } }, 3, 4, "set_row_progress")
            }
            Label("Reading")
            Group {
                SettingNav(Icons.Rounded.SwapVert, "Default scroll", if (s.pageByPage) "Page by page" else "Vertical scroll", { scrollDialog = true }, 0, 4, "set_scroll")
                SettingSwitch(Icons.Rounded.LightMode, "Keep screen on", "While a PDF is open", s.keepScreenOn, { v -> vm.update { it.copy(keepScreenOn = v) } }, 1, 4, "set_screen_on")
                SettingSwitch(Icons.AutoMirrored.Rounded.VolumeUp, "Volume keys turn pages", null, s.volumeKeys, { v -> vm.update { it.copy(volumeKeys = v) } }, 2, 4, "set_volume")
                SettingNav(Icons.Rounded.History, "Clear search history", null, {
                    scope.launch { vm.lib.clearSearchHistory(); vm.message("Search history cleared") }
                }, 3, 4, "set_clear_search")
            }
            Label("Library")
            Group {
                SettingNav(Icons.Rounded.ContentCopy, "Find duplicate PDFs", if (dupGroups == 0) "No copies found" else "$dupGroups group${if (dupGroups > 1) "s" else ""} found",
                    { nav.navigate(Routes.DUPLICATES) }, 0, 2, "set_duplicates")
                SettingNav(Icons.Rounded.Refresh, "Rescan phone for PDFs",
                    if (scan.running) "Scanning…" else if (s.lastScan > 0) "Last scan ${Format.opened(s.lastScan).removePrefix("Opened ")}" else null,
                    { vm.lib.scan(force = true); vm.message("Scanning for PDFs…") }, 1, 2, "set_rescan", chevron = false)
            }
            Label("Privacy")
            Group {
                SettingSwitch(Icons.Rounded.Fingerprint, "App lock", "Face, fingerprint or phone PIN", s.appLock, { v ->
                    val act = context as? FragmentActivity
                    if (!v) vm.update { it.copy(appLock = false) }
                    else if (act != null && canUnlock(act)) authenticate(act, "Turn on app lock", onOk = { vm.update { it.copy(appLock = true) }; vm.message("App lock is on") })
                    else vm.message("Set a screen lock on your phone first")
                }, 0, 2, "set_app_lock")
                SettingNav(Icons.Rounded.FolderOpen, "Files access", if (access) "Allowed" else "Not allowed · tap to allow", { openAllFilesSettings(context) }, 1, 2, "set_files_access")
            }
            Label("About")
            Group { SettingNav(Icons.Rounded.Info, "About PageOne", "Version ${BuildConfig.VERSION_NAME}", { nav.navigate(Routes.ABOUT) }, 0, 1, "set_about") }
        }
    }

    if (themeDialog) ChoiceDialog("Theme", listOf("Follow system", "Light", "Dark"), s.themeMode.ordinal, { i -> vm.update { it.copy(themeMode = ThemeMode.entries[i]) }; themeDialog = false }) { themeDialog = false }
    if (scrollDialog) ChoiceDialog("Default scroll", listOf("Vertical scroll", "Page by page"), if (s.pageByPage) 1 else 0, { i -> vm.update { it.copy(pageByPage = i == 1) }; scrollDialog = false }) { scrollDialog = false }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp))

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(i == selected, role = Role.RadioButton) { onPick(i) }
                        .padding(vertical = 12.dp).testTag("choice_$i"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(i == selected, null); Spacer(Modifier.width(12.dp)); Text(o, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AboutScreen(vm: MainVm, nav: NavHostController) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var licences by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SmallTopBar("About", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.size(112.dp).shadow(3.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(Color(0xFFF7FAF4)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(150.dp))
            }
            Text("PageOne", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { taps++; if (taps >= 5) { taps = 0; nav.navigate(Routes.DIAGNOSTICS) } }.padding(8.dp).testTag("about_version"))
            Spacer(Modifier.height(20.dp))
            Group {
                SettingNav(Icons.Rounded.Person, "Developer", "Sushant Kumar", null, 0, 3, "about_dev")
                SettingNav(Icons.Rounded.Mail, "Email", "sushant.sare@gmail.com", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com"))) }
                }, 1, 3, "about_email")
                SettingNav(Icons.Rounded.Gavel, "Open-source licences", null, { licences = true }, 2, 3, "about_licences")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (licences) AlertDialog(
        onDismissRequest = { licences = false },
        title = { Text("Open-source licences") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    "PDFBox-Android (Tom Roush, Apache PDFBox)" to "Apache License 2.0",
                    "AndroidX, Jetpack Compose, Material 3" to "Apache License 2.0",
                    "Material Symbols" to "Apache License 2.0",
                    "Roboto Flex font" to "SIL Open Font License 1.1",
                    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
                ).forEach { (n, l) ->
                    Text(n, style = MaterialTheme.typography.titleSmall); Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                }
                Text("PDFs are shown with Android's own PDF renderer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { licences = false }) { Text("Close") } },
    )
}

/** Hidden: tap the version in About 5 times. Local crash log + local counters; nothing leaves the phone. */
@Composable
fun DiagnosticsScreen(vm: MainVm, nav: NavHostController) {
    val context = LocalContext.current
    var log by remember { mutableStateOf(runCatching { vm.a.crashFile().readText() }.getOrDefault("")) }
    val counts = remember { vm.a.local.counts() }
    Column(Modifier.fillMaxSize()) {
        SmallTopBar("Diagnostics", "Crash log and usage counts · on this phone only", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("diagnostics")) {
            Text("Usage", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            if (counts.isEmpty()) Text("Nothing counted yet", style = MaterialTheme.typography.bodyMedium)
            counts.toSortedMap().forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodyMedium) }
            Text("Crash log", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(log.ifBlank { "No crashes recorded." }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val f = vm.a.crashFile()
                    if (!f.exists()) { vm.message("No crash log to share"); return@Button }
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                }, enabled = log.isNotBlank()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Share log") }
                OutlinedButton(onClick = { vm.a.crashFile().delete(); log = "" }, enabled = log.isNotBlank()) { Text("Clear") }
            }
        }
    }
}
