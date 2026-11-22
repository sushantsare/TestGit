package com.sushant.bahikhata.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Single repository over the encrypted database. Every write that touches money goes through here. */
class Repo(private val context: Context, val db: AppDb, val prefs: Prefs) {
    val dao = db.dao()

    val accounts = dao.accounts()
    val categories = dao.categories()
    val entries = dao.entries()
    val people = dao.people()
    val udhar = dao.udhar()
    val budgets = dao.budgets()
    val goals = dao.goals()
    val recurring = dao.recurring()
    val merchants = dao.merchants()
    val attachmentCounts = dao.attachmentCounts()
    val smsPending = dao.sms(listOf(SmsStatus.PENDING))
    val smsLook = dao.sms(listOf(SmsStatus.LOOK))
    val smsIgnored = dao.sms(listOf(SmsStatus.IGNORED))
    val smsPendingCount = dao.smsCount(SmsStatus.PENDING)
    val smsLookCount = dao.smsCount(SmsStatus.LOOK)

    fun attachments(entryId: Long) = dao.attachments(entryId)
    fun entryFlow(id: Long) = dao.entryFlow(id)

    suspend fun ensureCategories() = db.withTransaction {   // check + insert in one transaction: never twice
        if (dao.categoriesNow().isNotEmpty()) return@withTransaction
        DefaultData.categories().forEach { dao.upsertCategory(it) }
    }

    // ---------------- accounts
    suspend fun saveAccount(a: Account): Long = dao.upsertAccount(a)
    suspend fun archiveAccount(a: Account) = dao.upsertAccount(a.copy(archived = !a.archived))

    /** Deletes the account and all its entries (asked with a confirm dialog in the UI). */
    suspend fun deleteAccount(a: Account) = db.withTransaction {
        dao.entriesNow().filter { it.accountId == a.id || it.toAccountId == a.id }.forEach { deleteEntryInternal(it) }
        dao.deleteAccount(a)
    }

    /** Adds an ADJUSTMENT so the app balance equals the real balance he typed. */
    suspend fun adjustBalance(acc: Account, realPaise: Long) {
        val nets = Ledger.nets(dao.accountsNow(), dao.entriesNow())
        val cur = nets[acc.id] ?: 0L
        val target = if (acc.type == AccType.CARD) -realPaise else realPaise
        val diff = target - cur
        if (diff == 0L) return
        dao.upsertEntry(Entry(type = EType.ADJUSTMENT, amountPaise = diff, accountId = acc.id, note = "Balance adjusted to match bank"))
    }

    suspend fun transfer(type: String, from: Long, to: Long, amount: Long, time: Long = System.currentTimeMillis(), note: String? = null, smsKey: String? = null): Long =
        dao.upsertEntry(Entry(type = type, amountPaise = amount, accountId = from, toAccountId = to, time = time, note = note, smsKey = smsKey))

    // ---------------- categories
    suspend fun saveCategory(c: Category) = dao.upsertCategory(c)

    /** Moves a category one place up (-1) or down (+1) within its kind; the order shows everywhere categories are picked. */
    suspend fun moveCategory(c: Category, dir: Int) = db.withTransaction {
        val list = dao.categoriesNow().filter { it.kind == c.kind }.sortedWith(compareBy({ it.sort }, { it.id })).toMutableList()
        val i = list.indexOfFirst { it.id == c.id }; val j = i + dir
        if (i < 0 || j !in list.indices) return@withTransaction
        java.util.Collections.swap(list, i, j)
        list.forEachIndexed { k, x -> if (x.sort != k) dao.upsertCategory(x.copy(sort = k)) }
    }
    suspend fun mergeCategory(from: Category, into: Long) = db.withTransaction {
        dao.moveCategory(from.id, into); dao.deleteCategory(from)
    }

    // ---------------- entries
    data class SaveRequest(
        val entry: Entry,
        val newFiles: List<Uri> = emptyList(),
        val splits: List<Pair<Long, Long>> = emptyList(),   // personId -> share paise
        val repeatRule: String? = null,
    )

