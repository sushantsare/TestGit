package com.sushant.bahikhata.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.sushant.bahikhata.App
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.work.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

/**
 * Turns bank SMS into suggestions. Three layers make sure nothing is missed:
 * 1) SMS_RECEIVED receiver (instant), 2) a ContentObserver on the inbox while the app runs,
 * 3) catch-up scans (app open, boot, every 6 h) from the last processed time.
 * Every processed SMS key is stored, so nothing is handled twice.
 */
class SmsEngine(private val context: Context, private val repo: Repo, private val scope: CoroutineScope) {
    private val lock = Mutex()
    private val dao get() = repo.dao
    private var observer: ContentObserver? = null

    data class Stats(var parsed: Int = 0, var look: Int = 0, var info: Int = 0, var ignored: Int = 0)

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun enabled() = repo.prefs.now.smsOn && hasPermission()

    fun startObserver() {
        if (observer != null || !enabled()) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scope.launch { scanSince(null, notify = true) } }
        }
        runCatching { context.contentResolver.registerContentObserver(Uri.parse("content://sms"), true, o) }
            .onSuccess { observer = o }
    }

    fun stopObserver() { observer?.let { context.contentResolver.unregisterContentObserver(it) }; observer = null }

    /** Reads the inbox from [since] (null = last scan, first run = 90 days) and processes every message. */
    suspend fun scanSince(since: Long?, notify: Boolean): Stats = lock.withLock {
        val st = Stats()
        if (!enabled()) return st
        val from = since ?: repo.prefs.lastSmsScan.takeIf { it > 0 }?.minus(10 * 60_000L) ?: (System.currentTimeMillis() - 90L * 86_400_000L)
        var newest = repo.prefs.lastSmsScan
        val before = dao.smsNow().count { it.status == SmsStatus.PENDING || it.status == SmsStatus.LOOK }
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?", arrayOf(from.toString()), "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0) ?: continue
                    val body = c.getString(1) ?: continue
                    val date = c.getLong(2)
                    val got = processInternal(addr, body, date, st, "scan")
                    // a money SMS first found by a catch-up scan minutes after it arrived means the phone held back
                    // the instant receiver (battery saver): offer the battery guide once
                    if (since == null && repo.prefs.lastSmsScan > 0 && got != null && got !== SEEN && got.status != SmsStatus.INFO &&
                        date < System.currentTimeMillis() - 5 * 60_000L && !repo.prefs.batteryTipShown) repo.prefs.batteryTipDue = true
                    if (date > newest) newest = date
                }
            }
        }.onFailure { Log.w(TAG, "scan failed", it) }
        repo.prefs.lastSmsScan = maxOf(newest, from)
        val after = dao.smsNow().count { it.status == SmsStatus.PENDING || it.status == SmsStatus.LOOK }
        if (notify && after > before) Notify.smsSummary(context, after - before)
        Diagnostics.count(context, "sms_scanned", st.parsed + st.look + st.info + st.ignored)
        Log.i(TAG, "scan parsed=${st.parsed} look=${st.look} info=${st.info} ignored=${st.ignored}")
        st
    }

    /** One time (after the 1.1 update, or right after SMS access is given): reads up to 13 months of older bank SMS
     *  that were never processed, only to learn subscriptions, payday and upcoming reminders. They never become
     *  suggestions and never change a balance. */
    suspend fun historyScan(): Int = lock.withLock {
        if (!enabled() || repo.prefs.historyDone) return 0
        val now = System.currentTimeMillis()
        val from = now - 400L * 86_400_000L; val until = now - 90L * 86_400_000L
        val st = Stats()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.DATE} < ?", arrayOf(from.toString(), until.toString()), "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0) ?: continue; val body = c.getString(1) ?: continue; val date = c.getLong(2)
                    val key = key(addr, body, date)
                    if ((-1..1).any { dao.seen(key(addr, body, date, it)) }) continue
                    handle(addr, body, date, st, key, history = true)
                    dao.markSeen(SmsSeen(key, System.currentTimeMillis()))
                }
            }
        }.onFailure { Log.w(TAG, "history scan failed", it); return 0 }
        repo.prefs.historyDone = true
        Log.i(TAG, "history read=${st.info} ignored=${st.ignored}")
        st.info
    }

    /** Called by the SMS_RECEIVED receiver with the full (multi-part joined) message. */
    suspend fun onReceived(sender: String, body: String, time: Long) = lock.withLock {
        if (!enabled()) return@withLock
        val st = Stats()
        val item = processInternal(sender, body, time, st, "receiver")
        Log.i(TAG, "receiver done")
        if (item != null && item !== SEEN && (item.status == SmsStatus.PENDING || item.status == SmsStatus.LOOK)) Notify.smsItem(context, item)
    }

    /** The receiver sees the network timestamp and the inbox stores the arrival time, so keys use 10-minute buckets
     *  and the neighbouring buckets are checked too. */
    fun key(sender: String, body: String, time: Long, shift: Int = 0): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bucket = time / 600_000L + shift
        val bytes = md.digest((sender.uppercase(Locale.ROOT).takeLast(6) + "|" + body.trim() + "|" + bucket).toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private suspend fun processInternal(sender: String, body: String, time: Long, st: Stats, via: String): SmsItem? {
        val r = processCore(sender, body, time, st)
        if (r !== SEEN) Log.i(TAG, "processed via=$via status=${r?.status ?: "ignored"} kind=${r?.kind ?: "-"}")
        return r
    }

    private val SEEN = SmsItem(key = "", sender = "", body = "", receivedAt = 0, status = "", kind = "")

    private suspend fun processCore(sender: String, body: String, time: Long, st: Stats): SmsItem? {
        val key = key(sender, body, time)
        if ((-1..1).any { dao.seen(key(sender, body, time, it)) }) return SEEN
        // "seen" is written only after the SMS is fully handled: if the phone kills or freezes the app half-way,
        // the next scan handles it again (the suggestion's unique key stops any double)
        val r = handle(sender, body, time, st, key)
        dao.markSeen(SmsSeen(key, System.currentTimeMillis()))
        return r
    }

    private suspend fun handle(sender: String, body: String, time: Long, st: Stats, key: String, history: Boolean = false): SmsItem? {
        var p = SmsParser.parse(sender, body)
        if (p.kind == SmsKind.NOT_FINANCIAL || p.kind == SmsKind.OTP || p.kind == SmsKind.PROMO) { st.ignored++; return null }

        val accounts = dao.accountsNow().filter { !it.archived }
        fun accFor(last4: String): Account? = if (last4.isBlank()) null else
            accounts.firstOrNull { a -> a.last4.split(',').map { it.trim() }.any { it.isNotEmpty() && (it == last4 || it.endsWith(last4) || last4.endsWith(it)) } }

        // learned formats (he fixed one like this before)
        val shape = SmsParser.shape(body)
        val learned = dao.learned().filter { it.senderKey == p.senderKey }.maxByOrNull { SmsParser.similarity(shape, SmsParser.shape(it.shape)) }
            ?.takeIf { SmsParser.similarity(shape, SmsParser.shape(it.shape)) >= 0.6 }
        if (learned != null && (p.kind == SmsKind.UNKNOWN_MONEY || p.confidence < 70)) {
            p = p.copy(kind = runCatching { SmsKind.valueOf(learned.kind) }.getOrDefault(if (learned.debit) SmsKind.DEBIT else SmsKind.CREDIT),
                debit = learned.debit, merchant = p.merchant.ifBlank { learned.merchant }, confidence = maxOf(p.confidence, 80))
        }

        var account = accFor(p.last4) ?: learned?.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
        // FASTag and wallet SMS rarely carry an account number: match a wallet account by its name
        if (account == null && ("FASTAG" in p.tags || "WALLET" in p.tags || Senders.upiApps.containsKey(p.senderKey))) {
            val want = when { "FASTAG" in p.tags -> "fastag"; else -> Senders.bankFor(p.senderKey).lowercase(Locale.ROOT).substringBefore(' ') }
            if (want.isNotBlank()) account = accounts.firstOrNull { it.type == AccType.WALLET && it.name.lowercase(Locale.ROOT).contains(want) }
        }

        // older SMS read once for insights: only real money movements (kept out of suggestions) and future reminders
        if (history) {
            if (p.kind == SmsKind.REMINDER) { saveReminder(p, account, key); return null }
            if (!p.isTransaction || p.amountPaise <= 0) { st.ignored++; return null }
            val named = upiNameFor(p.vpa, p.merchant)
            st.info++
            return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.HISTORY, kind = p.kind.name,
                amountPaise = p.amountPaise, debit = p.debit, last4 = p.last4, accountId = account?.id, merchant = named?.display?.takeIf { it.isNotBlank() } ?: p.merchant,
                upiApp = p.upiApp, ref = p.ref, vpa = p.vpa.lowercase(Locale.ROOT), flags = p.tags.joinToString(","), confidence = p.confidence))
        }

        // the bank's own balance line: kept per account so the app can check itself against it
        if (account != null) recordBankFigures(account, p, time)

        when (p.kind) {
            SmsKind.BILL, SmsKind.DUE -> {
                val card = account?.takeIf { it.type == AccType.CARD } ?: accounts.singleOrNull { it.type == AccType.CARD && p.last4.isBlank() }
                if (card != null && p.billPaise >= 0) {
                    dao.account(card.id)?.let { fresh -> dao.upsertAccount(fresh.copy(billPaise = p.billPaise, minDuePaise = p.minDuePaise,
                        billDueEpochDay = (p.dueDate ?: CardMath.currentDue(LocalDate.now(), card.statementDay, card.dueDay))?.toEpochDay() ?: -1)) }
                }
                st.info++
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name,
                    amountPaise = p.billPaise.coerceAtLeast(0), last4 = p.last4, accountId = card?.id, confidence = p.confidence,
                    note = "Card bill" + (p.dueDate?.let { " due " + Dates.dayMonth(it) } ?: "")))
            }
            SmsKind.FAILED -> {
                val undone = cancelMatching(p.amountPaise, account?.id, time - 30 * 60_000L, time + 30 * 60_000L, "Payment failed")
                st.info++
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name, amountPaise = p.amountPaise, last4 = p.last4,
                    accountId = account?.id, note = if (undone > 0) "Failed payment — the matching entry was cancelled" else "Failed / declined payment"))
            }
            SmsKind.AUTOPAY_SETUP, SmsKind.BALANCE -> {
                st.info++
                if (p.kind == SmsKind.AUTOPAY_SETUP) SmsParser.reminderOf(body, p.senderKey)?.let { (k, d, t) -> saveReminder(p.copy(remindKind = k, remindDate = d, remindTitle = t), account, key) }
                val note = balanceNote(account, p.balancePaise, time)
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name, amountPaise = p.amountPaise, last4 = p.last4, accountId = account?.id, balancePaise = p.balancePaise, note = note.orEmpty()))
            }
            SmsKind.REMINDER -> {
                st.info++
                saveReminder(p, account, key)
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name, amountPaise = p.amountPaise,
                    accountId = account?.id, note = p.remindTitle + (p.remindDate?.let { " · " + Dates.dayMonth(it) } ?: "")))
            }
            else -> Unit
        }

        // a reversal of a payment that already went through: cancel that payment instead of adding income
        if (p.kind == SmsKind.REFUND && "REVERSAL" in p.tags && p.amountPaise > 0) {
            val undone = cancelMatching(p.amountPaise, account?.id, time - 3 * 86_400_000L, time, "Payment reversed by the bank")
            if (undone > 0) {
                st.info++
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name, amountPaise = p.amountPaise, debit = false,
                    last4 = p.last4, accountId = account?.id, note = "Reversal — the earlier ${Money.fmt(p.amountPaise)} payment was cancelled"))
            }
        }

        // transaction or unknown money
        val named = upiNameFor(p.vpa, p.merchant)
        val merchant = named?.display?.takeIf { it.isNotBlank() } ?: p.merchant
        val catId = named?.categoryId ?: categoryFor(p.copy(merchant = merchant), account)
        val flags = LinkedHashSet(p.tags)
        personFor(named, merchant, p.debit)?.let { flags += "PERSON:$it" }
        if (p.debit && p.isTransaction && (named?.askItems ?: true)) flags += "ASK"
        val dup = findDuplicate(p, account, time)
        val status = if (p.isTransaction && p.amountPaise > 0 && p.confidence >= 70) SmsStatus.PENDING else SmsStatus.LOOK
        if (status == SmsStatus.PENDING) st.parsed++ else st.look++
        val notes = listOfNotNull(dup?.second, balanceNote(account, p.balancePaise, time)).joinToString(" · ")
        val item = insert(SmsItem(
            key = key, sender = sender, body = body, receivedAt = time, status = status,
            kind = if (p.kind == SmsKind.UNKNOWN_MONEY) SmsKind.UNKNOWN_MONEY.name else p.kind.name,
            amountPaise = p.amountPaise, debit = p.debit, last4 = p.last4, accountId = account?.id, merchant = merchant,
            upiApp = p.upiApp, ref = p.ref, balancePaise = p.balancePaise, categoryId = catId, confidence = p.confidence,
            duplicateOf = dup?.first, note = notes, vpa = p.vpa.lowercase(Locale.ROOT), flags = flags.joinToString(","),
        )) ?: return null
        pairTransfer(item)?.let { return it }
        // unusual debits get their own alert (only for fresh SMS, not when old ones are caught up)
        if (item.debit && item.amountPaise > 0 && time > System.currentTimeMillis() - 6 * 3_600_000L && repo.prefs.now.alertsOn) {
            val why = Insights.unusual(item.amountPaise, time, item.accountId, "INTL" in p.tags, dao.entriesNow())
            if (why.isNotEmpty()) Notify.unusual(context, item, why)
        }
        return item
    }

    /** A saved UPI name for this SMS: by UPI ID first, then by the shop text when the SMS has no UPI ID. */
    private suspend fun upiNameFor(vpa: String, merchant: String): UpiName? {
        if (vpa.isNotBlank()) dao.upiName("vpa:" + vpa.lowercase(Locale.ROOT))?.let { return it }
        if (merchant.isNotBlank()) dao.upiName("name:" + merchant.trim().lowercase(Locale.ROOT))?.let { return it }
        return null
    }

    /** Udhar: money from someone who owes him, or to someone he owes, can be settled against their balance. */
    private suspend fun personFor(named: UpiName?, merchant: String, debit: Boolean): Long? {
        val people = dao.peopleNow()
        val p = named?.personId?.let { id -> people.firstOrNull { it.id == id } }
            ?: people.firstOrNull { merchant.isNotBlank() && it.name.trim().equals(merchant.trim(), ignoreCase = true) } ?: return null
        val bal = Ledger.personBalance(p, dao.udharNow())
        return p.id.takeIf { (!debit && bal > 0) || (debit && bal < 0) }
    }

    private suspend fun saveReminder(p: Parsed, account: Account?, key: String) {
        val date = p.remindDate ?: return
        val rk = "${p.remindKind}|${p.remindTitle.lowercase(Locale.ROOT)}|${date.toEpochDay()}"
        if (dao.reminderByKey(rk) != null) return
        // a newer reminder for the same thing replaces the older one (e.g. the plan was renewed)
        dao.remindersNow().filter { it.kind == p.remindKind && it.title.equals(p.remindTitle, true) && !it.done && it.dueEpochDay < date.toEpochDay() }
            .forEach { dao.upsertReminder(it.copy(done = true)) }
        dao.upsertReminder(Reminder(key = rk, kind = p.remindKind, title = p.remindTitle, amountPaise = if (p.amountPaise > 0) p.amountPaise else -1,
            dueEpochDay = date.toEpochDay(), accountId = account?.id))
        @Suppress("UNUSED_VARIABLE") val k = key
    }

    /** Remembers the bank's own balance line (bank accounts) and available limit (cards). */
    private suspend fun recordBankFigures(account: Account, p: Parsed, time: Long) {
        val a = dao.account(account.id) ?: return
        var n = a
        if (a.type != AccType.CARD && p.balancePaise >= 0 && time >= a.bankBalAt) n = n.copy(bankBalPaise = p.balancePaise, bankBalAt = time)
        if (a.type == AccType.CARD && p.availLimitPaise >= 0 && time >= a.availLimitAt) {
            n = n.copy(availLimitPaise = p.availLimitPaise, availLimitAt = time)
            // the card's limit wasn't typed in: work it out once from what's available plus what's owed
            if (a.limitPaise <= 0) {
                val owed = maxOf(0L, -(Ledger.netAt(a, dao.entriesNow(), time)))
                n = n.copy(limitPaise = p.availLimitPaise + owed)
            }
        }
        if (n != a) dao.upsertAccount(n)
    }

    /** Cancels the payment this SMS undoes: a waiting suggestion is set aside, a saved entry is kept but marked
     *  cancelled (it can be restored from the entry). Returns how many were undone. */
    private suspend fun cancelMatching(amount: Long, accountId: Long?, from: Long, to: Long, why: String): Int {
        if (amount <= 0) return 0
        var n = 0
        val near = dao.smsSimilar(amount, from, to).filter { it.debit && (accountId == null || it.accountId == null || it.accountId == accountId) }
        near.filter { it.status == SmsStatus.PENDING }.maxByOrNull { it.receivedAt }?.let {
            dao.updateSms(it.copy(status = SmsStatus.IGNORED, note = "$why — not saved")); Notify.cancel(context, Notify.smsId(it)); n++
        }
        if (n == 0) near.filter { it.status == SmsStatus.ACCEPTED && it.entryId != null }.maxByOrNull { it.receivedAt }?.let { sms ->
            dao.entry(sms.entryId!!)?.takeIf { !it.cancelled }?.let { e -> dao.upsertEntry(e.copy(cancelled = true, note = listOfNotNull(e.note, why).joinToString(" · "))); n++ }
        }
        if (n > 0) Notify.refreshSummary(context, dao.smsNow().count { it.status == SmsStatus.PENDING || it.status == SmsStatus.LOOK })
        return n
    }

    /** Money leaving one of his accounts and the same amount arriving in another of his accounts within minutes
     *  is a transfer between his own accounts — not spending and not income. */
    private suspend fun pairTransfer(item: SmsItem): SmsItem? {
        val acc = item.accountId ?: return null
        if (item.amountPaise <= 0 || item.kind !in setOf(SmsKind.DEBIT.name, SmsKind.CREDIT.name)) return null
        val other = dao.smsSimilar(item.amountPaise, item.receivedAt - 20 * 60_000L, item.receivedAt + 20 * 60_000L)
            .firstOrNull { it.id != item.id && it.debit != item.debit && it.accountId != null && it.accountId != acc && it.kind in setOf(SmsKind.DEBIT.name, SmsKind.CREDIT.name) }
            ?: return null
        val from = if (item.debit) acc else other.accountId!!
        val to = if (item.debit) other.accountId!! else acc
        val toName = dao.account(to)?.name ?: "your account"
        val fromName = dao.account(from)?.name ?: "your account"
        val f = "FROM:$from,TO:$to"
        val existing = other.entryId?.let { dao.entry(it) }
        if (other.status == SmsStatus.ACCEPTED && existing != null) {
            // already saved as spending/income: turn that entry into the transfer it really was
            dao.upsertEntry(existing.copy(type = EType.TRANSFER, accountId = from, toAccountId = to, categoryId = null, merchant = null, note = "Between your accounts"))
            val done = item.copy(status = SmsStatus.ACCEPTED, entryId = existing.id, kind = "TRANSFER", flags = f, note = "Transfer $fromName → $toName (saved)")
            dao.updateSms(done); return done
        }
        if (other.status == SmsStatus.PENDING || other.status == SmsStatus.LOOK) {
            dao.updateSms(other.copy(status = SmsStatus.INFO, note = "Part of a transfer between your accounts", duplicateOf = item.id))
            Notify.cancel(context, Notify.smsId(other))
            val t = item.copy(kind = "TRANSFER", accountId = from, debit = true, flags = f, merchant = "$fromName → $toName", categoryId = null,
                note = "Between your accounts — not counted as spending", status = SmsStatus.PENDING)
            dao.updateSms(t); return t
        }
        return null
    }

    private suspend fun insert(item: SmsItem): SmsItem? {
        val id = dao.insertSms(item)
        return if (id > 0) item.copy(id = id) else null
    }

    private suspend fun categoryFor(p: Parsed, account: Account?): Long? {
        val cats = dao.categoriesNow().filter { !it.archived }
        if (p.merchant.isNotBlank()) repo.merchant(p.merchant)?.categoryId?.let { return it }
        val kind = if (p.debit) "EXPENSE" else "INCOME"
        if (p.kind == SmsKind.REFUND) return cats.firstOrNull { it.kind == "INCOME" && it.name.equals("Refund", true) }?.id
        if (p.categoryHint.isNotBlank() && p.tags.isNotEmpty()) cats.firstOrNull { it.kind == kind && it.name.equals(p.categoryHint, true) }?.let { return it.id }
        if (!p.debit && Regex("(?i)salary|sal\\b").containsMatchIn(p.merchant)) return cats.firstOrNull { it.kind == "INCOME" && it.name.equals("Salary", true) }?.id
        if (p.categoryHint.isNotBlank()) return cats.firstOrNull { it.kind == kind && it.name.equals(p.categoryHint, true) }?.id
        return null
    }

    /** Same amount within 15 min (or same ref) from another SMS, or an entry he already added within 30 min. */
    private suspend fun findDuplicate(p: Parsed, account: Account?, time: Long): Pair<Long?, String>? {
        if (p.amountPaise <= 0) return null
        val near = dao.smsSimilar(p.amountPaise, time - 15 * 60_000L, time + 15 * 60_000L)
        near.firstOrNull { p.ref.isNotBlank() && it.ref == p.ref || it.debit == p.debit }?.let { return it.id to "Looks like the same payment as another SMS" }
        val entries = if (account != null) dao.similar(account.id, p.amountPaise, time - 30 * 60_000L, time + 30 * 60_000L)
        else dao.similarAny(p.amountPaise, time - 30 * 60_000L, time + 30 * 60_000L)
        entries.firstOrNull { it.smsKey == null }?.let { return null to "Already added? You saved ${Money.fmt(it.amountPaise)} at ${Dates.time(it.time)}" }
        return null
    }

    private suspend fun balanceNote(account: Account?, balance: Long, time: Long): String? {
        if (account == null || balance < 0 || account.type == AccType.CARD) return null
        val app = Ledger.netAt(dao.account(account.id) ?: account, dao.entriesNow(), time)
        val diff = balance - app
        return if (kotlin.math.abs(diff) >= 100) "BAL:" + balance else null
    }

    companion object { const val TAG = "BahiSms" }
}

/** Instant layer: SMS_RECEIVED. Joins multi-part messages and hands them to the engine. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if (msgs.isEmpty()) return
        val sender = msgs[0].displayOriginatingAddress ?: msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val time = msgs[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val app = context.applicationContext as App
        if (!app.ready) return   // the SMS stays in the inbox; the catch-up scan picks it up later
        val pending = goAsync()
        app.appScope.launch {
            try { app.sms.onReceived(sender, body, time) } finally { pending.finish() }
        }
    }
}
