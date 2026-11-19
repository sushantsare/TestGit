package com.sushant.gymmanager.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.local.Shift
import java.util.Locale

object Lang {
    const val EN = "en"
    const val HI = "hi"
}

val LocalLang = staticCompositionLocalOf { Lang.EN }

/**
 * English text is the key; Hindi translations live in [Hindi.map].
 * Templates use %s / %d placeholders.
 */
object I18n {
    fun tr(lang: String, en: String, vararg args: Any): String {
        val pattern = if (lang == Lang.HI) Hindi.map[en] ?: en else en
        return if (args.isEmpty()) pattern else String.format(Locale.ENGLISH, pattern, *args)
    }

    fun payModeLabel(lang: String, mode: String): String = tr(
        lang,
        when (mode) {
            PayMode.UPI -> "UPI"
            PayMode.CARD -> "Card"
            PayMode.BANK -> "Bank Transfer"
            else -> "Cash"
        }
    )

    fun shiftLabel(lang: String, shift: String): String = tr(lang, if (shift == Shift.MORNING) "MORNING" else "EVENING")

    fun planLabel(lang: String, planType: String, months: Int = 0, days: Int = 0): String = when (planType) {
        PlanType.ONE_MONTH -> tr(lang, "1 Month")
        PlanType.THREE_MONTHS -> tr(lang, "3 Months")
        PlanType.SIX_MONTHS -> tr(lang, "6 Months")
        PlanType.TWELVE_MONTHS -> tr(lang, "12 Months")
        else -> buildList {
            if (months > 0) add(tr(lang, if (months == 1) "%d Month" else "%d Months", months))
            if (days > 0) add(tr(lang, if (days == 1) "%d Day" else "%d Days", days))
        }.joinToString(" ").ifEmpty { tr(lang, "Custom") }
    }

    fun categoryLabel(lang: String, category: String): String = tr(lang, category)

    fun expiryText(lang: String, expiryDate: String): String {
        val days = com.sushant.gymmanager.util.DateUtils.daysUntil(expiryDate)
        return when {
            days < -1 -> tr(lang, "Expired %d days ago", -days)
            days == -1L -> tr(lang, "Expired yesterday")
            days == 0L -> tr(lang, "Expires today")
            days == 1L -> tr(lang, "Expires tomorrow")
            else -> tr(lang, "Expires in %d days", days)
        }
    }
}

@Composable
@ReadOnlyComposable
fun t(en: String, vararg args: Any): String = I18n.tr(LocalLang.current, en, *args)

@Composable
@ReadOnlyComposable
fun currentLang(): String = LocalLang.current
