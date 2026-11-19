package com.sushant.gymmanager.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sushant.gymmanager.BuildConfig
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.theme.WhatsAppGreen
import com.sushant.gymmanager.util.WhatsAppHelper
import com.sushant.gymmanager.util.toast

object Developer {
    const val NAME = "Sushant Kumar"
    const val EMAIL = "sushant.sare@gmail.com"
    const val PHONE = "8877612216"
    const val PHONE_DISPLAY = "+91 88776 12216"
}

@Composable
fun AboutScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { GymTopBar(t("About"), t("App and developer information"), onMenu = onOpenDrawer) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("about"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App
            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFC2410C)))),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Text("Gym Manager", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        t("Version %s", BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        t("Members, attendance, payments, receipts, reports and backups for your gym. All data stays safely on this phone."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Developer
            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(t("Developer"), style = MaterialTheme.typography.titleMedium)
                }
                ContactRow(Icons.Default.Person, t("Name"), Developer.NAME, onClick = null)
                Divider()
                ContactRow(Icons.Default.Email, t("Email"), Developer.EMAIL, onClick = { email(context) }, tag = "about_email")
                Divider()
                ContactRow(Icons.Default.Call, t("Phone"), Developer.PHONE_DISPLAY, onClick = { dial(context) }, tag = "about_phone")
                Divider()
                ContactRow(
                    Icons.AutoMirrored.Filled.Chat, "WhatsApp", Developer.PHONE_DISPLAY,
                    onClick = { WhatsAppHelper.open(context, Developer.PHONE, "Hello Sushant, I am using Gym Manager.") },
                    tint = WhatsAppGreen
                )
            }

        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    tag: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val mod = if (tag != null) Modifier.testTag(tag) else Modifier
    if (onClick != null) Surface(onClick = onClick, color = Color.Transparent, modifier = mod) { content() }
    else Box(mod) { content() }
}

private fun email(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Developer.EMAIL}"))
        .putExtra(Intent.EXTRA_SUBJECT, "Gym Manager")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { context.toast(Developer.EMAIL) }
}

private fun dial(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91${Developer.PHONE}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { context.toast(Developer.PHONE_DISPLAY) }
}
