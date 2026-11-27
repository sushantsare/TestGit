package com.sushant.pageone

import android.app.Application
import android.util.Log
import com.sushant.pageone.data.Library
import com.sushant.pageone.data.LocalPrefs
import com.sushant.pageone.data.PageOneDb
import com.sushant.pageone.data.Prefs
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PageOneApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { PageOneDb.create(this) }
    val prefs by lazy { Prefs(this) }
    val local by lazy { LocalPrefs(this) }
    val library by lazy { Library(this, db, prefs, appScope) }

    override fun onCreate() {
        super.onCreate()
        installCrashLog()
        PDFBoxResourceLoader.init(applicationContext)
    }

    /** Crashes are written to a local log (Diagnostics). Nothing leaves the phone. */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = crashFile()
                f.parentFile?.mkdirs()
                if (f.length() > 200_000) f.writeText("")
                f.appendText(
                    "=== " + SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.ENGLISH).format(Date()) +
                        " · v${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL} · thread ${t.name}\n" +
                        Log.getStackTraceString(e) + "\n",
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun crashFile() = File(filesDir, "crash/crash.log")
}
