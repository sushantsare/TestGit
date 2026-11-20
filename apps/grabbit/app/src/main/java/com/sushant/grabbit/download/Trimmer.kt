package com.sushant.grabbit.download

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cuts a finished download to [start]..[end] seconds with the FFmpeg bundled in the app (stream copy:
 * fast, no quality loss; video starts at the nearest keyframe). Runs FFmpeg the same way the download
 * engine does, with its libraries on LD_LIBRARY_PATH.
 */
object Trimmer {
    fun trim(ctx: Context, file: File, start: Long, end: Long): String? {
        val ff = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ff.exists()) return "FFmpeg not found"
        val packages = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
        val out = File(file.parentFile, "${file.nameWithoutExtension}.trim.${file.extension}")
        val cmd = listOf(
            ff.absolutePath, "-y", "-hide_banner", "-loglevel", "error",
            "-ss", start.toString(), "-i", file.absolutePath, "-t", (end - start).toString(),
            "-map", "0", "-c", "copy", "-avoid_negative_ts", "make_zero",
            out.absolutePath,
        )
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = listOf(
            File(packages, "python/usr/lib"), File(packages, "ffmpeg/usr/lib"),
        ).joinToString(":") { it.absolutePath } + (System.getenv("LD_LIBRARY_PATH")?.let { ":$it" } ?: "")
        val p = pb.start()
        val log = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(10, TimeUnit.MINUTES)) { p.destroy(); return "Trimming took too long" }
        if (p.exitValue() != 0 || !out.exists() || out.length() == 0L) {
            out.delete()
            return log.takeLast(1500).ifBlank { "FFmpeg exit ${p.exitValue()}" }
        }
        file.delete()
        out.renameTo(file)
        return null
    }
}
