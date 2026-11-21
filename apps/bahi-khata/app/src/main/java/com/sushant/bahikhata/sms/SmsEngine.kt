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
                    processInternal(addr, body, date, st)
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

    /** Called by the SMS_RECEIVED receiver with the full (multi-part joined) message. */
    suspend fun onReceived(sender: String, body: String, time: Long) = lock.withLock {
        if (!enabled()) return@withLock
        val st = Stats()
        val item = processInternal(sender, body, time, st)
        Log.i(TAG, "received status=${item?.status ?: "skipped"} kind=${item?.kind ?: "-"} conf=${item?.confidence ?: 0}")
        if (item != null && (item.status == SmsStatus.PENDING || item.status == SmsStatus.LOOK)) Notify.smsItem(context, item)
    }

    /** The receiver sees the network timestamp and the inbox stores the arrival time, so keys use 10-minute buckets
     *  and the neighbouring buckets are checked too. */
    fun key(sender: String, body: String, time: Long, shift: Int = 0): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bucket = time / 600_000L + shift
        val bytes = md.digest((sender.uppercase(Locale.ROOT).takeLast(6) + "|" + body.trim() + "|" + bucket).toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private suspend fun processInternal(sender: String, body: String, time: Long, st: Stats): SmsItem? {
        val key = key(sender, body, time)
        if ((-1..1).any { dao.seen(key(sender, body, time, it)) }) return null
        dao.markSeen(SmsSeen(key, System.currentTimeMillis()))
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

        val account = accFor(p.last4) ?: learned?.accountId?.let { id -> accounts.firstOrNull { it.id == id } }

        when (p.kind) {
            SmsKind.BILL, SmsKind.DUE -> {
                val card = account?.takeIf { it.type == AccType.CARD } ?: accounts.singleOrNull { it.type == AccType.CARD && p.last4.isBlank() }
                if (card != null && p.billPaise >= 0) {
                    dao.upsertAccount(card.copy(billPaise = p.billPaise, minDuePaise = p.minDuePaise,
                        billDueEpochDay = (p.dueDate ?: CardMath.currentDue(LocalDate.now(), card.statementDay, card.dueDay))?.toEpochDay() ?: -1))
                }
                st.info++
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name,
                    amountPaise = p.billPaise.coerceAtLeast(0), last4 = p.last4, accountId = card?.id, confidence = p.confidence,
                    note = "Card bill" + (p.dueDate?.let { " due " + Dates.dayMonth(it) } ?: "")))
            }
            SmsKind.FAILED -> {
                // a failed payment can come after its debit SMS: flag the matching suggestion
                dao.smsSimilar(p.amountPaise, time - 30 * 60_000L, time + 30 * 60_000L).filter { it.status == SmsStatus.PENDING }.forEach {
                    dao.updateSms(it.copy(note = "A failed-payment SMS came for this amount — check before saving"))
                }
                st.info++
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name, amountPaise = p.amountPaise, last4 = p.last4, note = "Failed / declined payment"))
            }
            SmsKind.AUTOPAY_SETUP, SmsKind.BALANCE -> {
                st.info++
                val note = balanceNote(account, p.balancePaise)
                return insert(SmsItem(key = key, sender = sender, body = body, receivedAt = time, status = SmsStatus.INFO, kind = p.kind.name, amountPaise = p.amountPaise, last4 = p.last4, accountId = account?.id, balancePaise = p.balancePaise, note = note))
            }
            else -> Unit
        }

        // transaction or unknown money
        val catId = categoryFor(p, account)
        val dup = findDuplicate(p, account, time)
        val status = if (p.isTransaction && p.amountPaise > 0 && p.confidence >= 70) SmsStatus.PENDING else SmsStatus.LOOK
        if (status == SmsStatus.PENDING) st.parsed++ else st.look++
        val notes = listOfNotNull(dup?.second, balanceNote(account, p.balancePaise)).joinToString(" · ")
        return insert(SmsItem(
            key = key, sender = sender, body = body, receivedAt = time, status = status,
            kind = if (p.kind == SmsKind.UNKNOWN_MONEY) SmsKind.UNKNOWN_MONEY.name else p.kind.name,
            amountPaise = p.amountPaise, debit = p.debit, last4 = p.last4, accountId = account?.id, merchant = p.merchant,
            upiApp = p.upiApp, ref = p.ref, balancePaise = p.balancePaise, categoryId = catId, confidence = p.confidence,
            duplicateOf = dup?.first, note = notes,
        ))
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

    private suspend fun balanceNote(account: Account?, balance: Long): String? {
        if (account == null || balance < 0 || account.type == AccType.CARD) return null
        val nets = Ledger.nets(dao.accountsNow(), dao.entriesNow())
        val app = nets[account.id] ?: return null
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
        val pending = goAsync()
        app.appScope.launch {
            try { app.sms.onReceived(sender, body, time) } finally { pending.finish() }
        }
    }
}
