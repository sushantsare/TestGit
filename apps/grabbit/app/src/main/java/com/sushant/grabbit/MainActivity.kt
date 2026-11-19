package com.sushant.grabbit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sushant.grabbit.ui.GrabbitRoot
import com.sushant.grabbit.ui.GrabbitTheme
import com.sushant.grabbit.ui.MainViewModel

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
    }

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            GrabbitTheme { GrabbitRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!text.isNullOrBlank()) vm.onShared(text)
        } else if (intent.hasExtra(EXTRA_TAB)) {
            vm.tab = intent.getIntExtra(EXTRA_TAB, 0)
        }
    }
}
