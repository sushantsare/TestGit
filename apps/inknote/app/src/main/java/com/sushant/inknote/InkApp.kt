package com.sushant.inknote

import android.app.Application
import android.os.StrictMode
import com.sushant.inknote.audio.Recorder
import com.sushant.inknote.data.AppDatabase
import com.sushant.inknote.data.NoteFiles
import com.sushant.inknote.data.Repository
import com.sushant.inknote.data.SecurePrefs
import com.sushant.inknote.data.Settings
import com.sushant.inknote.io.Exporter
import com.sushant.inknote.work.PurgeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InkApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db by lazy { AppDatabase.build(this) }
    val files by lazy { NoteFiles(this) }
    val repo by lazy { Repository(this, db, files) }
    val settings by lazy { Settings(this) }
    val securePrefs by lazy { SecurePrefs(this) }
    val recorder by lazy { Recorder(this, repo, appScope) }
    val exporter by lazy { Exporter(this, repo) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
        // Open preferences, folders and the database off the main thread so the first screen never waits on disk.
        appScope.launch(Dispatchers.IO) { files; settings; securePrefs; repo; db.openHelper.writableDatabase; PurgeWorker.schedule(this@InkApp) }
    }
}
