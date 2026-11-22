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
import com.sushant.tarang.data.Song
import kotlinx.coroutines.*
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

/** The screens' handle on the playback service (a Media3 MediaController) plus every playback action. */
@UnstableApi
class PlayerConnection(private val ctx: Context, private val scope: CoroutineScope) {
    private var controller: MediaController? = null
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()
    private var ticker: Job? = null

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
            shuffle = c.shuffleModeEnabled, repeat = c.repeatMode, speed = c.playbackParameters.speed,
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

    /** "Play all" / "Play from here": replaces the queue with this list. */
    fun playList(list: List<Song>, start: Int = 0, shuffle: Boolean = false, kind: String = "Folder", label: String = "") {
        val c = controller ?: return
        if (list.isEmpty()) return
        setContext(kind, label)
        c.shuffleModeEnabled = shuffle
        val first = if (shuffle) list.indices.random() else start.coerceIn(0, list.lastIndex)
        c.setMediaItems(items(list), first, 0)
        c.prepare(); c.play()
    }

    /**
     * Tapping a song (his choice): it is slipped in after the current song and plays now; the old list continues after it.
     * If nothing is lined up, the rest of the list plays from the tapped song.
     */
    fun tapSong(song: Song, context: List<Song>, kind: String, label: String) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playList(context, context.indexOfFirst { it.id == song.id }.coerceAtLeast(0), false, kind, label); return }
        val cur = c.currentMediaItemIndex
        if (c.currentMediaItem?.mediaId == song.id.toString()) { c.play(); return }
        val at = (cur + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(at, PlaybackService.mediaItem(song))
        c.seekTo(at, 0); c.prepare(); c.play()
    }

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playList(songs); return }
        c.addMediaItems((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), items(songs))
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
    fun seekTo(ms: Long) { controller?.seekTo(ms); _ui.value = _ui.value.copy(positionMs = ms) }
    fun playAt(index: Int) { controller?.let { it.seekTo(index, 0); it.play() } }
    fun removeAt(index: Int) { controller?.removeMediaItem(index) }
    fun move(from: Int, to: Int) { controller?.moveMediaItem(from, to) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun setSpeed(speed: Float, keepPitch: Boolean) { controller?.playbackParameters = PlaybackParameters(speed, if (keepPitch) 1f else speed) }

    private fun cmd(name: String, b: Bundle = Bundle.EMPTY) = controller?.sendCustomCommand(SessionCommand(name, Bundle.EMPTY), b)
    fun switchQueue(slot: Int) = cmd(Cmd.SWITCH_QUEUE, Bundle().apply { putInt("slot", slot) })
    fun clearQueue(slot: Int) = cmd(Cmd.CLEAR_QUEUE, Bundle().apply { putInt("slot", slot) })
    fun endQueue() = cmd(Cmd.END_QUEUE)
    fun sleep(minutes: Int, afterSong: Boolean, fade: Boolean) =
        cmd(Cmd.SLEEP, Bundle().apply { putInt("minutes", minutes); putBoolean("afterSong", afterSong); putBoolean("fade", fade) })

    /** Remove a deleted song from the queue. */
    fun dropSong(id: Long) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).mediaId == id.toString()) c.removeMediaItem(i)
    }

    @Suppress("unused") private fun MediaItem.idLong() = mediaId.toLongOrNull()
}
