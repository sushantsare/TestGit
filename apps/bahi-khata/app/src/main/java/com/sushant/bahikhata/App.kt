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

    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        prefs = Prefs(this)
        db = AppDb.open(this)
        repo = Repo(this, db, prefs)
        sms = SmsEngine(this, repo, appScope)
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
