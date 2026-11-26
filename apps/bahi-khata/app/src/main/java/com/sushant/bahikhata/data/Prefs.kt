package com.sushant.bahikhata.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val setupDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val hideAmounts: Boolean = false,
    val lockOn: Boolean = false,
    val lockAfterSec: Int = 60,
    val secureScreens: Boolean = true,
    val smsOn: Boolean = false,
    val dailyReminderOn: Boolean = false,
    val dailyReminderMin: Int = 21 * 60,
    val dueRemindersOn: Boolean = false,
    val dueReminderDays: String = "5,2,0",
    val budgetAlertsOn: Boolean = true,
    val driveOn: Boolean = false,
    val driveAccount: String = "",
    val driveFreq: String = "DAILY",
    val driveWifiOnly: Boolean = true,
    val lastDriveBackup: Long = 0,
    val lastFileBackup: Long = 0,
    val reminderLang: String = "en",
    val monthStartDay: Int = 1,
    val lastAccountId: Long = 0,
    val hideAskAuth: Boolean = true,                  // fingerprint / phone PIN needed to show hidden amounts
    val hideAreas: String = HideArea.ALL,             // which parts of the app "Hide amounts" covers
    val alertsOn: Boolean = true,                     // unusual-debit alerts
    val remindersOn: Boolean = true,                  // recharge, bill, EMI and AutoPay reminders from SMS
    val askItems: Boolean = true,                     // "What did you buy?" after a payment
) {
    fun hides(area: String) = hideAmounts && area in hideAreas.split(',')
}

/** Parts of the app the Hide amounts setting can cover, each with its own switch. */
object HideArea {
    const val HOME = "HOME"; const val ENTRIES = "ENTRIES"; const val ACCOUNTS = "ACCOUNTS"; const val UDHAR = "UDHAR"; const val REPORTS = "REPORTS"; const val NOTIF = "NOTIF"
    const val ALL = "HOME,ENTRIES,ACCOUNTS,UDHAR,REPORTS,NOTIF"
    val all = listOf(HOME to "Home summary", ENTRIES to "Entries", ACCOUNTS to "Accounts & cards", UDHAR to "Udhar", REPORTS to "Reports & budgets", NOTIF to "Notifications")
}

