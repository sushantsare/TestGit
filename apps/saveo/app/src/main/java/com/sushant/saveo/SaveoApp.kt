package com.sushant.saveo

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sushant.saveo.data.Diagnostics
import com.sushant.saveo.data.Prefs
import com.sushant.saveo.data.Repository
import com.sushant.saveo.data.SaveoDb
import com.sushant.saveo.work.Background
import com.sushant.saveo.work.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SaveoApp : Application(), ImageLoaderFactory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val prefs by lazy { Prefs(this) }
    val diagnostics by lazy { Diagnostics(this) }
    val database by lazy { SaveoDb.create(this) }
    val repository by lazy { Repository(this, database, prefs, diagnostics) }

    /** App lock: true while the lock screen must be shown. */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundAt = 0L

    override fun onCreate() {
        super.onCreate()
        // Small one-time reads of settings at startup happen before StrictMode is armed
        diagnostics.install()
        prefs.settings.value
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectActivityLeaks().penaltyLog().build())
        }
        Notifier.createChannel(this)
        val s = prefs.settings.value
        _locked.value = s.appLock
        Background.sync(this, s.alerts, s.autoSave)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Lock again after one minute in the background
                if (prefs.settings.value.appLock && backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > LOCK_AFTER_MS) _locked.value = true
            }
            override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
        })
    }

    fun unlock() { _locked.value = false; backgroundAt = 0 }
    fun lockNow() { _locked.value = true }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(120)
        .respectCacheHeaders(false)
        .build()

    companion object { const val LOCK_AFTER_MS = 60_000L }
}
