package com.sushant.bahikhata

import android.app.Application
import com.sushant.bahikhata.data.AppDb
import com.sushant.bahikhata.data.Diagnostics
import com.sushant.bahikhata.data.Prefs
import com.sushant.bahikhata.data.Repo
import com.sushant.bahikhata.sms.SmsEngine
import com.sushant.bahikhata.work.Notify
import com.sushant.bahikhata.work.Workers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency container (small enough not to need Hilt). */
class App : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var prefs: Prefs
        private set
    lateinit var db: AppDb
        private set
    lateinit var repo: Repo
        private set
    lateinit var sms: SmsEngine
        private set
    /** False when the phone's secure key store couldn't unlock the encrypted data. The app then shows an
     *  explanation instead of ever creating an unencrypted database (fail closed). */
    var ready = false
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {   // audit T4: main-thread disk/network and leaked closables are logged in debug builds
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectActivityLeaks().penaltyLog().build())
        }
        Diagnostics.install(this)
        prefs = Prefs(this)
        try {
            db = AppDb.open(this)
            repo = Repo(this, db, prefs)
            sms = SmsEngine(this, repo, appScope)
            ready = true
        } catch (t: Throwable) {
            android.util.Log.e("Bahi", "secure storage unavailable", t)
            Diagnostics.count(this, "storage_locked")
            return
        }
        Notify.channels(this)
        appScope.launch {
            repo.ensureCategories()
            File(cacheDir, "open").deleteRecursively()
            File(cacheDir, "share").deleteRecursively()
        }
        Workers.schedule(this)
        sms.startObserver()
    }
}