/** Settings live in SharedPreferences (included in backup file); "already asked" flags live in a separate file excluded from backups. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val local: SharedPreferences = context.getSharedPreferences("device_local", Context.MODE_PRIVATE)
    // read once at start-up on purpose: theme, app lock and screenshot blocking must be known before the first frame
    private val _s = MutableStateFlow(android.os.StrictMode.allowThreadDiskReads().let { old -> try { read() } finally { android.os.StrictMode.setThreadPolicy(old) } })
    val settings: StateFlow<Settings> = _s.asStateFlow()
    val now: Settings get() = _s.value

    private fun read() = Settings(
        setupDone = sp.getBoolean("setupDone", false),
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        pureBlack = sp.getBoolean("pureBlack", false),
        hideAmounts = sp.getBoolean("hideAmounts", false),
        lockOn = sp.getBoolean("lockOn", false),
        lockAfterSec = sp.getInt("lockAfterSec", 60),
        secureScreens = sp.getBoolean("secureScreens", true),
        smsOn = sp.getBoolean("smsOn", false),
        dailyReminderOn = sp.getBoolean("dailyReminderOn", false),
        dailyReminderMin = sp.getInt("dailyReminderMin", 21 * 60),
        dueRemindersOn = sp.getBoolean("dueRemindersOn", false),
        dueReminderDays = sp.getString("dueReminderDays", "5,2,0")!!,
        budgetAlertsOn = sp.getBoolean("budgetAlertsOn", true),
        driveOn = sp.getBoolean("driveOn", false),
        driveAccount = sp.getString("driveAccount", "")!!,
        driveFreq = sp.getString("driveFreq", "DAILY")!!,
        driveWifiOnly = sp.getBoolean("driveWifiOnly", true),
        lastDriveBackup = sp.getLong("lastDriveBackup", 0),
        lastFileBackup = sp.getLong("lastFileBackup", 0),
        reminderLang = sp.getString("reminderLang", "en")!!,
        monthStartDay = sp.getInt("monthStartDay", 1),
        lastAccountId = sp.getLong("lastAccountId", 0),
        hideAskAuth = sp.getBoolean("hideAskAuth", true),
        hideAreas = sp.getString("hideAreas", HideArea.ALL)!!,
        alertsOn = sp.getBoolean("alertsOn", true),
        remindersOn = sp.getBoolean("remindersOn", true),
        askItems = sp.getBoolean("askItems", true),
    )

    fun update(block: (Settings) -> Settings) {
        val n = block(_s.value)
        sp.edit()
            .putBoolean("setupDone", n.setupDone).putString("theme", n.theme.name).putBoolean("pureBlack", n.pureBlack)
            .putBoolean("hideAmounts", n.hideAmounts).putBoolean("lockOn", n.lockOn).putInt("lockAfterSec", n.lockAfterSec)
            .putBoolean("secureScreens", n.secureScreens).putBoolean("smsOn", n.smsOn)
            .putBoolean("dailyReminderOn", n.dailyReminderOn).putInt("dailyReminderMin", n.dailyReminderMin)
            .putBoolean("dueRemindersOn", n.dueRemindersOn).putString("dueReminderDays", n.dueReminderDays)
            .putBoolean("budgetAlertsOn", n.budgetAlertsOn)
            .putBoolean("driveOn", n.driveOn).putString("driveAccount", n.driveAccount).putString("driveFreq", n.driveFreq)
            .putBoolean("driveWifiOnly", n.driveWifiOnly).putLong("lastDriveBackup", n.lastDriveBackup)
            .putLong("lastFileBackup", n.lastFileBackup).putString("reminderLang", n.reminderLang)
            .putInt("monthStartDay", n.monthStartDay).putLong("lastAccountId", n.lastAccountId)
            .putBoolean("hideAskAuth", n.hideAskAuth).putString("hideAreas", n.hideAreas).putBoolean("alertsOn", n.alertsOn)
            .putBoolean("remindersOn", n.remindersOn).putBoolean("askItems", n.askItems)
            .apply()
        _s.value = n
    }

    fun reload() { _s.value = read() }

    // device-local (never backed up)
    var lastSmsScan: Long
        get() = local.getLong("lastSmsScan", 0)
        set(v) = local.edit().putLong("lastSmsScan", v).apply()
    var askedSms: Boolean
        get() = local.getBoolean("askedSms", false)
        set(v) = local.edit().putBoolean("askedSms", v).apply()
    var askedNotif: Boolean
        get() = local.getBoolean("askedNotif", false)
        set(v) = local.edit().putBoolean("askedNotif", v).apply()
    var batteryTipDue: Boolean
        get() = local.getBoolean("batteryTipDue", false)
        set(v) = local.edit().putBoolean("batteryTipDue", v).apply()

    var batteryTipShown: Boolean
        get() = local.getBoolean("batteryTipShown", false)
        set(v) = local.edit().putBoolean("batteryTipShown", v).apply()
    var budgetAlerted: String
        get() = local.getString("budgetAlerted", "")!!
        set(v) = local.edit().putString("budgetAlerted", v).apply()
    var lastDailyRun: Long
        get() = local.getLong("lastDailyRun", 0)
        set(v) = local.edit().putLong("lastDailyRun", v).apply()
    var historyDone: Boolean
        get() = local.getBoolean("historyDone", false)
        set(v) = local.edit().putBoolean("historyDone", v).apply()
    var remindNotified: String
        get() = local.getString("remindNotified", "")!!
        set(v) = local.edit().putString("remindNotified", v).apply()
    var dueNotified: String
        get() = local.getString("dueNotified", "")!!
        set(v) = local.edit().putString("dueNotified", v).apply()

    fun exportMap(): Map<String, String> = sp.all.mapValues { "${it.value!!::class.simpleName}:${it.value}" }
    fun importMap(m: Map<String, String>) {
        val e = sp.edit().clear()
        m.forEach { (k, v) ->
            val type = v.substringBefore(":"); val value = v.substringAfter(":")
            when (type) {
                "Boolean" -> e.putBoolean(k, value.toBoolean())
                "Int" -> e.putInt(k, value.toIntOrNull() ?: 0)
                "Long" -> e.putLong(k, value.toLongOrNull() ?: 0)
                else -> e.putString(k, value)
            }
        }
        e.apply(); reload()
    }
}
