package com.sushant.inknote.io

import android.content.Context
import android.net.Uri
import com.sushant.inknote.data.Crypto
import com.sushant.inknote.data.FolderEntity
import com.sushant.inknote.data.NoteEntity
import com.sushant.inknote.data.NoteFiles
import com.sushant.inknote.data.PageEntity
import com.sushant.inknote.data.RecordingEntity
import com.sushant.inknote.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreResult(val notes: Int, val skippedLocked: Int)

/**
 * One-file backup (.inkbackup / .inknote): manifest.json + every note's files.
 * Locked notes are re-encrypted with a key made from the user's PIN, so they can be restored on a new phone.
 */
class Backup(private val context: Context, private val repo: Repository) {

    suspend fun lockedCount(ids: List<Long>? = null): Int = (ids?.mapNotNull { repo.note(it) } ?: repo.db.notes().all()).count { it.locked && it.deletedAt == null }

    suspend fun writeAll(out: File, pinForLocked: String?): Int {
        val ids = repo.db.notes().all().filter { it.deletedAt == null }.map { it.id }
        writeNotes(out, ids, pinForLocked)
        return ids.size
    }

    suspend fun writeNotes(out: File, ids: List<Long>, pinForLocked: String?) = withContext(Dispatchers.IO) {
        val salt = Crypto.newSalt()
        val pinKey = pinForLocked?.let { Crypto.pinKey(it, salt) }
        val m = JSONObject().put("app", "InkNote").put("version", 1).put("createdAt", System.currentTimeMillis())
            .put("salt", Crypto.hex(salt))
        repo.files.clearCaches()
        val notesArr = JSONArray(); val pagesArr = JSONArray(); val recArr = JSONArray(); val tagArr = JSONArray()
        val folderIds = HashSet<Long>()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (id in ids) {
                val n = repo.note(id) ?: continue
                if (n.locked && pinKey == null) continue
                notesArr.put(noteJson(n)); n.folderId?.let { folderIds += it }
                repo.pages(id).forEach { pagesArr.put(pageJson(it)) }
                repo.recordings(id).forEach { recArr.put(recJson(it)) }
                repo.tagsFor(id).forEach { tagArr.put(JSONObject().put("noteId", id).put("name", it)) }
                val dir = repo.files.noteDir(id)
                dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                    val rel = "files/$id/" + f.relativeTo(dir).path.replace('\\', '/')
                    zip.putNextEntry(ZipEntry(rel))
                    var bytes = f.readBytes()
                    if (Crypto.isEncrypted(bytes) && pinKey != null) bytes = Crypto.encrypt(Crypto.decrypt(bytes), pinKey)
                    zip.write(bytes); zip.closeEntry()
                }
            }
            val folders = JSONArray(); repo.db.folders().all().filter { it.id in folderIds || ids.size > 1 }.forEach { folders.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("sort", it.sort)) }
            m.put("notes", notesArr).put("pages", pagesArr).put("recordings", recArr).put("tags", tagArr).put("folders", folders)
            if (pinForLocked != null) app().securePrefs.let { sp -> m.put("pinSalt", sp.pinSaltHex()).put("pinHash", sp.pinHashHex()) }
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(m.toString().toByteArray()); zip.closeEntry()
        }
    }

    private fun app() = context.applicationContext as com.sushant.inknote.InkApp

    /** Reads only the manifest to tell whether a backup holds locked notes (then the PIN is needed). */
    suspend fun inspect(uri: Uri): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { readManifest(it) } }.getOrNull()
    }

    private fun readManifest(i: InputStream): JSONObject? {
        ZipInputStream(i.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) { if (e.name == "manifest.json") return JSONObject(z.readBytes().toString(Charsets.UTF_8)); e = z.nextEntry }
        }
        return null
    }

    fun hasLocked(m: JSONObject): Boolean { val a = m.optJSONArray("notes") ?: return false; return (0 until a.length()).any { a.getJSONObject(it).optBoolean("locked") } }

    /** Adds every note from the backup as new notes (nothing on this phone is overwritten). */
    suspend fun restore(uri: Uri, pin: String?): RestoreResult = withContext(Dispatchers.IO) {
        val m = inspect(uri) ?: error("This isn't an InkNote backup file")
        if (m.optString("app") != "InkNote") error("This isn't an InkNote backup file")
        val pinKey = pin?.let { Crypto.pinKey(it, Crypto.unhex(m.getString("salt"))) }
        val pinOk = pin != null && m.optString("pinHash").isNotEmpty() &&
            Crypto.hex(Crypto.pbkdf2(pin, Crypto.unhex(m.getString("pinSalt")))) == m.getString("pinHash")
        val folderMap = HashMap<Long, Long>()
        val existing = repo.db.folders().all()
        val fa = m.optJSONArray("folders") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val f = fa.getJSONObject(i)
            val match = existing.firstOrNull { it.name.equals(f.getString("name"), true) }
            folderMap[f.getLong("id")] = match?.id ?: repo.db.folders().insert(FolderEntity(name = f.getString("name"), color = f.getInt("color"), sort = f.optInt("sort")))
        }
        val noteMap = HashMap<Long, Long>(); val pageMap = HashMap<Long, Long>(); var skipped = 0
        val na = m.getJSONArray("notes")
        for (i in 0 until na.length()) {
            val j = na.getJSONObject(i)
            if (j.optBoolean("locked") && !pinOk) { skipped++; continue }
            val old = j.getLong("id")
            noteMap[old] = repo.db.notes().insert(NoteEntity(
                title = j.getString("title"), folderId = if (j.isNull("folderId")) null else folderMap[j.getLong("folderId")],
                createdAt = j.getLong("createdAt"), updatedAt = j.getLong("updatedAt"), pinned = j.optBoolean("pinned"),
                favourite = j.optBoolean("favourite"), locked = j.optBoolean("locked"), kind = j.getString("kind"),
                pageType = j.getString("pageType"), defaultOrientation = j.getString("defaultOrientation"), defaultPaper = j.getString("defaultPaper"),
                pageCount = j.optInt("pageCount", 1), hasRecording = j.optBoolean("hasRecording"), firstLine = j.optString("firstLine"),
            ))
        }
        val pa = m.getJSONArray("pages")
        for (i in 0 until pa.length()) {
            val j = pa.getJSONObject(i); val nid = noteMap[j.getLong("noteId")] ?: continue
            pageMap[j.getLong("id")] = repo.db.pages().insert(PageEntity(noteId = nid, idx = j.getInt("idx"), orientation = j.getString("orientation"),
                paper = j.getString("paper"), width = j.getDouble("width").toFloat(), height = j.getDouble("height").toFloat(),
                pdfFile = if (j.isNull("pdfFile")) null else j.getString("pdfFile"), pdfPage = j.optInt("pdfPage", -1)))
        }
        val ra = m.optJSONArray("recordings") ?: JSONArray()
        for (i in 0 until ra.length()) {
            val j = ra.getJSONObject(i); val nid = noteMap[j.getLong("noteId")] ?: continue
            repo.db.recordings().insert(RecordingEntity(noteId = nid, file = j.getString("file"), name = j.getString("name"),
                durationMs = j.getLong("durationMs"), createdAt = j.getLong("createdAt"), kind = j.getString("kind")))
        }
        val ta = m.optJSONArray("tags") ?: JSONArray()
        val tagsByNote = HashMap<Long, MutableList<String>>()
        for (i in 0 until ta.length()) { val j = ta.getJSONObject(i); noteMap[j.getLong("noteId")]?.let { tagsByNote.getOrPut(it) { mutableListOf() } += j.getString("name") } }
        tagsByNote.forEach { (n, t) -> repo.setTags(n, t) }
        // files
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val parts = e.name.split('/')
                    if (!e.isDirectory && parts.size >= 3 && parts[0] == "files") {
                        val nid = noteMap[parts[1].toLongOrNull() ?: -1]
                        if (nid != null && parts.none { it == ".." }) {
                            var rel = parts.drop(2).joinToString("/")
                            if (parts[2] == "pages") rel = "pages/" + (pageMap[parts[3].removeSuffix(".ink").toLongOrNull() ?: -1] ?: -1) + ".ink"
                            var bytes = z.readBytes()
                            if (Crypto.isEncrypted(bytes) && pinKey != null) bytes = Crypto.encrypt(Crypto.decrypt(bytes, pinKey))
                            NoteFiles.atomicWrite(File(repo.files.noteDir(nid), rel), bytes)
                        }
                    }
                    e = z.nextEntry
                }
            }
        }
        if (pinOk && !app().securePrefs.hasPin.value) app().securePrefs.setPinHash(m.getString("pinSalt"), m.getString("pinHash"))
        repo.files.clearCaches()
        RestoreResult(noteMap.size, skipped)
    }

    private fun noteJson(n: NoteEntity) = JSONObject().put("id", n.id).put("title", n.title).put("folderId", n.folderId ?: JSONObject.NULL)
        .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt).put("pinned", n.pinned).put("favourite", n.favourite)
        .put("locked", n.locked).put("kind", n.kind).put("pageType", n.pageType).put("defaultOrientation", n.defaultOrientation)
        .put("defaultPaper", n.defaultPaper).put("pageCount", n.pageCount).put("hasRecording", n.hasRecording).put("firstLine", n.firstLine)

    private fun pageJson(p: PageEntity) = JSONObject().put("id", p.id).put("noteId", p.noteId).put("idx", p.idx).put("orientation", p.orientation)
        .put("paper", p.paper).put("width", p.width.toDouble()).put("height", p.height.toDouble()).put("pdfFile", p.pdfFile ?: JSONObject.NULL).put("pdfPage", p.pdfPage)

    private fun recJson(r: RecordingEntity) = JSONObject().put("noteId", r.noteId).put("file", r.file).put("name", r.name)
        .put("durationMs", r.durationMs).put("createdAt", r.createdAt).put("kind", r.kind)
}
