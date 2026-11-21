package com.sushant.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gallery.data.Diagnostics
import com.sushant.gallery.ui.AppRoot
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.theme.GalleryTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GalleryApp
        if (savedInstanceState == null) app.diag.count(Diagnostics.OPENS)
        vm.setAccess(hasAccess())
        splash.setKeepOnScreenCondition { vm.hasAccess.value && !app.repo.loaded.value && System.currentTimeMillis() - start < 1500 }
        val external = savedInstanceState == null && vm.openExternal(intent) != null
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle()
            val dynamic by app.prefs.dynamicColor.collectAsStateWithLifecycle()
            GalleryTheme(theme, dynamic) { AppRoot(vm, startExternal = external) }
        }
    }

    private val start = System.currentTimeMillis()

    override fun onResume() {
        super.onResume()
        vm.setAccess(hasAccess())
        (application as GalleryApp).repo.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}
