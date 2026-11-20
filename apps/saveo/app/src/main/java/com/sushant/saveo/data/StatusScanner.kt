package com.sushant.saveo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.io.File

/** Result of one scan of every connected WhatsApp status folder. */
data class ScanResult(
    val items: List<StatusItem>,
    /** Sources that are set up and readable. */
    val connected: Set<Source>,
    /** Sources that were set up but can no longer be read (permission lost / folder gone). */
    val lost: Set<Source>,
)

/**
 * Reads WhatsApp's status folders.
 * - Android 11+: through the folder the user allowed once with the system folder picker (SAF tree).
 * - Android 8–10: directly from storage with READ_EXTERNAL_STORAGE.
 */
class StatusScanner(private val ctx: Context, private val prefs: Prefs) {

    fun usesFolderPicker(): Boolean = Build.VERSION.SDK_INT >= 30

    fun hasLegacyPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun scan(): ScanResult {
        val test = prefs.testDir
        if (test != null) return scanTestDir(File(test))
        val items = ArrayList<StatusItem>()
        val connected = HashSet<Source>()
        val lost = HashSet<Source>()
        val folders = prefs.folders.value
        for (source in Source.entries) {
            val ref = folders[source]
            if (usesFolderPicker()) {
                if (ref == null) continue
                val r = runCatching { scanTree(source, ref) }.getOrNull()
                if (r == null) lost += source else { connected += source; items += r }
            } else {
                if (source == Source.DUAL && ref != null) {
                    val r = runCatching { scanTree(source, ref) }.getOrNull()
                    if (r == null) lost += source else { connected += source; items += r }
                    continue
                }
                if (!hasLegacyPermission()) { if (ref != null) lost += source; continue }
                val root = Environment.getExternalStorageDirectory()
                val dirs = source.relativePaths.map { File(root, it) }.filter { it.isDirectory }
                if (dirs.isEmpty()) continue
                connected += source
                dirs.forEach { items += scanFileDir(source, it) }
            }
        }
        return ScanResult(items.distinctBy { it.key }.sortedByDescending { it.modified }, connected, lost)
    }

    /** Which sources have a status folder on this phone (Android 8–10 only). */
    fun legacyAvailable(source: Source): Boolean {
        val root = Environment.getExternalStorageDirectory()
        return source.relativePaths.any { File(root, it).isDirectory }
    }

    private fun scanTestDir(dir: File): ScanResult {
        val items = ArrayList<StatusItem>(); val connected = HashSet<Source>()
        val lost = HashSet<Source>()
        Source.entries.forEach { s ->
            val d = File(dir, s.name)
            if (File(dir, "LOST_${s.name}").exists()) lost += s
            else if (d.isDirectory) { connected += s; items += scanFileDir(s, d) }
        }
        return ScanResult(items.sortedByDescending { it.modified }, connected, lost)
    }

    private fun scanFileDir(source: Source, dir: File): List<StatusItem> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0 && MediaTypes.isStatusFile(it.name, null) }.map {
            StatusItem(Uri.fromFile(it), it.name, MediaTypes.kindOf(it.name, null)!!, source, it.lastModified(), it.length())
        }

    /** Returns null if the folder can no longer be read. */
    private fun scanTree(source: Source, ref: String): List<StatusItem>? {
        val (tree, docId) = parseRef(ref)
        val granted = ctx.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!granted) return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val out = ArrayList<StatusItem>()
        val c = ctx.contentResolver.query(children, proj, null, null, null) ?: return null
        c.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val mime = it.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val size = if (it.isNull(4)) 0L else it.getLong(4)
                if (size <= 0 || !MediaTypes.isStatusFile(name, mime)) continue
                val kind = MediaTypes.kindOf(name, mime) ?: continue
                out += StatusItem(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, kind, source, if (it.isNull(3)) 0L else it.getLong(3), size)
            }
        }
        return out
    }

    /**
     * Checks a folder the user picked. Accepts the .Statuses folder itself, or a parent
     * (Media / WhatsApp / com.whatsapp) that contains it. Returns the stored reference or null.
     */
    fun resolvePicked(tree: Uri): String? {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (rootId.trimEnd('/').endsWith(".Statuses")) return tree.toString()
        // Walk down at most 4 levels looking for ".Statuses"
        var frontier = listOf(rootId)
        repeat(4) {
            val next = ArrayList<String>()
            for (parent in frontier) {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
                val c = runCatching {
                    ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
                }.getOrNull() ?: continue
                c.use {
                    while (it.moveToNext()) {
                        val id = it.getString(0) ?: continue
                        val name = it.getString(1) ?: ""
                        if (it.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) continue
                        if (name == ".Statuses") return "$tree\n$id"
                        if (name in WALK_NAMES) next += id
                    }
                }
            }
            frontier = next
            if (frontier.isEmpty()) return null
        }
        return null
    }

    companion object {
        private val WALK_NAMES = setOf("Media", "WhatsApp", "WhatsApp Business", "com.whatsapp", "com.whatsapp.w4b", "media", "Android")

        fun parseRef(ref: String): Pair<Uri, String> {
            val parts = ref.split('\n')
            val tree = Uri.parse(parts[0])
            val docId = parts.getOrNull(1) ?: DocumentsContract.getTreeDocumentId(tree)
            return tree to docId
        }

        /** Where the folder picker should open, so the user only has to tap "Use this folder". */
        fun initialUri(source: Source): Uri? {
            val path = source.relativePaths.firstOrNull() ?: return null
            return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$path")
        }
    }
}
