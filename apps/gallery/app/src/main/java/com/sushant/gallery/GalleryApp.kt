package com.sushant.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.sushant.gallery.data.Diagnostics
import com.sushant.gallery.data.FileOps
import com.sushant.gallery.data.GalleryDb
import com.sushant.gallery.data.MediaSource
import com.sushant.gallery.data.Prefs
import com.sushant.gallery.data.Repository
import com.sushant.gallery.data.Vault
import com.sushant.gallery.ui.components.ThumbFetcher
import com.sushant.gallery.ui.components.ThumbKeyer
import com.sushant.gallery.ui.components.VaultFetcher
import com.sushant.gallery.ui.components.VaultKeyer
import com.sushant.gallery.work.BinCleaner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class GalleryApp : Application(), ImageLoaderFactory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { Prefs(this) }
    val diag by lazy { Diagnostics(this) }
    val db by lazy { GalleryDb.create(this) }
    val media by lazy { MediaSource(this) }
    val ops by lazy { FileOps(this, media) }
    val vault by lazy { Vault(this, db, media) }
    val repo by lazy { Repository(db, media, ops, vault, prefs, diag, appScope) }

    /** Hidden album unlock state; locks whenever the whole app goes to the background. */
    val vaultUnlocked = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        diag.install()
        BinCleaner.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (vaultUnlocked.value) { vaultUnlocked.value = false; vault.lock() }
            }
        })
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoFrameDecoder.Factory())
            add(ThumbKeyer())
            add(ThumbFetcher.Factory(this@GalleryApp))
            add(VaultKeyer())
            add(VaultFetcher.Factory(vault))
        }
        .crossfade(150)
        .respectCacheHeaders(false)
        .build()
}
