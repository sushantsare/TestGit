package com.sushant.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var taps by remember { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Jetpack Compose works! 🎉", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Text("Built by Claude via GitHub Actions", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { taps++ }) { Text("Tapped $taps times") }
                    }
                }
            }
        }
    }
}
