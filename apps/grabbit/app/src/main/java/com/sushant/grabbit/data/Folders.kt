package com.sushant.grabbit.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow

/** A folder the user picked with Android's folder picker. */
data class Folder(val uri: String, val name: String, val path: String)

/** Default + up to 3 remembered folders shown as chips; the last one used can stick. */
object Folders {
    const val MAX = 3
    val recent = MutableStateFlow<List<Folder>>(emptyList())
    /** Test hook: where the folder picker opens. */
    @Volatile var pickerStart: android.net.Uri? = null

    /** "" = Default. */
    val current = MutableStateFlow("")

    fun init(ctx: Context) {
        // Drop folders whose permission is gone (deleted folder, permission revoked)
        val ok = Prefs.recentFolders.filter { valid(ctx, it) }
        if (ok != Prefs.recentFolders) Prefs.recentFolders = ok
        recent.value = ok.map { describe(ctx, it) }
        current.value = if (Prefs.keepLastFolder && Prefs.saveFolder in ok) Prefs.saveFolder else ""
    }

    fun valid(ctx: Context, uri: String): Boolean = runCatching {
        val u = Uri.parse(uri)
        ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission } &&
            DocumentFile.fromTreeUri(ctx, u)?.let { it.exists() && it.isDirectory } == true
    }.getOrDefault(false)

    /** "Lectures" and "Internal storage › Download › Lectures". */
    fun describe(ctx: Context, uri: String): Folder {
        val u = Uri.parse(uri)
        val docId = runCatching { DocumentsContract.getTreeDocumentId(u) }.getOrDefault("")
        val path = readablePath(docId)
        val name = runCatching { DocumentFile.fromTreeUri(ctx, u)?.name }.getOrNull()
            ?: path.substringAfterLast(" › ")
        return Folder(uri, name, path)
    }

    /** "primary:Download/Lectures" -> "Internal storage › Download › Lectures". */
    fun readablePath(docId: String): String {
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val root = if (vol.equals("primary", true)) "Internal storage" else "SD card"
        return (listOf(root) + rel.split('/').filter { it.isNotBlank() }).joinToString(" › ")
    }

    /** After the folder picker: keep permission, put it first, forget the oldest beyond 3. */
    fun add(ctx: Context, uri: Uri): Folder {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
        val s = uri.toString()
        val list = (listOf(s) + Prefs.recentFolders.filterNot { it == s })
        list.drop(MAX).forEach { release(ctx, it) }
        Prefs.recentFolders = list.take(MAX)
        recent.value = Prefs.recentFolders.map { describe(ctx, it) }
        select(s)
        return describe(ctx, s)
    }

    fun select(uri: String) {
        current.value = uri
        Prefs.saveFolder = uri
    }

    fun forget(ctx: Context, uri: String) {
        Prefs.recentFolders = Prefs.recentFolders.filterNot { it == uri }
        recent.value = Prefs.recentFolders.map { describe(ctx, it) }
        if (current.value == uri) select("")
        release(ctx, uri)
    }

    private fun release(ctx: Context, uri: String) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(uri), flags) }
    }

    /** Name shown in "Saved to …" messages. */
    fun label(ctx: Context, uri: String, audio: Boolean, image: Boolean = false): String =
        if (uri.isBlank()) (if (image) "Pictures/Grabbit" else if (audio) "Music/Grabbit" else "Movies/Grabbit")
        else describe(ctx, uri).name
}
