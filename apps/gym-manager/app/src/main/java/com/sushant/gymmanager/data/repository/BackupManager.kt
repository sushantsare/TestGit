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
    val version: Int = 1,
    val createdAt: Long,
    val gymName: String,
    val members: List<MemberEntity>,
    val memberPhotosBase64: Map<String, String> = emptyMap(),
    val memberships: List<MembershipEntity>,
    val paymentCycles: List<PaymentCycleEntity>,
    val attendance: List<AttendanceEntity>,
    val settings: GymSettingsEntity
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
            members = data.members,
            memberPhotosBase64 = photos,
            memberships = data.memberships,
            paymentCycles = data.paymentCycles,
            attendance = data.attendance,
            settings = data.settings
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
                val parsed = runCatching { parse(f.readText()) }.getOrNull()
                BackupFileInfo(
                    file = f,
                    createdAt = parsed?.createdAt ?: f.lastModified(),
                    sizeKb = (f.length() + 1023) / 1024,
                    isSafety = f.name.startsWith("Safety_"),
                    counts = parsed?.counts
                )
            }
    }

    fun parse(text: String): GymBackup {
        val backup = try {
            json.decodeFromString(GymBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("This file is not a valid Gym Manager backup")
        }
        if (backup.format != FORMAT) throw BackupException("Unsupported backup format: ${backup.format}")
        return backup
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
                BackupData(backup.settings, members, backup.memberships, backup.paymentCycles, backup.attendance)
            )
        } catch (e: Exception) {
            written.forEach { PhotoStore.delete(it) }
            throw BackupException("Restore failed, your data was not changed")
        }
        (previous - written.toSet()).forEach { PhotoStore.delete(it) }
    }

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
