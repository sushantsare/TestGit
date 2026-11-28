package com.sushant.grabbit.engine

import android.content.Context
import com.sushant.grabbit.data.Prefs
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Wraps yt-dlp (+ Python runtime) and ffmpeg. */
object Engine {
    sealed interface State {
        data object Starting : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Starting)
    val version = MutableStateFlow<String?>(null)
    val updating = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var aria2Ready = false

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        state.value = State.Starting
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                // Multi-connection downloader; if it can't start, downloads fall back to yt-dlp's own
                aria2Ready = runCatching { com.yausername.aria2c.Aria2c.getInstance().init(app) }.isSuccess
                version.value = runCatching { YoutubeDL.getInstance().version(app) }.getOrNull()
                state.value = State.Ready
                // Daily check, or straight away if the chosen channel (Nightly / Stable) isn't the installed one
                if (Prefs.autoUpdate && (Prefs.engineChannel != Prefs.engineChannelInstalled ||
                        System.currentTimeMillis() - Prefs.lastUpdate > 24L * 3600 * 1000)) {
                    runCatching { update(app) }
                }
            } catch (e: Throwable) {
                state.value = State.Failed(e.message ?: e.toString())
            }
        }
    }

    suspend fun awaitReady(): Boolean = state.first { it !is State.Starting } is State.Ready

    fun failMessage(): String = (state.value as? State.Failed)?.message
        ?.let { "Download engine failed to start: $it" }
        ?: "Download engine is not ready."

    /** Updates yt-dlp to the newest release so sites keep working. Returns a status message. */
    suspend fun update(ctx: Context): String = withContext(Dispatchers.IO) {
        if (!awaitReady()) return@withContext failMessage()
        updating.value = true
        try {
            val channel = if (Prefs.engineChannel == "stable") YoutubeDL.UpdateChannel.STABLE else YoutubeDL.UpdateChannel.NIGHTLY
            val status = YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, channel)
            Prefs.lastUpdate = System.currentTimeMillis()
            Prefs.engineChannelInstalled = Prefs.engineChannel
            val v = runCatching { YoutubeDL.getInstance().version(ctx.applicationContext) }.getOrNull()
            version.value = v
            if ("$status".contains("DONE")) "Engine updated to $v" else "Engine is already up to date ($v)"
        } catch (e: Throwable) {
            "Update failed: ${e.message ?: e}"
        } finally {
            updating.value = false
        }
    }
}
