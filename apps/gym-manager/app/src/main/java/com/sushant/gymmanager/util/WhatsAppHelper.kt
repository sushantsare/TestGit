package com.sushant.gymmanager.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

data class DuePeriod(val start: String, val end: String, val amount: Double)

object WhatsAppHelper {

    /** Keeps digits only and adds India's 91 prefix to local numbers. */
    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
            else -> digits
        }
    }

    fun buildReminderMessage(memberName: String, gymName: String, periods: List<DuePeriod>): String {
        val total = periods.sumOf { it.amount }
        val lines = periods.joinToString("\n") {
            "• ${DateUtils.formatShort(it.start)} – ${DateUtils.formatShort(it.end)}: ${Money.format(it.amount)}"
        }
        return buildString {
            append("Hello $memberName,\n\n")
            append("This is a friendly reminder from $gymName regarding your pending membership fee.\n\n")
            if (periods.isNotEmpty()) {
                append("Outstanding billing period(s):\n")
                append(lines)
                append("\n\n")
            }
            append("Total due: ${Money.format(total)}\n\n")
            append("Please clear the dues at your earliest convenience. Thank you and keep training hard! 💪")
        }
    }

    fun generateWhatsAppUrl(phone: String, message: String? = null): String {
        val number = normalizePhone(phone)
        if (message.isNullOrEmpty()) return "https://wa.me/$number"
        val encoded = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        return "https://wa.me/$number?text=$encoded"
    }

    fun open(context: Context, phone: String, message: String? = null) {
        if (normalizePhone(phone).length < 10) {
            Toast.makeText(context, "Invalid WhatsApp number", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(generateWhatsAppUrl(phone, message)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp or a browser is required", Toast.LENGTH_SHORT).show()
        }
    }
}