    suspend fun saveEntry(r: SaveRequest): Long = withContext(Dispatchers.IO) {
        val id = db.withTransaction {
            var e = r.entry
            if (r.repeatRule != null && e.recurringId == null) {
                val next = Ledger.nextDate(Dates.date(e.time), r.repeatRule)
                val rid = dao.upsertRecurring(Recurring(type = e.type, amountPaise = e.amountPaise, accountId = e.accountId, categoryId = e.categoryId,
                    merchant = e.merchant, note = e.note, rule = r.repeatRule, nextEpochDay = next.toEpochDay()))
                e = e.copy(recurringId = rid)
            }
            val id = dao.upsertEntry(e).let { if (e.id != 0L) e.id else it }
            // the entry's shares are always replaced by what the form shows now ("No split" removes them)
            dao.udharOfEntry(id).forEach { dao.deleteUdhar(it) }
            run {
                r.splits.filter { it.second > 0 }.forEach { (pid, share) ->
                    dao.upsertUdhar(Udhar(personId = pid, gave = true, amountPaise = share, entryId = id, note = "Split: " + (e.merchant ?: "bill"), time = e.time))
                }
            }
            if (!e.merchant.isNullOrBlank() && e.type == EType.EXPENSE || e.type == EType.INCOME && !e.merchant.isNullOrBlank()) remember(e)
            id
        }
        lastAttachFailures = r.newFiles.count { runCatching { addAttachment(id, it) }.isFailure }
        if (r.entry.type == EType.EXPENSE || r.entry.type == EType.INCOME) prefs.update { it.copy(lastAccountId = r.entry.accountId) }
        id
    }

    /** How many files of the last save couldn't be stored (shown to the user, never silent). */
    @Volatile var lastAttachFailures = 0
        private set

    private suspend fun remember(e: Entry) {
        val key = e.merchant!!.trim().lowercase(Locale.ROOT)
        val old = dao.merchant(key)
        dao.upsertMerchant(Merchant(key, e.merchant.trim(), e.categoryId, e.accountId, (old?.uses ?: 0) + 1, System.currentTimeMillis(), kind = e.type))
    }

    suspend fun merchant(name: String): Merchant? = dao.merchant(name.trim().lowercase(Locale.ROOT))

    data class Deleted(val entry: Entry, val attachments: List<Attachment>, val udhar: List<Udhar>, val files: Map<String, ByteArray>)

    /** Deletes an entry and returns what's needed to undo it. */
    suspend fun deleteEntry(e: Entry): Deleted = withContext(Dispatchers.IO) {
        db.withTransaction { deleteEntryInternal(e) }
    }

    private suspend fun deleteEntryInternal(e: Entry): Deleted {
        val atts = dao.attachmentsNow().filter { it.entryId == e.id }
        val files = atts.associate { it.fileName to (runCatching { attFile(it).readBytes() }.getOrDefault(ByteArray(0))) }
        val ud = dao.udharOfEntry(e.id)
        ud.forEach { dao.deleteUdhar(it) }
        atts.forEach { dao.deleteAttachment(it); attFile(it).delete() }
        dao.deleteEntry(e)
        dao.smsNow().filter { it.entryId == e.id }.forEach { dao.updateSms(it.copy(status = SmsStatus.IGNORED, entryId = null)) }
        return Deleted(e, atts, ud, files)
    }

