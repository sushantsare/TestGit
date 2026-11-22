package com.sushant.tarang

import android.app.Application
import android.os.Build
import com.sushant.tarang.data.Prefs
import com.sushant.tarang.data.Repo
import com.sushant.tarang.data.TarangDb
import com.sushant.tarang.playback.PlaybackHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TarangApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { Prefs(this) }
    val db by lazy { TarangDb.create(this) }
    val repo by lazy { Repo(this, db, prefs, appScope) }
    val hub by lazy { PlaybackHub() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Diagnostics.install(this)
    }

    companion object {
        lateinit var instance: TarangApp
            private set
    }
}

/** Crash details are saved to a file on this phone only (shown and shareable from Settings → Diagnostics). */
object Diagnostics {
    fun file(app: Application) = File(app.filesDir, "crash.log")

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val time = SimpleDateFormat("dd MMM yyyy, h:mm:ss a", Locale.ENGLISH).format(Date())
                val v = app.packageManager.getPackageInfo(app.packageName, 0).versionName
                val text = buildString {
                    append("=== Crash · ").append(time).append('\n')
                    append("Tarang ").append(v).append(" · Android ").append(Build.VERSION.RELEASE)
                    append(" (API ").append(Build.VERSION.SDK_INT).append(") · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append("Thread: ").append(t.name).append('\n')
                    append(e.stackTraceToString().take(6000)).append("\n\n")
                }
                val f = file(app)
                if (f.length() > 200_000) f.writeText(f.readText().takeLast(100_000))
                f.appendText(text)
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun crashes(app: Application): List<String> =
        file(app).takeIf { it.exists() }?.readText()?.split("=== Crash · ")?.filter { it.isNotBlank() } ?: emptyList()
}
