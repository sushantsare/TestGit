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
)

/** Settings live in SharedPreferences (included in backup file); "already asked" flags live in a separate file excluded from backups. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val local: SharedPreferences = context.getSharedPreferences("device_local", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _s.asStateFlow()
    val now: Settings get() = _s.value

    private fun read() = Settings(
        setupDone = sp.getBoolean("setupDone", false),
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
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
    )

    fun update(block: (Settings) -> Settings) {
        val n = block(_s.value)
        sp.edit()
            .putBoolean("setupDone", n.setupDone).putString("theme", n.theme.name)
            .putBoolean("hideAmounts", n.hideAmounts).putBoolean("lockOn", n.lockOn).putInt("lockAfterSec", n.lockAfterSec)
            .putBoolean("secureScreens", n.secureScreens).putBoolean("smsOn", n.smsOn)
            .putBoolean("dailyReminderOn", n.dailyReminderOn).putInt("dailyReminderMin", n.dailyReminderMin)
            .putBoolean("dueRemindersOn", n.dueRemindersOn).putString("dueReminderDays", n.dueReminderDays)
            .putBoolean("budgetAlertsOn", n.budgetAlertsOn)
            .putBoolean("driveOn", n.driveOn).putString("driveAccount", n.driveAccount).putString("driveFreq", n.driveFreq)
            .putBoolean("driveWifiOnly", n.driveWifiOnly).putLong("lastDriveBackup", n.lastDriveBackup)
            .putLong("lastFileBackup", n.lastFileBackup).putString("reminderLang", n.reminderLang)
            .putInt("monthStartDay", n.monthStartDay).putLong("lastAccountId", n.lastAccountId)
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
    var batteryTipShown: Boolean
        get() = local.getBoolean("batteryTipShown", false)
        set(v) = local.edit().putBoolean("batteryTipShown", v).apply()
    var budgetAlerted: String
        get() = local.getString("budgetAlerted", "")!!
        set(v) = local.edit().putString("budgetAlerted", v).apply()
    var lastDailyRun: Long
        get() = local.getLong("lastDailyRun", 0)
        set(v) = local.edit().putLong("lastDailyRun", v).apply()
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
        e.commit(); reload()
    }
}
