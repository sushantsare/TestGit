package com.sushant.gymmanager.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.sushant.gymmanager.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AppLockScreen(gymName: String, onUnlock: () -> Unit, onExit: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    BackHandler { onExit() } // Back leaves the app; it never skips the lock
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.background, scheme.background)))
            .systemBarsPadding()
            .testTag("app_lock"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(104.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.75f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FitnessCenter, null, tint = scheme.onPrimary, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(gymName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Surface(shape = CircleShape, color = scheme.secondaryContainer) {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp), tint = scheme.onSecondaryContainer)
                    Spacer(Modifier.size(6.dp))
                    Text(t("App lock is on"), style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(48.dp))
            Icon(Icons.Default.Fingerprint, null, tint = scheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(t("Unlock Application"))
            }
        }
    }
}
