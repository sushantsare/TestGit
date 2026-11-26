package com.sushant.tarang.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sushant.tarang.MainActivity
import com.sushant.tarang.R
import com.sushant.tarang.TarangApp
import com.sushant.tarang.data.*
import com.sushant.tarang.widget.WidgetState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future

/**
 * Plays the music (Media3 ExoPlayer) in the background with the media notification, lock screen and Bluetooth controls.
 * Also owns: the one saved queue (with real shuffle), sleep timer, crossfade, the sound processing (EQ), listening stats and the widget.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private val app get() = application as TarangApp
    private val repo get() = app.repo
    private val hub get() = app.hub
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fx = TarangFx()
    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null
    private var restored = CompletableDeferred<Unit>()

    // stats of the song being listened to now
    private var statSong = -1L
    private var statRow = 0L
    private var statMs = 0L
    private var statStartedAt = 0L
    private var lastTick = 0L
    private var lastSave = 0L
    private var transitionAt = 0L

    override fun onCreate() {
        super.onCreate()
        val rf = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(fx))
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        // MP3s without a length header (common from downloaders) get their real length and exact seeking
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setMp3ExtractorFlags(androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            .setConstantBitrateSeekingEnabled(true)
        exo = ExoPlayer.Builder(this, rf, androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)          // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // Hide the cover on the lock screen / notification when the user turned that off.
        val player = object : ForwardingPlayer(exo) {
            override fun getMediaMetadata(): MediaMetadata {
                val m = super.getMediaMetadata()
                return if (app.prefs.now.lockCover) m else m.buildUpon().setArtworkData(null, null).setArtworkUri(null).build()
            }
        }
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(Callback()).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setChannelName(R.string.channel_playback).build()
            .apply { setSmallIcon(R.drawable.ic_notification) })

        exo.addListener(Listener())
        exo.shuffleModeEnabled = false                       // shuffle is a real reordering (see shuffleNow)
        exo.repeatMode = app.prefs.repeatMode
        hub.shuffle.value = app.prefs.shuffleOn
        scope.launch {
            // 1.3.0 has one queue: the queue that was active (any of the old 3) becomes the queue
            val oldSlot = app.prefs.activeQueue
            restore(oldSlot, play = false)
            restored.complete(Unit)
            if (oldSlot != 1) { app.prefs.activeQueue = 1; saveQueueNow() }
        }
        scope.launch {
            app.prefs.settings.collectLatest { s ->
                fx.update(FxParams(s.eqEnabled, s.eqBands, s.bassBoost, s.virtualizer))
                exo.playbackParameters = PlaybackParameters(s.speed, if (s.keepPitch) 1f else s.speed)
                updateButtons()
            }
        }
        scope.launch { repo.likedIds.collectLatest { updateButtons() } }
        scope.launch { tickLoop() }
        registerDeviceWatcher()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing when the app is swiped away; stop the service if nothing is playing.
        if (!exo.playWhenReady || exo.mediaItemCount == 0) { saveQueueNow(); stopSelf() }
    }

    override fun onDestroy() {
        saveQueueNow(); flushStats()
        unregisterDeviceWatcher()
        session?.run { player.release(); release() }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ session callback
    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().apply {
                listOf(Cmd.LIKE, Cmd.SHUFFLE, Cmd.REPEAT, Cmd.SLEEP, Cmd.END_QUEUE, Cmd.CONTEXT, Cmd.CLEAR_QUEUE, Cmd.SET_SHUFFLE)
                    .forEach { add(SessionCommand(it, Bundle.EMPTY)) }
            }.build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .setMediaButtonPreferences(buttons())
                .build()
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            scope.future { mediaItems.mapNotNull { resolve(it) }.toMutableList() }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            scope.launch { handle(customCommand.customAction, args) }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            scope.future {
                val q = repo.dao.queue(1)
                val items = q?.ids?.split(",")?.mapNotNull { it.toLongOrNull() }?.mapNotNull { itemFor(it) }.orEmpty()
                if (items.isEmpty()) throw UnsupportedOperationException("Nothing to resume")
                MediaSession.MediaItemsWithStartPosition(items, q!!.index.coerceIn(0, items.lastIndex), q.positionMs)
            }
    }

    private suspend fun itemFor(id: Long): MediaItem? = repo.dao.song(id)?.toSong()?.let { mediaItem(it) }

    private suspend fun resolve(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        val id = item.mediaId.toLongOrNull() ?: return null
        return itemFor(id)
    }

    private suspend fun handle(action: String, args: Bundle) {
        when (action) {
            Cmd.LIKE -> currentId()?.let { repo.toggleLike(it) }
            Cmd.SHUFFLE -> setShuffle("toggle")
            Cmd.SET_SHUFFLE -> setShuffle(args.getString("mode") ?: "toggle")
            Cmd.REPEAT -> exo.repeatMode = when (exo.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
            Cmd.SLEEP -> {
                val min = args.getInt("minutes", 0)
                hub.sleepFade.value = args.getBoolean("fade", true)
                hub.sleepAfterSong.value = args.getBoolean("afterSong", false)
                hub.sleepEndsAt.value = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L
            }
            Cmd.END_QUEUE -> {
                flushStats(); exo.stop(); exo.clearMediaItems()
                hub.contextLabel.value = ""; hub.sleepEndsAt.value = 0; hub.sleepAfterSong.value = false
                setShuffle("off"); saveQueueNow()
            }
            Cmd.CONTEXT -> { hub.contextKind.value = args.getString("kind") ?: "Folder"; hub.contextLabel.value = args.getString("label") ?: "" }
            Cmd.CLEAR_QUEUE -> {
                // keep only the song playing now
                val n = exo.mediaItemCount; val cur = exo.currentMediaItemIndex
                if (n > 0 && cur >= 0) {
                    if (cur + 1 < n) exo.removeMediaItems(cur + 1, n)
                    if (cur > 0) exo.removeMediaItems(0, cur)
                }
                if (hub.shuffle.value) app.prefs.orderBeforeShuffle = listOfNotNull(currentId())
                saveQueueNow()
            }
        }
        updateButtons()
    }

    // ------------------------------------------------------------------ shuffle (a real reordering, so Up next shows the true order)
    private fun setShuffle(mode: String) {
        when (mode) {
            "toggle" -> if (hub.shuffle.value) unshuffle() else shuffleNow()
            "apply" -> shuffleNow()
            "off" -> { hub.shuffle.value = false; app.prefs.shuffleOn = false; app.prefs.orderBeforeShuffle = emptyList() }
        }
        updateButtons()
    }

    /** Current song stays playing and moves to the top; everything else is shuffled after it. The old order is kept. */
    private fun shuffleNow() {
        val n = exo.mediaItemCount
        if (n > 1) {
            val cur = exo.currentMediaItemIndex.coerceIn(0, n - 1)
            val items = (0 until n).map { exo.getMediaItemAt(it) }
            app.prefs.orderBeforeShuffle = items.mapNotNull { it.mediaId.toLongOrNull() }
            val rest = items.filterIndexed { i, _ -> i != cur }.shuffled()
            if (cur + 1 < n) exo.removeMediaItems(cur + 1, n)
            if (cur > 0) exo.removeMediaItems(0, cur)
            exo.addMediaItems(rest)
        } else app.prefs.orderBeforeShuffle = (0 until n).mapNotNull { exo.getMediaItemAt(it).mediaId.toLongOrNull() }
        hub.shuffle.value = true; app.prefs.shuffleOn = true
        saveQueueNow()
    }

    /** Puts the queue back in the order it had before shuffling; songs added meanwhile stay, after the others. */
    private fun unshuffle() {
        val n = exo.mediaItemCount
        val before = app.prefs.orderBeforeShuffle
        hub.shuffle.value = false; app.prefs.shuffleOn = false; app.prefs.orderBeforeShuffle = emptyList()
        if (n <= 1 || before.isEmpty()) { saveQueueNow(); return }
        val cur = exo.currentMediaItemIndex.coerceIn(0, n - 1)
        val items = (0 until n).map { exo.getMediaItemAt(it) }
        val order = QueueOrder.restore(items.map { it.mediaId }, before)
        val k = order.indexOf(cur)
        if (cur + 1 < n) exo.removeMediaItems(cur + 1, n)
        if (cur > 0) exo.removeMediaItems(0, cur)
        if (k > 0) exo.addMediaItems(0, order.subList(0, k).map { items[it] })
        if (k + 1 < order.size) exo.addMediaItems(order.subList(k + 1, order.size).map { items[it] })
        saveQueueNow()
    }

    // ------------------------------------------------------------------ the queue

    private suspend fun restore(slot: Int, play: Boolean) {
        val q = repo.dao.queue(slot)
        val items = q?.ids?.split(",")?.mapNotNull { it.toLongOrNull() }?.mapNotNull { itemFor(it) }.orEmpty()
        val ctx = q?.contextLabel.orEmpty()
        hub.contextKind.value = ctx.substringBefore('|', "Folder").ifEmpty { "Folder" }
        hub.contextLabel.value = ctx.substringAfter('|', "")
        if (items.isEmpty()) { exo.stop(); exo.clearMediaItems(); return }
        exo.setMediaItems(items, q!!.index.coerceIn(0, items.lastIndex), q.positionMs.coerceAtLeast(0))
        exo.prepare()
        if (play) exo.play()
    }

    private fun queueEntity(): QueueEntity {
        val ids = (0 until exo.mediaItemCount).joinToString(",") { exo.getMediaItemAt(it).mediaId }
        return QueueEntity(1, ids, exo.currentMediaItemIndex.coerceAtLeast(0), exo.currentPosition.coerceAtLeast(0),
            "${hub.contextKind.value}|${hub.contextLabel.value}")
    }

    private fun saveQueueNow() {
        if (!this::exo.isInitialized || !restored.isCompleted) return
        val q = queueEntity()
        app.appScope.launch(Dispatchers.IO) { repo.dao.putQueue(q) }
        lastSave = SystemClock.elapsedRealtime()
    }


    // ------------------------------------------------------------------ player events
    private inner class Listener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushStats()
            transitionAt = SystemClock.elapsedRealtime()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && hub.sleepAfterSong.value) {
                exo.pause(); hub.sleepAfterSong.value = false
            }
            saveQueueNow(); updateButtons(); updateWidget()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) hub.playedThisSession.value = true else { flushStats(); saveQueueNow() }
            updateWidget()
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueueNow()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateButtons()
        override fun onRepeatModeChanged(repeatMode: Int) { app.prefs.repeatMode = repeatMode; updateButtons() }
        override fun onPlayerError(error: PlaybackException) {
            val title = exo.currentMediaItem?.mediaMetadata?.title ?: "this song"
            hub.error.value = "Couldn't play $title. Skipping to the next song."
            if (exo.hasNextMediaItem()) { exo.seekToNextMediaItem(); exo.prepare(); exo.play() } else exo.stop()
        }
    }

    private fun currentId(): Long? = exo.currentMediaItem?.mediaId?.toLongOrNull()

    // ------------------------------------------------------------------ ticking: stats, sleep timer, crossfade
    private suspend fun tickLoop() {
        lastTick = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            delay(250)
            val now = SystemClock.elapsedRealtime(); val dt = now - lastTick; lastTick = now
            if (exo.isPlaying) {
                val id = currentId()
                if (id != null) {
                    if (id != statSong) { flushStats(); statSong = id; statMs = 0; statRow = 0; statStartedAt = System.currentTimeMillis() }
                    statMs += (dt * exo.playbackParameters.speed).toLong().coerceAtMost(1000)
                    if (statRow == 0L && statMs >= 1000) {
                        val sid = statSong; val started = statStartedAt
                        statRow = -1   // inserting
                        statRow = repo.dao.insertPlay(PlayEntity(songId = sid, startedAt = started, listenedMs = statMs, counted = statMs >= COUNT_MS))
                    } else if (statRow > 0 && now - lastSave > 5000) {
                        repo.dao.updatePlay(statRow, statMs, statMs >= COUNT_MS)
                    }
                }
                if (now - lastSave > 5000) saveQueueNow()
            }
            // sleep timer
            var sleepVol = 1f
            val ends = hub.sleepEndsAt.value
            if (ends > 0) {
                val left = ends - System.currentTimeMillis()
                if (left <= 0) { exo.pause(); hub.sleepEndsAt.value = 0 }
                else if (hub.sleepFade.value && left < 30_000) sleepVol = (left / 30_000f).coerceIn(0f, 1f)
            }
            // crossfade (fade the end of a song out and the next one in)
            var crossVol = 1f
            val s = app.prefs.now
            if (s.crossfadeSec > 0 && !s.gapless && exo.mediaItemCount > 0) {
                val cf = s.crossfadeSec * 1000L
                val dur = exo.duration
                if (dur > 0 && exo.hasNextMediaItem()) {
                    val left = dur - exo.currentPosition
                    if (left in 0 until cf) crossVol = (left.toFloat() / cf).coerceIn(0.05f, 1f)
                }
                val since = now - transitionAt
                if (since in 0 until cf) crossVol = minOf(crossVol, (since.toFloat() / cf).coerceIn(0.05f, 1f))
            }
            exo.volume = sleepVol * crossVol
        }
    }

    private fun flushStats() {
        if (statSong < 0) return
        val row = statRow; val ms = statMs; val sid = statSong; val started = statStartedAt
        statSong = -1; statRow = 0; statMs = 0
        if (ms < 1000) return
        app.appScope.launch(Dispatchers.IO) {
            if (row > 0) repo.dao.updatePlay(row, ms, ms >= COUNT_MS)
            else repo.dao.insertPlay(PlayEntity(songId = sid, startedAt = started, listenedMs = ms, counted = ms >= COUNT_MS))
        }
    }

    // ------------------------------------------------------------------ notification buttons (user picks 2 in Settings)
    private fun buttons(): List<CommandButton> {
        val liked = currentId()?.let { repo.isLiked(it) } ?: false
        val chosen = app.prefs.now.notifButtons.take(2)
        return chosen.mapIndexed { i, b ->
            val builder = when (b) {
                NotifButton.LIKE -> CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(if (liked) "Unlike" else "Like").setSessionCommand(SessionCommand(Cmd.LIKE, Bundle.EMPTY))
                NotifButton.SHUFFLE -> CommandButton.Builder(if (hub.shuffle.value) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                    .setDisplayName(if (hub.shuffle.value) "Shuffle on" else "Shuffle off").setSessionCommand(SessionCommand(Cmd.SHUFFLE, Bundle.EMPTY))
                NotifButton.REPEAT -> CommandButton.Builder(when (exo.repeatMode) { Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE; Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL; else -> CommandButton.ICON_REPEAT_OFF })
                    .setDisplayName("Repeat").setSessionCommand(SessionCommand(Cmd.REPEAT, Bundle.EMPTY))
            }
            builder.setSlots(if (i == 0) CommandButton.SLOT_BACK_SECONDARY else CommandButton.SLOT_FORWARD_SECONDARY).build()
        }
    }

    private fun updateButtons() { session?.setMediaButtonPreferences(buttons()) }

    // ------------------------------------------------------------------ widget
    private var widgetJob: Job? = null
    private fun updateWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(200)
            val id = currentId()
            val song = id?.let { repo.dao.song(it)?.toSong() }
            WidgetState.publish(this@PlaybackService, song, exo.isPlaying, song?.let { repo.covers.load(it, 400) })
        }
    }

    // ------------------------------------------------------------------ resume when headphones / Bluetooth connect
    private var deviceCb: AudioDeviceCallback? = null
    private var deviceCbAt = 0L
    private fun registerDeviceWatcher() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        deviceCbAt = SystemClock.elapsedRealtime()
        deviceCb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (SystemClock.elapsedRealtime() - deviceCbAt < 1500) return   // first call lists devices already connected
                if (!app.prefs.now.resumeOnConnect || exo.isPlaying || exo.mediaItemCount == 0) return
                val outs = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 26 /* BLE headset */, 27 /* BLE speaker */)
                if (addedDevices.any { it.isSink && it.type in outs }) exo.play()
            }
        }
        am.registerAudioDeviceCallback(deviceCb, Handler(Looper.getMainLooper()))
    }
    private fun unregisterDeviceWatcher() {
        deviceCb?.let { (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(it) }
    }

    companion object {
        const val COUNT_MS = 30_000L   // a play counts in stats after 30 seconds

        fun mediaItem(s: Song): MediaItem = MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setAlbumArtist(s.albumArtist).setIsPlayable(true).setIsBrowsable(false).build())
            .build()
    }
}

/** Pure queue-order helpers (unit tested). */
object QueueOrder {
    /**
     * Positions of the current queue items ([ids]) in the order they had before shuffling ([before]).
     * Songs added while shuffled (not in [before]) keep their relative order at the end. Duplicates are matched one by one.
     */
    fun restore(ids: List<String>, before: List<Long>): List<Int> {
        val pool = LinkedHashMap<String, ArrayDeque<Int>>()
        ids.forEachIndexed { i, id -> pool.getOrPut(id) { ArrayDeque() }.addLast(i) }
        val order = ArrayList<Int>(ids.size)
        val used = BooleanArray(ids.size)
        before.forEach { id -> pool[id.toString()]?.removeFirstOrNull()?.let { order += it; used[it] = true } }
        ids.indices.forEach { if (!used[it]) order += it }
        return order
    }
}
