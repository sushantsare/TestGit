package com.sushant.speedtile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sushant.speedtile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(version: String, onEmail: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("nav_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0F4C47)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(R.drawable.ic_speed), null,
                            modifier = Modifier.size(46.dp),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("Speed Tile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Your live GPS speed in km/h, right in Quick Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AppCard {
                SectionTitle("How it works")
                Spacer(Modifier.size(8.dp))
                Bullet("Add the Speed tile once: pull down Quick Settings, tap the pencil (Edit) and drag Speed into your tiles.")
                Bullet("Tap the tile: Quick Settings closes for a moment while GPS starts. Pull it down again and the tile shows your km/h live.")
                Bullet("Close Quick Settings (or tap the tile again) and speed switches off. You can change this in Settings.")
                Bullet("Speed comes straight from your phone GPS receiver, updated every second.")
                Bullet("Long-press the tile to open this screen: speedometer, trip and settings.")
                Bullet("If you do not pull down within 30 seconds, or stop moving for a while, GPS turns itself off to save battery.")
            }

            AppCard {
                SectionTitle("Privacy")
                Spacer(Modifier.size(8.dp))
                Bullet("Location is used only while speed is on.")
                Bullet("Your location is never saved. Only trip totals (top speed, distance, time) are kept.")
                Bullet("Works fully offline. The app has no internet access, so nothing leaves your phone.")
            }

            AppCard {
                SectionTitle("Developer")
                Spacer(Modifier.size(8.dp))
                Text("Developed by Sushant Kumar", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.size(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onEmail)
                        .padding(vertical = 8.dp, horizontal = 4.dp).testTag("about_email"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Email, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("sushant.sare@gmail.com", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
