package com.sushant.grabbit.download

import android.content.Context
import com.sushant.grabbit.engine.PlaylistEntry
import java.io.File

/** Playlist videos already downloaded or queued. They are skipped the next time. */
object Archive {
    private fun file(ctx: Context) = File(ctx.filesDir, "archive.txt")

    fun keyOf(e: PlaylistEntry): String = if (e.id.isNotBlank()) "id:${e.id}" else "url:${e.url}"

    @Synchronized
    fun known(ctx: Context): Set<String> = runCatching { file(ctx).readLines().toHashSet() }.getOrDefault(emptySet())

    @Synchronized
    fun add(ctx: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        runCatching { file(ctx).appendText(keys.joinToString(separator = "") { "$it\n" }) }
    }
}
