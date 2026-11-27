package com.sushant.pageone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sushant.pageone.data.DocRef
import com.sushant.pageone.data.Library
import com.sushant.pageone.ui.AppRoot
import com.sushant.pageone.ui.MainVm
import com.sushant.pageone.ui.theme.PageOneTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Something the activity was asked to do (open a PDF from another app, or a launcher shortcut). */
sealed class Incoming {
    data class Open(val ref: DocRef) : Incoming()
    data object Continue : Incoming()
    data object Search : Incoming()
    data object Starred : Incoming()
}

/** Volume keys → reader page turns (only consumed while the reader listens and the setting is on). */
object KeyBus {
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    @Volatile var listening = false
}

class MainActivity : AppCompatActivity() {
    internal val vm: MainVm by viewModels()
    val incoming = MutableStateFlow<Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { if (vm.settings.value.appLock) vm.locked.value = true }
        })
        setContent {
            val s by vm.settings.collectAsState()
            androidx.compose.runtime.LaunchedEffect(s.appLock) { applySecure(s.appLock) }
            PageOneTheme(s.themeMode, s.wallpaperColors, s.pureBlack) { AppRoot(vm, this) }
        }
    }

    private fun applySecure(on: Boolean) {
        // while app lock is on, Recents shows a blank card instead of your PDF
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() { super.onResume(); vm.refreshAccess() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        incoming.value = when (i.action) {
            Intent.ACTION_VIEW -> i.data?.let { Incoming.Open(refFor(it)) }
            Intent.ACTION_SEND -> streamOf(i)?.let { Incoming.Open(refFor(it)) }
            ACTION_CONTINUE -> Incoming.Continue
            ACTION_SEARCH -> Incoming.Search
            ACTION_STARRED -> Incoming.Starred
            else -> null
        } ?: incoming.value
    }

    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun refFor(uri: Uri): DocRef {
        val path = Library.pathFromUri(contentResolver, uri)
        if (path != null) return DocRef.ofFile(path)
        val name = Library.displayName(contentResolver, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
        return DocRef(uri.toString(), null, name)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (KeyBus.listening && vm.settings.value.volumeKeys &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.action == KeyEvent.ACTION_DOWN) KeyBus.events.tryEmit(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val ACTION_CONTINUE = "com.sushant.pageone.CONTINUE"
        const val ACTION_SEARCH = "com.sushant.pageone.SEARCH"
        const val ACTION_STARRED = "com.sushant.pageone.STARRED"
    }
}
