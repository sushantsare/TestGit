package com.sushant.gymmanager.ui.payments

import android.content.Context
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.util.IdCardRenderer
import com.sushant.gymmanager.util.ReceiptRenderer
import com.sushant.gymmanager.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShareActions {
    suspend fun shareReceipt(context: Context, receiptNo: String) {
        val app = context.applicationContext as GymApp
        val data = app.repository.receipt(receiptNo) ?: return
        val lang = data.settings.language
        val file = withContext(Dispatchers.Default) {
            ShareUtil.saveToCache(context, ReceiptRenderer.render(data, lang), "Receipt_$receiptNo.png")
        }
        val text = I18n.tr(lang, "Payment receipt %s from %s. Thank you!", receiptNo, data.settings.gymName)
        ShareUtil.shareImage(context, file, text, data.member.whatsappNumber)
    }

    suspend fun shareIdCard(context: Context, memberId: Long) {
        val app = context.applicationContext as GymApp
        val repo = app.repository
        val member = repo.getMember(memberId) ?: return
        val plan = repo.getActiveMembership(memberId)
        val settings = repo.getSettings()
        val file = withContext(Dispatchers.Default) {
            ShareUtil.saveToCache(context, IdCardRenderer.render(member, plan, settings, settings.language), "IDCard_${member.rollNumber}.png")
        }
        val text = I18n.tr(settings.language, "Your %s member ID card. Show the QR code at the desk to check in.", settings.gymName)
        ShareUtil.shareImage(context, file, text, member.whatsappNumber)
    }
}
