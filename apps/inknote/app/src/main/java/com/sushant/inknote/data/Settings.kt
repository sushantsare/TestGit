package com.sushant.inknote.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeMode { const val SYSTEM = 0; const val LIGHT = 1; const val DARK = 2 }
object SortBy { const val MODIFIED = 0; const val CREATED = 1; const val TITLE = 2 }

data class AppSettings(
    val stylusOnly: Boolean = false,
    val stylusSeen: Boolean = false,
    val shapeHold: Boolean = true,
    val defaultPaper: String = Paper.LINED,
    val defaultOrientation: String = Orientation.PORTRAIT,
    val defaultPageType: String = PageType.PAGES,
    val sortBy: Int = SortBy.MODIFIED,
    val gridView: Boolean = true,
    val autoEmptyTrash: Boolean = true,
    val biometric: Boolean = true,
    val theme: Int = ThemeMode.SYSTEM,
    val lastBackupAt: Long = 0,
    val lastBackupSize: Long = 0,
    val penColors: List<Int> = DEFAULT_COLORS,
    val savedPens: String = "",
) {
    companion object {
        val DEFAULT_COLORS = listOf(0xFF1F3F9A.toInt(), 0xFF1C1B1F.toInt(), 0xFFC0392B.toInt(), 0xFF0E8A6B.toInt(), 0xFFE67E22.toInt())
    }
}

/** Plain preferences (part of the user's backup). PIN and "asked" flags live in [SecurePrefs], which is excluded from Android backup. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        stylusOnly = p.getBoolean("stylusOnly", false),
        stylusSeen = p.getBoolean("stylusSeen", false),
        shapeHold = p.getBoolean("shapeHold", true),
        defaultPaper = p.getString("defaultPaper", Paper.LINED) ?: Paper.LINED,
        defaultOrientation = p.getString("defaultOrientation", Orientation.PORTRAIT) ?: Orientation.PORTRAIT,
        defaultPageType = p.getString("defaultPageType", PageType.PAGES) ?: PageType.PAGES,
        sortBy = p.getInt("sortBy", SortBy.MODIFIED),
        gridView = p.getBoolean("gridView", true),
        autoEmptyTrash = p.getBoolean("autoEmptyTrash", true),
        biometric = p.getBoolean("biometric", true),
        theme = p.getInt("theme", ThemeMode.SYSTEM),
        lastBackupAt = p.getLong("lastBackupAt", 0),
        lastBackupSize = p.getLong("lastBackupSize", 0),
        savedPens = p.getString("savedPens", "") ?: "",
    )

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_state.value)
        p.edit()
            .putBoolean("stylusOnly", n.stylusOnly).putBoolean("stylusSeen", n.stylusSeen).putBoolean("shapeHold", n.shapeHold)
            .putString("defaultPaper", n.defaultPaper).putString("defaultOrientation", n.defaultOrientation)
            .putString("defaultPageType", n.defaultPageType).putInt("sortBy", n.sortBy).putBoolean("gridView", n.gridView)
            .putBoolean("autoEmptyTrash", n.autoEmptyTrash).putBoolean("biometric", n.biometric).putInt("theme", n.theme)
            .putLong("lastBackupAt", n.lastBackupAt).putLong("lastBackupSize", n.lastBackupSize).putString("savedPens", n.savedPens)
            .apply()
        _state.value = n
    }
}

/** PIN hash and salt. Excluded from Android backup / device transfer (see backup rules). */
class SecurePrefs(context: Context) {
    private val p = context.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private val _hasPin = MutableStateFlow(p.contains("pinHash"))
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()
    private val failKey = "fails"; private val untilKey = "lockedUntil"

    fun setPin(pin: String) {
        val salt = Crypto.newSalt()
        p.edit().putString("pinSalt", Crypto.hex(salt)).putString("pinHash", Crypto.hex(Crypto.pbkdf2(pin, salt))).apply()
        _hasPin.value = true
    }

    fun setPinHash(saltHex: String, hashHex: String) {
        p.edit().putString("pinSalt", saltHex).putString("pinHash", hashHex).apply(); _hasPin.value = true
    }

    fun pinSaltHex(): String? = p.getString("pinSalt", null)
    fun pinHashHex(): String? = p.getString("pinHash", null)

    fun checkPin(pin: String): Boolean {
        val salt = p.getString("pinSalt", null) ?: return false
        val ok = Crypto.hex(Crypto.pbkdf2(pin, Crypto.unhex(salt))) == p.getString("pinHash", null)
        if (ok) p.edit().putInt(failKey, 0).putLong(untilKey, 0).apply()
        else {
            val f = p.getInt(failKey, 0) + 1
            val e = p.edit().putInt(failKey, f)
            if (f % 5 == 0) e.putLong(untilKey, System.currentTimeMillis() + 30_000)
            e.apply()
        }
        return ok
    }

    fun waitMs(): Long = (p.getLong(untilKey, 0) - System.currentTimeMillis()).coerceAtLeast(0)
}
