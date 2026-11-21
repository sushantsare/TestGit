package com.sushant.gallery.ui.onboarding

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sushant.gallery.R
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.components.EmptyState
import com.sushant.gallery.ui.components.Illus
import com.sushant.gallery.ui.theme.LocalSpacing

/** First run: explains why "All files access" is needed and opens the system switch. */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val sp = LocalSpacing.current
    var declined by rememberSaveable { mutableStateOf(vm.prefs.onboarded) }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.prefs.onboarded = true
        if (r.values.all { it }) vm.setAccess(true) else declined = true
    }
    fun ask() {
        vm.prefs.onboarded = true
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            runCatching { ctx.startActivity(i) }.onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
        } else {
            legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        if (declined) {
            Column(Modifier.fillMaxSize().testTag("denied")) {
                Text(stringResource(R.string.tab_photos), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 18.dp))
                EmptyState(Illus.LOCK, stringResource(R.string.denied_title), stringResource(R.string.denied_body),
                    actionLabel = stringResource(R.string.open_settings), onAction = { ask() })
            }
            return@Box
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).testTag("onboarding"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFFF1FAF8)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_fg), null, modifier = Modifier.size(180.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.onb_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(stringResource(R.string.onb_why_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.onb_why_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = { ask() }, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(48.dp).testTag("allow")) { Text(stringResource(R.string.allow_access)) }
            TextButton(onClick = { vm.prefs.onboarded = true; declined = true }, modifier = Modifier.padding(top = 8.dp).testTag("not_now")) { Text(stringResource(R.string.not_now)) }
            Spacer(Modifier.height(sp.xl))
        }
    }
}
