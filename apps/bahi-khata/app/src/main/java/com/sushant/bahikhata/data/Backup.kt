package com.sushant.bahikhata.data

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import com.sushant.bahikhata.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val accounts: List<Account>, val categories: List<Category>, val entries: List<Entry>, val attachments: List<Attachment>,
    val merchants: List<Merchant>, val recurring: List<Recurring>, val people: List<Person>, val udhar: List<Udhar>,
    val budgets: List<Budget>, val goals: List<Goal>, val sms: List<SmsItem>, val learned: List<Learned>,
    val settings: Map<String, String>,
)

/** Encrypted backup file: zip(data.json + attachments) sealed with a password (AES-GCM, PBKDF2). */
object Backup {
    private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,120}$")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun build(context: Context, password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        val d = app.repo.dao
        val data = BackupData(
            accounts = d.accountsNow(), categories = d.categoriesNow(), entries = d.entriesNow(), attachments = d.attachmentsNow(),
            merchants = d.merchantsNow(), recurring = d.recurringNow(), people = d.peopleNow(), udhar = d.udharNow(),
            budgets = d.budgetsNow(), goals = d.goalsNow(), sms = d.smsNow().filter { it.status != SmsStatus.INFO }, learned = d.learned(),
            settings = app.prefs.exportMap(),
        )
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("data.json")); z.write(json.encodeToString(data).toByteArray()); z.closeEntry()
            data.attachments.forEach { a ->
                val f = app.repo.attFile(a)
                if (f.exists()) { z.putNextEntry(ZipEntry("att/" + a.fileName)); z.write(FileCrypto.decrypt(context, f.readBytes())); z.closeEntry() }
            }
        }
        PassCrypto.encrypt(bos.toByteArray(), password)
    }

    data class Summary(val entries: Int, val accounts: Int, val createdAt: Long)

    /** Replaces everything on this phone with the backup. Throws with a plain message on a wrong password. */
    suspend fun restore(context: Context, bytes: ByteArray, password: CharArray): Summary = withContext(Dispatchers.IO) {
        val plain = try { PassCrypto.decrypt(bytes, password) } catch (e: IllegalArgumentException) { throw e } catch (e: Exception) { throw IllegalStateException("Wrong password, or the file is damaged") }
        var data: BackupData? = null
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(plain)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val b = z.readBytes()
                if (e.name == "data.json") data = json.decodeFromString<BackupData>(String(b))
                else if (e.name.startsWith("att/")) e.name.removePrefix("att/").takeIf { SAFE_NAME.matches(it) }?.let { files[it] = b }   // no "../" tricks
            }
        }
        val d = data ?: throw IllegalStateException("This file has no Bahi Khata data")
        val app = context.applicationContext as App
        val dao = app.repo.dao
        // 1) receipts go to a side folder first; 2) one database transaction; 3) folders swapped.
        // Steps 2–3 can't be cancelled half-way, so a restore never leaves entries without their receipts.
        val staged = File(context.filesDir, "attachments.new").apply { deleteRecursively(); mkdirs() }
        files.forEach { (name, b) -> File(staged, name).writeBytes(FileCrypto.encrypt(context, b)) }
        withContext(kotlinx.coroutines.NonCancellable) {
        app.db.withTransaction {
            dao.clearEntries(); dao.clearAttachments(); dao.clearAccounts(); dao.clearCategories(); dao.clearMerchants(); dao.clearRecurring()
            dao.clearPeople(); dao.clearUdhar(); dao.clearBudgets(); dao.clearGoals(); dao.clearSms(); dao.clearLearned()
            d.accounts.forEach { dao.upsertAccount(it) }; d.categories.forEach { dao.upsertCategory(it) }; d.entries.forEach { dao.upsertEntry(it) }
            d.merchants.forEach { dao.upsertMerchant(it) }; d.recurring.forEach { dao.upsertRecurring(it) }; d.people.forEach { dao.upsertPerson(it) }
            d.udhar.forEach { dao.upsertUdhar(it) }; d.budgets.forEach { dao.upsertBudget(it) }; d.goals.forEach { dao.upsertGoal(it) }
            d.sms.forEach { dao.insertSms(it) }; d.learned.forEach { dao.insertLearned(it) }
            d.attachments.filter { SAFE_NAME.matches(it.fileName) }.forEach { dao.insertAttachment(it) }
        }
        val dir = File(context.filesDir, "attachments")
        val old = File(context.filesDir, "attachments.old").apply { deleteRecursively() }
        if (dir.exists()) dir.renameTo(old)
        if (!staged.renameTo(dir)) { staged.copyRecursively(dir, overwrite = true); staged.deleteRecursively() }
        old.deleteRecursively()
        }
        val keep = app.prefs.now
        app.prefs.importMap(d.settings)
        // device-specific things are not taken from the backup
        app.prefs.update { it.copy(setupDone = true, lockOn = keep.lockOn, smsOn = keep.smsOn, driveOn = keep.driveOn, driveAccount = keep.driveAccount) }
        Summary(d.entries.size, d.accounts.size, d.createdAt)
    }

    // Drive auto-backup uses a password he sets once; it is stored on this phone encrypted with the keystore-wrapped key.
    fun savePassword(context: Context, pw: CharArray) {
        val key = DbKey.get(context) ?: return
        val enc = FileCrypto.encrypt(context, String(pw).toByteArray())
        context.getSharedPreferences("device_local", Context.MODE_PRIVATE).edit().putString("bkpw", Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
        key.size
    }

    fun loadPassword(context: Context): CharArray? {
        val s = context.getSharedPreferences("device_local", Context.MODE_PRIVATE).getString("bkpw", null) ?: return null
        return runCatching { String(FileCrypto.decrypt(context, Base64.decode(s, Base64.NO_WRAP))).toCharArray() }.getOrNull()
    }
}
