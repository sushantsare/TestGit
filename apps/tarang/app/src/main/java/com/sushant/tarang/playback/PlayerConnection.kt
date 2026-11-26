package com.sushant.tarang.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.sushant.tarang.TarangApp
import com.sushant.tarang.data.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUi(
    val connected: Boolean = false,
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queueIds: List<Long> = emptyList(),
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val nextIndex: Int = -1,
) {
    val hasSong get() = currentId != null
    val progress get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** The queue as it was just before a tap replaced it (for "Queue replaced · Undo"). */
data class QueueSnapshot(
    val ids: List<Long>, val index: Int, val positionMs: Long, val playing: Boolean,
    val kind: String, val label: String, val shuffle: Boolean, val orderBeforeShuffle: List<Long>,
)

/** The screens' handle on the playback service (a Media3 MediaController) plus every playback action. */
@UnstableApi
class PlayerConnection(private val ctx: Context, private val scope: CoroutineScope) {
    private val app get() = ctx.applicationContext as TarangApp
    private var controller: MediaController? = null
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()
    private var ticker: Job? = null
    /** Emits when tapping a song replaced the queue, so the screen can offer Undo. */
    val replaced = MutableSharedFlow<QueueSnapshot>(extraBufferCapacity = 2)

    fun connect() {
        if (controller != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener { override fun onEvents(player: Player, events: Player.Events) = refresh() })
            refresh()
            ticker = scope.launch { while (isActive) { delay(250); if (c.isPlaying) refreshPosition() } }
            scope.launch { app.hub.shuffle.collect { refresh() } }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() { ticker?.cancel(); controller?.release(); controller = null }

    private fun refresh() {
        val c = controller ?: return
        val dur = c.duration.takeIf { it > 0 } ?: 0
        val ids = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        _ui.value = PlayerUi(
            connected = true,
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            positionMs = c.currentPosition.coerceAtLeast(0).let { if (dur > 0) it.coerceAtMost(dur) else it },
            durationMs = dur,
            queueIds = ids, index = c.currentMediaItemIndex,
            nextIndex = c.nextMediaItemIndex,
            shuffle = app.hub.shuffle.value, repeat = c.repeatMode, speed = c.playbackParameters.speed,
        )
    }

    private fun refreshPosition() {
        val c = controller ?: return
        val dur = c.duration.takeIf { it > 0 } ?: _ui.value.durationMs
        _ui.value = _ui.value.copy(positionMs = c.currentPosition.coerceAtLeast(0).let { if (dur > 0) it.coerceAtMost(dur) else it }, durationMs = dur)
    }

    private fun items(list: List<Song>) = list.map { PlaybackService.mediaItem(it) }

    private fun setContext(kind: String, label: String) =
        controller?.sendCustomCommand(SessionCommand(Cmd.CONTEXT, Bundle.EMPTY), Bundle().apply { putString("kind", kind); putString("label", label) })

    private fun shuffleCmd(mode: String) = cmd(Cmd.SET_SHUFFLE, Bundle().apply { putString("mode", mode) })

    /**
     * "Play all" / "Shuffle" / tapping a song: the list becomes the queue, starting at [start].
     * [shuffle] = the Shuffle button. Otherwise shuffle stays on only when "Keep shuffle on" (persistent shuffle) is set.
     */
    fun playList(list: List<Song>, start: Int = 0, shuffle: Boolean = false, kind: String = "Folder", label: String = "") {
        val c = controller ?: return
        if (list.isEmpty()) return
        setContext(kind, label)
        val keepShuffle = app.hub.shuffle.value && app.prefs.now.persistentShuffle
        val first = if (shuffle) list.indices.random() else start.coerceIn(0, list.lastIndex)
        c.setMediaItems(items(list), first, 0)
        c.prepare(); c.play()
        shuffleCmd(if (shuffle || keepShuffle) "apply" else "off")
    }

    /**
     * Tapping a song in a list plays that list from the tapped song (the queue is replaced; the screen offers Undo).
     * Tapping a song of the list that is already playing just jumps to it.
     */
    fun tapSong(song: Song, context: List<Song>, kind: String, label: String) {
        val c = controller ?: return
        val idx = context.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        if (c.currentMediaItem?.mediaId == song.id.toString()) { c.play(); return }
        val hub = app.hub
        val queueIds = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        val sameList = queueIds.isNotEmpty() && kind == hub.contextKind.value && label == hub.contextLabel.value &&
            queueIds.toSet() == context.map { it.id }.toSet()
        if (sameList) {
            val at = queueIds.indexOf(song.id)
            if (at >= 0) { c.seekTo(at, 0); c.play(); return }
        }
        if (queueIds.isNotEmpty()) replaced.tryEmit(QueueSnapshot(queueIds, c.currentMediaItemIndex.coerceAtLeast(0), c.currentPosition.coerceAtLeast(0),
            c.isPlaying, hub.contextKind.value, hub.contextLabel.value, hub.shuffle.value, app.prefs.orderBeforeShuffle))
        playList(context, idx, false, kind, label)
    }

    /** Brings back the queue a tap replaced. */
    fun undoReplace(s: QueueSnapshot, lookup: (Long) -> Song?) {
        val c = controller ?: return
        val songs = s.ids.mapNotNull(lookup)
        if (songs.isEmpty()) return
        setContext(s.kind, s.label)
        c.setMediaItems(items(songs), s.index.coerceIn(0, songs.lastIndex), s.positionMs)
        c.prepare(); if (s.playing) c.play()
        app.hub.shuffle.value = s.shuffle; app.prefs.shuffleOn = s.shuffle; app.prefs.orderBeforeShuffle = s.orderBeforeShuffle
        refresh()
    }

    /** Plays right after the current song. A song already in the queue is moved there instead of added twice. */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playList(songs); return }
        var k = 0   // how many songs were already placed after the current one
        songs.forEach { s ->
            val cur = c.currentMediaItemIndex
            if (c.currentMediaItem?.mediaId == s.id.toString()) return@forEach
            val existing = (0 until c.mediaItemCount).firstOrNull { it != cur && c.getMediaItemAt(it).mediaId == s.id.toString() }
            if (existing != null) {
                // final place: right after the current song (+ songs placed before this one)
                val target = (if (existing < cur) cur + k else cur + 1 + k).coerceAtMost(c.mediaItemCount - 1)
                if (existing != target) c.moveMediaItem(existing, target)
            } else c.addMediaItem((cur + 1 + k).coerceAtMost(c.mediaItemCount), PlaybackService.mediaItem(s))
            k++
        }
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { c.setMediaItems(items(songs)); c.prepare(); return }
        c.addMediaItems(items(songs))
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) { c.pause(); return }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)   // queue finished: play it again from the top
        c.play()
    }
    fun play() { controller?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    /** Swipe gestures always change the song (never restart the current one). Returns false when there is no song that way. */
    fun swipeNext(): Boolean { val c = controller ?: return false; if (!c.hasNextMediaItem()) return false; c.seekToNextMediaItem(); return true }
    fun swipePrevious(): Boolean { val c = controller ?: return false; if (!c.hasPreviousMediaItem()) return false; c.seekToPreviousMediaItem(); return true }
    fun seekTo(ms: Long) { controller?.seekTo(ms); _ui.value = _ui.value.copy(positionMs = ms) }
    fun playAt(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.play() } } }
    fun removeAt(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }
    fun move(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    /** Shuffle on = the queue really gets reordered (current song stays); off = the earlier order comes back. */
    fun toggleShuffle() { shuffleCmd("toggle") }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun setSpeed(speed: Float, keepPitch: Boolean) { controller?.playbackParameters = PlaybackParameters(speed, if (keepPitch) 1f else speed) }

    private fun cmd(name: String, b: Bundle = Bundle.EMPTY) = controller?.sendCustomCommand(SessionCommand(name, Bundle.EMPTY), b)
    /** Removes every song from the queue except the one playing now. */
    fun clearQueue() = cmd(Cmd.CLEAR_QUEUE)
    fun endQueue() = cmd(Cmd.END_QUEUE)
    fun sleep(minutes: Int, afterSong: Boolean, fade: Boolean) =
        cmd(Cmd.SLEEP, Bundle().apply { putInt("minutes", minutes); putBoolean("afterSong", afterSong); putBoolean("fade", fade) })

    /** Remove deleted songs from the queue. */
    fun dropSong(id: Long) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).mediaId == id.toString()) c.removeMediaItem(i)
    }

    @Suppress("unused") private fun MediaItem.idLong() = mediaId.toLongOrNull()
}