    suspend fun undoDelete(d: Deleted) = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.upsertEntry(d.entry)
            d.udhar.forEach { dao.upsertUdhar(it) }
            d.attachments.forEach { a -> d.files[a.fileName]?.let { attFile(a).apply { parentFile?.mkdirs() }.writeBytes(it) }; dao.insertAttachment(a) }
        }
    }

    suspend fun duplicate(e: Entry): Long = dao.upsertEntry(e.copy(id = 0, time = System.currentTimeMillis(), smsKey = null, recurringId = null, createdAt = System.currentTimeMillis()))

    // ---------------- attachments (encrypted in app storage)
    private fun attDir() = File(context.filesDir, "attachments").apply { mkdirs() }
    fun attFile(a: Attachment) = File(attDir(), a.fileName)

    suspend fun addAttachment(entryId: Long, uri: Uri): Long = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "file"; var size = 0L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        val mime = cr.getType(uri) ?: if (name.endsWith(".pdf", true)) "application/pdf" else "image/jpeg"
        val bytes = cr.openInputStream(uri)!!.use { it.readBytes() }
        val file = UUID.randomUUID().toString()
        File(attDir(), file).writeBytes(FileCrypto.encrypt(context, bytes))
        if (uri.authority == context.packageName + ".files") runCatching { cr.delete(uri, null, null) }
        dao.insertAttachment(Attachment(entryId = entryId, fileName = file, mime = mime, displayName = name, size = if (size > 0) size else bytes.size.toLong()))
    }

    suspend fun removeAttachment(a: Attachment) = withContext(Dispatchers.IO) { dao.deleteAttachment(a); attFile(a).delete() }

    /** Decrypts an attachment into the cache so it can be shown or shared (cache is cleared on next start). */
    suspend fun openAttachment(a: Attachment): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "open").apply { mkdirs() }
        val ext = a.displayName.substringAfterLast('.', if (a.mime.contains("pdf")) "pdf" else "jpg")
        val out = File(dir, a.fileName + "." + ext)
        if (!out.exists()) out.writeBytes(FileCrypto.decrypt(context, attFile(a).readBytes()))
        out
    }

    // ---------------- people / udhar
    suspend fun savePerson(p: Person) = dao.upsertPerson(p)
    /** Everything a person delete removes, so Undo can put it back exactly. */
    class PersonBackup(val person: Person, val lines: List<Udhar>, val entries: List<Entry>)

    suspend fun deletePerson(p: Person): PersonBackup = db.withTransaction {
        val lines = dao.udharNow().filter { it.personId == p.id }
        val entries = lines.mapNotNull { u -> u.entryId?.let { id -> dao.entry(id)?.takeIf { it.type == EType.UDHAR_GAVE || it.type == EType.UDHAR_GOT } } }
        entries.forEach { dao.deleteEntry(it) }
        dao.deleteUdharOf(p.id); dao.deletePerson(p)
        PersonBackup(p, lines, entries)
    }

    suspend fun restorePerson(b: PersonBackup) = db.withTransaction {
        dao.upsertPerson(b.person)
        b.entries.forEach { dao.upsertEntry(it) }
        b.lines.forEach { dao.upsertUdhar(it) }
    }

    /** I gave / I got money. With an account, the money also leaves / enters that account (not counted as spending or income). */
    suspend fun addUdhar(personId: Long, gave: Boolean, amount: Long, accountId: Long?, note: String, time: Long = System.currentTimeMillis()) = db.withTransaction {
        val entryId = accountId?.let {
            dao.upsertEntry(Entry(type = if (gave) EType.UDHAR_GAVE else EType.UDHAR_GOT, amountPaise = amount, accountId = it, note = note.ifBlank { null }, time = time, personId = personId))
        }
        dao.upsertUdhar(Udhar(personId = personId, gave = gave, amountPaise = amount, accountId = accountId, entryId = entryId, note = note, time = time))
    }

    suspend fun deleteUdhar(u: Udhar) = db.withTransaction {
        u.entryId?.let { id -> dao.entry(id)?.takeIf { it.type == EType.UDHAR_GAVE || it.type == EType.UDHAR_GOT }?.let { dao.deleteEntry(it) } }
        dao.deleteUdhar(u)
    }

    suspend fun restoreUdhar(u: Udhar) = dao.upsertUdhar(u)

    // ---------------- budgets / goals / recurring
    suspend fun saveBudget(b: Budget) = dao.upsertBudget(b)
    suspend fun deleteBudget(b: Budget) = dao.deleteBudget(b)
    suspend fun saveGoal(g: Goal) = dao.upsertGoal(g)
    suspend fun deleteGoal(g: Goal) = dao.deleteGoal(g)
    suspend fun saveRecurring(r: Recurring) = dao.upsertRecurring(r)
    suspend fun deleteRecurring(r: Recurring) = dao.deleteRecurring(r)

    /** Adds every recurring entry that is due up to [today]. Returns how many were added. */
    suspend fun runRecurring(today: LocalDate = Dates.today()): Int = db.withTransaction {
        var n = 0
        dao.recurringNow().filter { it.active }.forEach { r ->
            var next = LocalDate.ofEpochDay(r.nextEpochDay)
            var guard = 0
            while (!next.isAfter(today) && guard++ < 60) {
                val t = Dates.startOf(next) + 9 * 3_600_000L
                dao.upsertEntry(Entry(type = r.type, amountPaise = r.amountPaise, accountId = r.accountId, categoryId = r.categoryId,
                    merchant = r.merchant, note = r.note, time = t, recurringId = r.id))
                next = Ledger.nextDate(next, r.rule); n++
            }
            if (next.toEpochDay() != r.nextEpochDay) dao.upsertRecurring(r.copy(nextEpochDay = next.toEpochDay()))
        }
        n
    }

    // ---------------- SMS suggestions
    /** Turns a suggestion into a real entry. Returns the entry id. */
    suspend fun acceptSms(item: SmsItem, accountId: Long, categoryId: Long?, merchant: String, amount: Long = item.amountPaise, debit: Boolean = item.debit, kind: String = item.kind): Long =
        db.withTransaction {
            val time = item.receivedAt
            val id = when (kind) {
                "ATM" -> {
                    val cash = dao.accountsNow().firstOrNull { it.type == AccType.CASH && !it.archived }?.id
                        ?: dao.upsertAccount(Account(type = AccType.CASH, name = "Cash"))
                    dao.upsertEntry(Entry(type = EType.CASH_WITHDRAWAL, amountPaise = amount, accountId = accountId, toAccountId = cash, time = time, smsKey = item.key, note = "ATM withdrawal"))
                }
                "CARD_PAYMENT" -> {
                    val from = dao.accountsNow().firstOrNull { it.type == AccType.BANK && !it.archived }?.id ?: accountId
                    dao.upsertEntry(Entry(type = EType.CARD_PAYMENT, amountPaise = amount, accountId = from, toAccountId = accountId, time = time, smsKey = item.key, note = "Card bill paid"))
                }
                else -> {
                    val e = Entry(type = if (debit) EType.EXPENSE else EType.INCOME, amountPaise = amount, accountId = accountId,
                        categoryId = categoryId, upiApp = item.upiApp.ifBlank { null }, merchant = merchant.ifBlank { null }, time = time, smsKey = item.key,
                        note = if (kind == "REFUND") "Refund" else null)
                    val id = dao.upsertEntry(e)
                    if (!merchant.isBlank()) remember(e)
                    id
                }
            }
            dao.updateSms(item.copy(status = SmsStatus.ACCEPTED, entryId = id, accountId = accountId, categoryId = categoryId, merchant = merchant, amountPaise = amount))
            // remember the last-4 for this account so the next SMS matches automatically
            if (item.last4.isNotBlank()) dao.account(accountId)?.let { a ->
                val l4 = a.last4.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (item.last4 !in l4) dao.upsertAccount(a.copy(last4 = (l4 + item.last4).joinToString(",")))
            }
            id
        }

    /** Marks an SMS as saved after he edited it on the full entry screen, and remembers its last-4 for the account. */
    suspend fun linkSms(item: SmsItem, entryId: Long, accountId: Long) {
        dao.updateSms(item.copy(status = SmsStatus.ACCEPTED, entryId = entryId, accountId = accountId))
        if (item.last4.isNotBlank()) dao.account(accountId)?.let { a ->
            val l4 = a.last4.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (item.last4 !in l4) dao.upsertAccount(a.copy(last4 = (l4 + item.last4).joinToString(",")))
        }
    }

    suspend fun ignoreSms(item: SmsItem) = dao.updateSms(item.copy(status = SmsStatus.IGNORED))
    suspend fun restoreSms(item: SmsItem) = dao.updateSms(item.copy(status = if (item.amountPaise > 0 && item.confidence >= 70) SmsStatus.PENDING else SmsStatus.LOOK))
    suspend fun teach(l: Learned) = dao.insertLearned(l)

    // ---------------- reset
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.clearEntries(); dao.clearAttachments(); dao.clearAccounts(); dao.clearCategories(); dao.clearMerchants(); dao.clearRecurring()
            dao.clearPeople(); dao.clearUdhar(); dao.clearBudgets(); dao.clearGoals(); dao.clearSms(); dao.clearLearned()
        }
        attDir().deleteRecursively()
        ensureCategories()
    }
}

