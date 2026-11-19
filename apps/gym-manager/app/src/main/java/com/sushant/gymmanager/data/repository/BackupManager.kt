package com.sushant.gymmanager.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.sushant.gymmanager.data.local.AttendanceEntity
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.MembershipEntity
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PaymentEntity
import com.sushant.gymmanager.data.local.ExpenseEntity
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.util.Downloads
import com.sushant.gymmanager.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class GymBackup(
    val format: String = BackupManager.FORMAT,
    val version: Int = 2,
    val createdAt: Long,
    val gymName: String,
    /** Summary written before the data so the backup list can show it without reading the whole file. */
    val memberCount: Int = -1,
    val membershipCount: Int = -1,
    val attendanceCount: Int = -1,
    val cycleCount: Int = -1,
    val photoCount: Int = -1,
    val members: List<MemberEntity>,
    val memberPhotosBase64: Map<String, String> = emptyMap(),
    val memberships: List<MembershipEntity>,
    val paymentCycles: List<PaymentCycleEntity>,
    val attendance: List<AttendanceEntity>,
    val settings: GymSettingsEntity,
    val payments: List<PaymentEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList()
) {
    val counts: BackupCounts
        get() = BackupCounts(members.size, memberships.size, attendance.size, paymentCycles.size, memberPhotosBase64.size)
}

data class BackupCounts(val members: Int, val memberships: Int, val attendance: Int, val paymentCycles: Int, val photos: Int)

data class BackupFileInfo(
    val file: File,
    val createdAt: Long,
    val sizeKb: Long,
    val isSafety: Boolean,
    val counts: BackupCounts?
)

class BackupException(message: String) : Exception(message)

class BackupManager(private val context: Context, private val repository: GymRepository) {
    companion object {
        const val FORMAT = "GYM_BACKUP_V1"
        const val EXTENSION = ".gymbackup"
        private const val MAX_USER_BACKUPS = 10
        private const val MAX_SAFETY_BACKUPS = 5
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun backupsDir(): File =
        (context.getExternalFilesDir("Backups") ?: File(context.filesDir, "Backups")).apply { mkdirs() }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(Date())

    private suspend fun buildBackup(): GymBackup {
        val data = repository.snapshot()
        val photos = data.members.mapNotNull { m ->
            val path = m.photoUri ?: return@mapNotNull null
            val file = File(path)
            if (!file.exists()) return@mapNotNull null
            m.rollNumber to Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.toMap()
        return GymBackup(
            createdAt = System.currentTimeMillis(),
            gymName = data.settings.gymName,
            memberCount = data.members.size,
            membershipCount = data.memberships.size,
            attendanceCount = data.attendance.size,
            cycleCount = data.paymentCycles.size,
            photoCount = photos.size,
            members = data.members,
            memberPhotosBase64 = photos,
            memberships = data.memberships,
            paymentCycles = data.paymentCycles,
            attendance = data.attendance,
            settings = data.settings,
            payments = data.payments,
            expenses = data.expenses
        )
    }

    /** Writes a new backup file, verifies it by reading it back, then applies the retention policy. */
    suspend fun createBackup(safety: Boolean = false): File = withContext(Dispatchers.IO) {
        val backup = buildBackup()
        val name = (if (safety) "Safety_PreRestore_" else "GymBackup_") + stamp() + EXTENSION
        val file = File(backupsDir(), name)
        file.writeText(json.encodeToString(GymBackup.serializer(), backup))
        val check = runCatching { parse(file.readText()) }.getOrNull()
        if (check == null || check.members.size != backup.members.size) {
            file.delete()
            throw BackupException("Backup verification failed")
        }
        applyRetention()
        file
    }

    private fun applyRetention() {
        val files = backupsDir().listFiles { f -> f.name.endsWith(EXTENSION) }.orEmpty()
        val (safety, user) = files.partition { it.name.startsWith("Safety_") }
        user.sortedByDescending { it.lastModified() }.drop(MAX_USER_BACKUPS).forEach { it.delete() }
        safety.sortedByDescending { it.lastModified() }.drop(MAX_SAFETY_BACKUPS).forEach { it.delete() }
    }

    suspend fun listBackups(): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        backupsDir().listFiles { f -> f.name.endsWith(EXTENSION) }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val header = readHeader(f)
                BackupFileInfo(
                    file = f,
                    createdAt = header?.first ?: f.lastModified(),
                    sizeKb = (f.length() + 1023) / 1024,
                    isSafety = f.name.startsWith("Safety_"),
                    counts = header?.second
                )
            }
    }

