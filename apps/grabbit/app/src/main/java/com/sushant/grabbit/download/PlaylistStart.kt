package com.sushant.grabbit.download

import android.content.Context
import com.sushant.grabbit.data.PlaylistPick
import com.sushant.grabbit.data.PlaylistQueue
import com.sushant.grabbit.engine.MediaInfo

/** Turns the picked playlist videos into one download each ("04 - Title"), started 2 at a time. */
object PlaylistStart {
    /** [info].entries = the chosen videos in the chosen order; [opts].sub = playlist folder ("" = none). */
    fun start(ctx: Context, info: MediaInfo, quality: String, opts: DlOpts): String {
        val total = maxOf(info.count, info.entries.maxOfOrNull { it.index } ?: 0)
        val title = opts.gt.ifBlank { info.title }
        val jobs = info.entries.map { e ->
            Downloads.Job(
                url = e.url, title = e.title, thumb = e.thumb, site = info.site, quality = quality, playlist = false,
                opts = opts.copy(
                    name = FileNames.clean(PlaylistPick.fileName(e.index, total, e.title)),
                    plId = info.playlistId, gt = title, gi = e.index, vid = e.id,
                    // Trim, chapters and a per-video name don't apply to a whole playlist
                    trimStart = -1, trimEnd = -1, splitChapters = false,
                ),
            )
        }
        if (com.sushant.grabbit.data.Prefs.keepLastFolder) com.sushant.grabbit.data.Folders.select(opts.folder)
        return PlaylistQueue.start(ctx, title, info.thumbnail, info.site, jobs)
    }
}