object DefaultData {
    // icon keys map to Material icons in ui/components/Icons.kt
    fun categories(): List<Category> = listOf(
        Category(name = "Food", kind = "EXPENSE", icon = "restaurant", colorIdx = 0, sort = 0),
        Category(name = "Groceries", kind = "EXPENSE", icon = "cart", colorIdx = 1, sort = 1),
        Category(name = "Travel", kind = "EXPENSE", icon = "car", colorIdx = 2, sort = 2),
        Category(name = "Fuel", kind = "EXPENSE", icon = "fuel", colorIdx = 3, sort = 3),
        Category(name = "Rent", kind = "EXPENSE", icon = "home", colorIdx = 4, sort = 4),
        Category(name = "Bills & Recharge", kind = "EXPENSE", icon = "bill", colorIdx = 5, sort = 5),
        Category(name = "Shopping", kind = "EXPENSE", icon = "bag", colorIdx = 6, sort = 6),
        Category(name = "Health", kind = "EXPENSE", icon = "health", colorIdx = 7, sort = 7),
        Category(name = "Education", kind = "EXPENSE", icon = "school", colorIdx = 8, sort = 8),
        Category(name = "Entertainment", kind = "EXPENSE", icon = "movie", colorIdx = 9, sort = 9),
        Category(name = "EMI", kind = "EXPENSE", icon = "bank", colorIdx = 0, sort = 10),
        Category(name = "Gifts", kind = "EXPENSE", icon = "gift", colorIdx = 1, sort = 11),
        Category(name = "Others", kind = "EXPENSE", icon = "more", colorIdx = 2, sort = 12),
        Category(name = "Salary", kind = "INCOME", icon = "bank", colorIdx = 3, sort = 0),
        Category(name = "Freelance", kind = "INCOME", icon = "work", colorIdx = 4, sort = 1),
        Category(name = "Interest", kind = "INCOME", icon = "percent", colorIdx = 5, sort = 2),
        Category(name = "Refund", kind = "INCOME", icon = "undo", colorIdx = 6, sort = 3),
        Category(name = "Gift", kind = "INCOME", icon = "gift", colorIdx = 7, sort = 4),
        Category(name = "Others", kind = "INCOME", icon = "more", colorIdx = 8, sort = 5),
    )
}
