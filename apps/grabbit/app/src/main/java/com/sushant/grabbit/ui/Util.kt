package com.sushant.grabbit.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
}

fun fmtDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun fmtEta(sec: Long): String = if (sec <= 0) "" else fmtDuration(sec) + " left"

/** Compact one-line date: "4:42 AM" today, "19 Nov" this year, "19 Nov 2025" otherwise. */
fun fmtDate(ms: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
    val a = Calendar.getInstance().apply { timeInMillis = ms }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    val pattern = when {
        sameYear && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) -> "h:mm a"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).format(Date(ms))
}

fun qualityLabel(res: Int): String = when {
    res >= 4320 -> "8K"
    res >= 2160 -> "4K"
    res >= 1440 -> "2K"
    else -> "${res}p"
}

/** App messages are shown as snackbars by the root screen (no Toasts). */
object UiMessages {
    val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun say(msg: String) { flow.tryEmit(msg) }
}

@Suppress("UnusedReceiverParameter")
fun Context.toast(msg: String) = UiMessages.say(msg)

fun Context.openUri(uri: String, mime: String) {
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(uri), mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { startActivity(i) } catch (e: ActivityNotFoundException) { toast("No app found to open this file") }
}

fun Context.shareUri(uri: String, mime: String) {
    val i = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(i, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) { toast("No app found to open this link") }
}
