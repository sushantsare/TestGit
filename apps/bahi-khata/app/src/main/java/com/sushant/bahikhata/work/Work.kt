package com.sushant.bahikhata.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.sushant.bahikhata.App
import com.sushant.bahikhata.MainActivity
import com.sushant.bahikhata.R
import com.sushant.bahikhata.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

object Notify {
    const val CH_SMS = "sms"; const val CH_REMIND = "reminders"; const val CH_DUE = "dues"; const val CH_BUDGET = "budgets"; const val CH_BACKUP = "backup"
    const val CH_ALERT = "alerts"; const val CH_BILLS = "bills"
    const val SUMMARY_ID = 9001
    private const val GROUP_SMS = "sms_group"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_SMS, "Entries from SMS", NotificationManager.IMPORTANCE_HIGH).apply { description = "Bank SMS that are ready to save with one tap" },
            NotificationChannel(CH_DUE, "Card bill due dates", NotificationManager.IMPORTANCE_HIGH).apply { description = "Reminders before a credit card bill is due" },
            NotificationChannel(CH_REMIND, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Evening reminder when nothing was added today" },
            NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "When a budget reaches 80% and 100%" },
            NotificationChannel(CH_BACKUP, "Backup", NotificationManager.IMPORTANCE_LOW).apply { description = "Google Drive backup problems" },
            NotificationChannel(CH_ALERT, "Unusual payments", NotificationManager.IMPORTANCE_HIGH).apply { description = "A debit that looks out of the ordinary: very large, late at night, international or from an account you rarely use" },
            NotificationChannel(CH_BILLS, "Bills, renewals and EMIs", NotificationManager.IMPORTANCE_HIGH).apply { description = "Before a recharge plan ends, a utility bill is due, or an EMI / AutoPay is about to be debited" },
        ))
    }

    fun allowed(c: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(c).areNotificationsEnabled()

    private fun open(c: Context, route: String, req: Int): PendingIntent =
        PendingIntent.getActivity(c, req, Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Suppress("MissingPermission")
    private fun post(c: Context, id: Int, b: NotificationCompat.Builder) {
        if (!allowed(c)) return
        runCatching { NotificationManagerCompat.from(c).notify(id, b.build()) }
    }

    fun cancel(c: Context, id: Int) = NotificationManagerCompat.from(c).cancel(id)

    /** On the lock screen only a neutral line shows; amounts and shops appear after the phone is unlocked. */
    private fun base(c: Context, ch: String) = NotificationCompat.Builder(c, ch).setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(c, R.color.brand)).setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(NotificationCompat.Builder(c, ch).setSmallIcon(R.drawable.ic_notification).setColor(ContextCompat.getColor(c, R.color.brand))
            .setContentTitle("Bahi Khata").setContentText("New update — unlock to see it").build())

    /** Amount text that respects "Hide amounts". */
    private fun amt(c: Context, paise: Long): String =
        if ((c.applicationContext as? com.sushant.bahikhata.App)?.prefs?.now?.hides(HideArea.NOTIF) == true) "₹ ••••" else Money.fmt(paise)

    fun smsId(item: SmsItem) = 10_000 + (item.id % 50_000).toInt()

    fun smsItem(c: Context, item: SmsItem) {
        val amount = amt(c, item.amountPaise)
        val flags = item.flags.split(',')
        val person = flags.firstOrNull { it.startsWith("PERSON:") }
        val app = c.applicationContext as? App
        val title = when {
            item.status == SmsStatus.LOOK -> "New money SMS — needs a look"
            item.kind == "TRANSFER" -> "Moved $amount between your accounts"
            item.debit -> "Spent $amount" + (item.merchant.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            else -> "Received $amount" + (item.merchant.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        }
        val ask = item.status == SmsStatus.PENDING && item.debit && "ASK" in flags && app?.prefs?.now?.askItems != false
        val text = when {
            item.accountId == null && item.status == SmsStatus.PENDING -> "Choose the account to save it"
            person != null -> if (item.debit) "You owe this person — settle it against their udhar?" else "They owe you — settle it against their udhar?"
            ask -> "Tap OK to save it, or reply with what you bought (milk 60, bread 40)"
            else -> "Tap OK to save it to Bahi Khata"
        }
        val b = base(c, CH_SMS).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(c, "sms", smsId(item))).setGroup(GROUP_SMS)
        if (item.status == SmsStatus.PENDING && item.accountId != null) {
            b.addAction(0, "OK, save", action(c, item.id, SmsActionReceiver.OK))
            if (person != null) b.addAction(0, "Settle udhar", action(c, item.id, SmsActionReceiver.SETTLE))
            else b.addAction(0, "Ignore", action(c, item.id, SmsActionReceiver.IGNORE))
        }
        if (ask) {
            // typed straight into the notification; the reply needs a mutable PendingIntent (explicit, to our own receiver)
            val ri = androidx.core.app.RemoteInput.Builder(SmsActionReceiver.KEY_ITEMS).setLabel("Milk 60, bread 40…").build()
            val pi = PendingIntent.getBroadcast(c, (item.id % 100_000).toInt() * 4 + 3,
                Intent(c, SmsActionReceiver::class.java).setAction(SmsActionReceiver.ITEMS).putExtra("id", item.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            b.addAction(NotificationCompat.Action.Builder(0, "What did you buy?", pi).addRemoteInput(ri).setAllowGeneratedReplies(false).build())
        }
        if (item.status != SmsStatus.PENDING || item.accountId == null) b.addAction(0, "Edit", open(c, "sms", smsId(item) + 1))
        post(c, smsId(item), b)
    }

    private fun action(c: Context, id: Long, act: String) = PendingIntent.getBroadcast(c, (id % 100_000).toInt() * 4 + when (act) { SmsActionReceiver.OK -> 0; SmsActionReceiver.IGNORE -> 1; else -> 2 },
        Intent(c, SmsActionReceiver::class.java).setAction(act).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun smsSummary(c: Context, n: Int) = post(c, SUMMARY_ID, base(c, CH_SMS).setContentTitle("$n SMS ${if (n == 1) "entry needs" else "entries need"} your OK")
        .setContentText("Tap to review and save them").setContentIntent(open(c, "sms", SUMMARY_ID)))

    /** Keeps the "N entries need your OK" notification true: removed at 0, the number updated while it's showing. */
    fun refreshSummary(c: Context, n: Int) {
        if (n <= 0) { cancel(c, SUMMARY_ID); return }
        val showing = runCatching { c.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == SUMMARY_ID } }.getOrDefault(false)
        if (showing) smsSummary(c, n)
    }

    fun unusual(c: Context, item: SmsItem, why: List<String>) = post(c, 9400 + (item.id % 500).toInt(), base(c, CH_ALERT)
        .setContentTitle("Unusual payment: ${amt(c, item.amountPaise)}" + (item.merchant.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
        .setContentText("This looks different: " + why.joinToString(", ") + ". If it wasn't you, call your bank now.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("This looks different: " + why.joinToString(", ") + ". If it wasn't you, call your bank right away and block the card or UPI."))
        .setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(open(c, "sms", 9400 + (item.id % 500).toInt())))

    fun reminder(c: Context, r: Reminder, days: Long) {
        val whenTxt = when { days <= 0L -> "today"; days == 1L -> "tomorrow"; else -> "in $days days" }
        val (title, body) = when (r.kind) {
            RemindKind.RECHARGE -> "${r.title} ends $whenTxt" to "Recharge before ${Dates.dayMonth(java.time.LocalDate.ofEpochDay(r.dueEpochDay))} so the service doesn't stop"
            RemindKind.UTILITY -> "${r.title} due $whenTxt" to ((if (r.amountPaise > 0) "Amount ${amt(c, r.amountPaise)} · " else "") + "pay by ${Dates.dayMonth(java.time.LocalDate.ofEpochDay(r.dueEpochDay))}")
            else -> "${r.title} will be debited $whenTxt" to ((if (r.amountPaise > 0) amt(c, r.amountPaise) + " · " else "") + "keep enough balance in the account")
        }
        post(c, 9600 + (r.id % 300).toInt(), base(c, CH_BILLS).setContentTitle(title).setContentText(body).setContentIntent(open(c, "reminders", 9600 + (r.id % 300).toInt())))
    }

    fun daily(c: Context) = post(c, 9002, base(c, CH_REMIND).setContentTitle("Anything spent today?")
        .setContentText("Nothing added to Bahi Khata today — add it now so balances stay right").setContentIntent(open(c, "add", 9002)))

    fun due(c: Context, card: Account, bill: Long, due: LocalDate, days: Long) = post(c, 9100 + card.id.toInt() % 800,
        base(c, CH_DUE).setContentTitle(if (days == 0L) "${card.name} bill is due today" else "${card.name} bill due in $days ${if (days == 1L) "day" else "days"}")
            .setContentText("Bill ${amt(c, bill)} · due ${Dates.dayMonth(due)}").setContentIntent(open(c, "account/${card.id}", 9100 + card.id.toInt() % 800)))

    fun budget(c: Context, name: String, spent: Long, limit: Long) = post(c, 9300 + name.hashCode() % 500, base(c, CH_BUDGET)
        .setContentTitle(if (spent >= limit) "$name budget crossed" else "$name budget at ${spent * 100 / limit}%")
        .setContentText("${amt(c, spent)} of ${amt(c, limit)} this month").setContentIntent(open(c, "budgets", 9300)))

    fun recurringAdded(c: Context, n: Int) = post(c, 9004, base(c, CH_REMIND).setContentTitle("$n repeating ${if (n == 1) "entry" else "entries"} added")
        .setContentText("Rent, EMI or subscriptions were added for today").setContentIntent(open(c, "entries", 9004)))

    fun backupProblem(c: Context, msg: String) = post(c, 9005, base(c, CH_BACKUP).setContentTitle("Drive backup didn't run").setContentText(msg).setContentIntent(open(c, "backup", 9005)))
}

/** "OK, save" / "Ignore" / "Settle udhar" buttons and the "What did you buy?" reply on an SMS notification. */
class SmsActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        if (!app.ready) return
        val id = intent.getLongExtra("id", -1)
        val typed = androidx.core.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_ITEMS)?.toString()?.trim()
        val pending = goAsync()
        app.appScope.launch {
            try {
                val item = app.repo.dao.smsItem(id) ?: return@launch
                if (item.status != SmsStatus.PENDING) { Notify.cancel(context, Notify.smsId(item)); return@launch }
                when (intent.action) {
                    OK -> item.accountId?.let { app.repo.acceptSms(item, it, item.categoryId, item.merchant); Diagnostics.count(context, "sms_ok_from_notification") }
                    IGNORE -> app.repo.ignoreSms(item)
                    SETTLE -> item.accountId?.let { app.repo.acceptSms(item, it, item.categoryId, item.merchant, kind = "UDHAR"); Diagnostics.count(context, "udhar_settled_from_sms") }
                    ITEMS -> {
                        val items = Insights.parseItems(typed.orEmpty())
                        val withItems = item.copy(itemsText = Insights.itemsText(items))
                        app.repo.setSmsItems(item, withItems.itemsText)
                        // answering means the payment is real: save it now when the account is known
                        val acc = item.accountId
                        if (acc != null) app.repo.acceptSms(withItems, acc, item.categoryId, item.merchant)
                        else Notify.cancel(context, Notify.smsId(item))
                        Diagnostics.count(context, "items_from_notification")
                    }
                }
            } finally { pending.finish() }
        }
    }
    companion object {
        const val OK = "com.sushant.bahikhata.SMS_OK"; const val IGNORE = "com.sushant.bahikhata.SMS_IGNORE"
        const val SETTLE = "com.sushant.bahikhata.SMS_SETTLE"; const val ITEMS = "com.sushant.bahikhata.SMS_ITEMS"; const val KEY_ITEMS = "items"
    }
}

object Workers {
    fun schedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build())
        scheduleReminder(c)
        scheduleDrive(c)
    }

    fun runNow(c: Context) = WorkManager.getInstance(c).enqueueUniqueWork("daily_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DailyWorker>().build())

    /** One-shot work at the chosen reminder time; it re-schedules itself after running. */
    fun scheduleReminder(c: Context) {
        val s = (c.applicationContext as App).prefs.now
        val wm = WorkManager.getInstance(c)
        if (!s.dailyReminderOn) { wm.cancelUniqueWork("reminder"); return }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(s.dailyReminderMin / 60, s.dailyReminderMin % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val delay = java.time.Duration.between(now, at).toMillis()
        wm.enqueueUniqueWork("reminder", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    fun scheduleDrive(c: Context) {
        val s = (c.applicationContext as App).prefs.now
        val wm = WorkManager.getInstance(c)
        if (!s.driveOn) { wm.cancelUniqueWork("drive"); return }
        val cons = Constraints.Builder().setRequiredNetworkType(if (s.driveWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        val hours = if (s.driveFreq == "WEEKLY") 24L * 7 else 24L
        wm.enqueueUniquePeriodicWork("drive", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveWorker>(hours, TimeUnit.HOURS).setConstraints(cons).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build())
    }

    fun driveNow(c: Context) = WorkManager.getInstance(c).enqueueUniqueWork("drive_now", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<DriveWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
}

/** Every ~6 h: repeating entries, card due reminders, budget alerts and the SMS catch-up scan. */
class DailyWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (!app.ready) return Result.retry()
        val repo = app.repo
        val s = app.prefs.now
        runCatching { val n = repo.runRecurring(); if (n > 0) Notify.recurringAdded(applicationContext, n) }
        runCatching { app.sms.scanSince(null, notify = true) }
        runCatching { app.sms.historyScan() }
        val accounts = repo.dao.accountsNow(); val entries = repo.dao.entriesNow()
        val nets = Ledger.nets(accounts, entries)
        val today = Dates.today()
        if (s.dueRemindersOn) runCatching {
            val days = s.dueReminderDays.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
            var done = app.prefs.dueNotified
            val smsPaid = repo.dao.smsNow().filter { it.kind == "CARD_PAYMENT" && it.status == SmsStatus.PENDING }
            accounts.filter { it.type == AccType.CARD && !it.archived }.forEach { card ->
                val bill = Ledger.cardBill(card, nets[card.id] ?: 0, entries, today)
                val due = bill.due ?: return@forEach
                if (bill.bill <= 0) return@forEach
                // a payment SMS for this card is waiting for his OK: the bill is already paid, don't nag
                val since = bill.cycleStart?.let { Dates.startOf(it) } ?: 0L
                if (smsPaid.filter { it.accountId == card.id && it.receivedAt >= since }.sumOf { it.amountPaise } >= bill.bill) return@forEach
                val left = java.time.temporal.ChronoUnit.DAYS.between(today, due)
                val tag = "${card.id}:${due.toEpochDay()}:$left"
                if (left in days && !done.contains(tag)) { Notify.due(applicationContext, card, bill.bill, due, left); done = (done.split('|').takeLast(40) + tag).joinToString("|") }
            }
            app.prefs.dueNotified = done
        }
        if (s.budgetAlertsOn) runCatching {
            val r = Dates.cycleRange(Dates.today(), s.monthStartDay); val ym = Dates.cycleStart(Dates.today(), s.monthStartDay)
            val cats = repo.dao.categoriesNow().associateBy { it.id }
            val byCat = Ledger.byCategory(entries, r)
            var alerted = app.prefs.budgetAlerted
            repo.dao.budgetsNow().forEach { b ->
                val spent = if (b.categoryId == null) Ledger.spent(entries, r) else byCat[b.categoryId] ?: 0L
                if (b.limitPaise <= 0) return@forEach
                val level = when { spent >= b.limitPaise -> 100; spent * 100 >= b.limitPaise * 80 -> 80; else -> 0 }
                val tag = "$ym:${b.id}:$level"
                if (level > 0 && !alerted.contains(tag)) {
                    Notify.budget(applicationContext, if (b.categoryId == null) "Overall" else cats[b.categoryId]?.name ?: "Budget", spent, b.limitPaise)
                    alerted = (alerted.split('|').takeLast(40) + tag).joinToString("|")
                }
            }
            app.prefs.budgetAlerted = alerted
        }
        if (s.remindersOn) runCatching {
            var done = app.prefs.remindNotified
            repo.dao.remindersNow().filter { !it.done }.forEach { r ->
                val left = Insights.daysLeft(r, today)
                if (left < -2) { repo.setReminderDone(r, true); return@forEach }   // well past: tidy away
                val days = if (r.kind == RemindKind.UTILITY) setOf(2L, 0L) else setOf(1L, 0L)
                val tag = "${r.id}:$left"
                if (left in days && !done.contains(tag)) { Notify.reminder(applicationContext, r, left); done = (done.split('|').takeLast(60) + tag).joinToString("|") }
            }
            app.prefs.remindNotified = done
        }
        app.prefs.lastDailyRun = System.currentTimeMillis()
        return Result.success()
    }
}

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (!app.ready) return Result.retry()
        if (app.prefs.now.dailyReminderOn) {
            val r = Dates.startOf(Dates.today())..Dates.endOf(Dates.today())
            val any = app.repo.dao.entriesNow().any { it.createdAt in r || it.time in r }
            if (!any) Notify.daily(applicationContext)
        }
        Workers.scheduleReminder(applicationContext)
        return Result.success()
    }
}

class DriveWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (!app.ready) return Result.retry()
        if (!app.prefs.now.driveOn) return Result.success()
        return when (val r = Drive.backup(applicationContext)) {
            is Drive.Res.Ok -> Result.success()
            is Drive.Res.NeedsSignIn -> { Notify.backupProblem(applicationContext, "Open Bahi Khata → Backup and sign in to Google Drive again"); Result.success() }
            is Drive.Res.Fail -> if (runAttemptCount < 3) Result.retry() else { Notify.backupProblem(applicationContext, r.msg); Result.success() }
        }
    }
}

/** WorkManager keeps its schedule across reboots; this also runs the SMS catch-up right after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Workers.runNow(context)
    }
}