    /** Reads only the start of a backup for its date and counts; older backups fall back to a full read. */
    private fun readHeader(f: File): Pair<Long, BackupCounts>? = runCatching {
        val head = f.bufferedReader().use { r ->
            val buf = CharArray(4096)
            val n = r.read(buf)
            if (n > 0) String(buf, 0, n) else ""
        }
        fun num(key: String): Long? = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(head)?.groupValues?.get(1)?.toLongOrNull()
        val members = num("memberCount")
        if (members != null && members >= 0) {
            (num("createdAt") ?: f.lastModified()) to BackupCounts(
                members.toInt(), (num("membershipCount") ?: 0).toInt(), (num("attendanceCount") ?: 0).toInt(),
                (num("cycleCount") ?: 0).toInt(), (num("photoCount") ?: 0).toInt()
            )
        } else {
            val parsed = parse(f.readText())
            parsed.createdAt to parsed.counts
        }
    }.getOrNull()

    fun parse(text: String): GymBackup {
        val backup = try {
            json.decodeFromString(GymBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("This file is not a valid Gym Manager backup")
        }
        if (backup.format != FORMAT) throw BackupException("Unsupported backup format: ${backup.format}")
        return upgrade(backup)
    }

    /** Version-1 backups have no payment ledger: rebuild it from cycles that were marked paid. */
    private fun upgrade(b: GymBackup): GymBackup {
        if (b.version >= 2) return b
        val fixedCycles = b.paymentCycles.map { c ->
            if (c.isPaid && c.paidAmount <= 0) c.copy(paidAmount = c.amount, paymentMode = PayMode.CASH) else c
        }
        val ledger = fixedCycles.filter { it.isPaid }.map { c ->
            PaymentEntity(
                cycleId = c.id, memberId = c.memberId, amount = c.amount, mode = PayMode.CASH,
                paidAt = c.paidDate ?: (c.cycleEnd + " 00:00:00"), receiptNo = "R${c.id}", createdAt = c.createdAt
            )
        }
        return b.copy(paymentCycles = fixedCycles, payments = ledger)
    }

    suspend fun readFromUri(uri: Uri): GymBackup = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw BackupException("Could not open the selected file")
        parse(text)
    }

    /** Safety snapshot first, then an all-or-nothing replace of every table, then photos. */
    suspend fun restore(backup: GymBackup) = withContext(Dispatchers.IO) {
        createBackup(safety = true)
        val previous = repository.snapshot().members.mapNotNull { it.photoUri }.toSet()
        val written = mutableListOf<String>()
        val members = backup.members.map { m ->
            val encoded = backup.memberPhotosBase64[m.rollNumber]
            if (encoded != null) {
                val file = PhotoStore.newFile(context, "restored")
                runCatching { file.writeBytes(Base64.decode(encoded, Base64.DEFAULT)) }
                    .onSuccess { written += file.absolutePath }
                    .map { m.copy(photoUri = file.absolutePath) }
                    .getOrElse { m.copy(photoUri = null) }
            } else m.copy(photoUri = null)
        }
        try {
            repository.replaceAll(
                BackupData(
                    backup.settings, members, backup.memberships, backup.paymentCycles, backup.attendance,
                    backup.payments, backup.expenses
                )
            )
        } catch (e: Exception) {
            written.forEach { PhotoStore.delete(it) }
            throw BackupException("Restore failed, your data was not changed")
        }
        (previous - written.toSet()).forEach { PhotoStore.delete(it) }
    }

    /** Copies a backup into the phone's Downloads/GymManager folder, which survives uninstalling the app. */
    suspend fun copyToDownloads(file: File): Boolean = withContext(Dispatchers.IO) {
        Downloads.save(context, file.name, "application/octet-stream") { out -> file.inputStream().use { it.copyTo(out) } } != null
    }

    /** Daily automatic backup (once per day) also copied to Downloads. Returns true when a backup was made. */
    suspend fun runAutoBackupIfDue(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val settings = repository.getSettings()
        if (!settings.autoBackupEnabled || prefs.getString("last", null) == today) return@withContext false
        val data = repository.snapshot()
        if (data.members.isEmpty()) return@withContext false
        val file = runCatching { createBackup() }.getOrNull() ?: return@withContext false
        val autoName = "AutoBackup_" + file.name.removePrefix("GymBackup_")
        Downloads.save(context, autoName, "application/octet-stream") { out -> file.inputStream().use { it.copyTo(out) } }
        Downloads.prune(context, "AutoBackup_", keep = 7)
        prefs.edit().putString("last", today).apply()
        true
    }

    fun lastAutoBackup(): String? =
        context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE).getString("last", null)

    fun delete(file: File) {
        file.delete()
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share backup").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
