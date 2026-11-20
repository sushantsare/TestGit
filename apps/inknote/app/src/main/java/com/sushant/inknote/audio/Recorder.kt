package com.sushant.inknote.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sushant.inknote.InkApp
import com.sushant.inknote.MainActivity
import com.sushant.inknote.R
import com.sushant.inknote.data.AudioKind
import com.sushant.inknote.data.Ids
import com.sushant.inknote.data.RecordingEntity
import com.sushant.inknote.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecState(val noteId: Long, val recId: Long, val file: File, val startedAt: Long)

/** Records lecture audio into a note. Strokes written meanwhile are stamped with (recId, ms) so playback can sync. */
class Recorder(private val context: Context, private val repo: Repository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<RecState?>(null)
    val state: StateFlow<RecState?> = _state.asStateFlow()
    private var mr: MediaRecorder? = null

    fun elapsedMs(): Long = _state.value?.let { SystemClock.elapsedRealtime() - it.startedAt } ?: 0

    fun stamp(noteId: Long): Pair<Long, Long> = _state.value?.takeIf { it.noteId == noteId }?.let { it.recId to (SystemClock.elapsedRealtime() - it.startedAt) } ?: (-1L to -1L)

    /** Must be called while the app is visible and the microphone permission is granted. */
    suspend fun start(noteId: Long): Boolean {
        if (_state.value != null) return false
        val name = "rec_${Ids.next()}.m4a"
        val f = repo.files.audio(noteId, name)
        val label = context.getString(com.sushant.inknote.R.string.recording_label, SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date()))
        val recId = repo.addRecording(RecordingEntity(noteId = noteId, file = name, name = label, durationMs = 0, createdAt = System.currentTimeMillis()))
        val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder())
        return try {
            // file and recorder setup touch the disk: keep them off the main thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            f.parentFile?.mkdirs()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000); r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            }
            mr = r
            _state.value = RecState(noteId, recId, f, SystemClock.elapsedRealtime())
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
            true
        } catch (e: Exception) {
            r.release(); f.delete()
            repo.recordings(noteId).firstOrNull { it.id == recId }?.let { repo.deleteRecording(it) }
            false
        }
    }

    fun stop() {
        val s = _state.value ?: return
        val dur = SystemClock.elapsedRealtime() - s.startedAt
        val rec = mr; mr = null
        _state.value = null
        context.stopService(Intent(context, RecordingService::class.java))
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { rec?.stop() }; rec?.release() }
            val r = repo.recordings(s.noteId).firstOrNull { it.id == s.recId } ?: return@launch
            if (!s.file.exists() || s.file.length() == 0L) repo.deleteRecording(r)
            else { repo.db.recordings().update(r.copy(durationMs = durationOf(s.file).takeIf { it > 0 } ?: dur)); repo.secureIfLocked(s.noteId) }
        }
    }

    companion object {
        fun durationOf(f: File): Long = runCatching {
            MediaMetadataRetriever().run { setDataSource(f.absolutePath); val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0; release(); d }
        }.getOrDefault(0)

        /** Adds an existing audio file (song or clip) to a note. */
        suspend fun importAudio(context: Context, repo: Repository, noteId: Long, uri: android.net.Uri): Boolean {
            val display = repo.displayName(uri)
            val ext = display.substringAfterLast('.', "m4a").take(5)
            val name = "file_${Ids.next()}.$ext"
            val f = repo.files.audio(noteId, name)
            val ok = runCatching { context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } != null }.getOrDefault(false)
            if (!ok || f.length() == 0L) { f.delete(); return false }
            repo.addRecording(RecordingEntity(noteId = noteId, file = name, name = display, durationMs = durationOf(f), createdAt = System.currentTimeMillis(), kind = AudioKind.FILE))
            repo.secureIfLocked(noteId)
            return true
        }
    }
}

class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { (application as InkApp).recorder.stop(); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.recording_now))
            .setContentText(getString(R.string.recording_tap_open))
            .setUsesChronometer(true).setWhen(System.currentTimeMillis())
            .setOngoing(true).setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, 7, n, type) }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) { (application as InkApp).recorder.stop(); super.onTaskRemoved(rootIntent) }

    companion object { const val CHANNEL = "recording"; const val ACTION_STOP = "com.sushant.inknote.STOP_RECORDING" }
}
