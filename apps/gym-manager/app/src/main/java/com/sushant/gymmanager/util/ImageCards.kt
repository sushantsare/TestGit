package com.sushant.gymmanager.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.MembershipEntity
import com.sushant.gymmanager.data.repository.GymRepository
import com.sushant.gymmanager.data.repository.ReceiptData
import com.sushant.gymmanager.i18n.I18n
import java.io.File

private const val ORANGE = 0xFFEA580C.toInt()
private const val ORANGE_DARK = 0xFFC2410C.toInt()
private const val INK = 0xFF0F172A.toInt()
private const val MUTED = 0xFF64748B.toInt()
private const val LINE = 0xFFE2E8F0.toInt()
private const val GREEN = 0xFF16A34A.toInt()
private const val RED = 0xFFDC2626.toInt()

private fun paint(size: Float, color: Int = INK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = align
    }

private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
    if (p.measureText(text) <= maxWidth) return text
    var t = text
    while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
    return "$t…"
}

object ReceiptRenderer {
    fun render(d: ReceiptData, lang: String): Bitmap {
        fun tr(s: String, vararg a: Any) = I18n.tr(lang, s, *a)
        val w = 1080
        val rowsH = d.lines.size * 70
        val h = 1180 + rowsH
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        // Header band
        val band = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), 260f, ORANGE, ORANGE_DARK, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), 260f, band)
        c.drawText(ellipsize(d.settings.gymName, paint(58f, Color.WHITE, true), 960f), 60f, 120f, paint(58f, Color.WHITE, true))
        val contact = listOf(d.settings.gymAddress, d.settings.gymPhone).filter { it.isNotBlank() }.joinToString("  •  ")
        if (contact.isNotBlank()) c.drawText(ellipsize(contact, paint(30f, Color.WHITE), 960f), 60f, 175f, paint(30f, 0xE6FFFFFF.toInt()))
        c.drawText(tr("PAYMENT RECEIPT"), 60f, 228f, paint(28f, 0xCCFFFFFF.toInt(), true))

        var y = 340f
        c.drawText(tr("Receipt No."), 60f, y, paint(28f, MUTED))
        c.drawText(tr("Date"), 1020f, y, paint(28f, MUTED, align = Paint.Align.RIGHT))
        y += 46f
        c.drawText(d.receiptNo, 60f, y, paint(40f, INK, true))
        val date = DateUtils.parseDate(d.paidAt.take(10))?.let { DateUtils.formatFull(it.toString()) } ?: d.paidAt
        c.drawText(date + "  " + DateUtils.formatTo12Hour(d.paidAt.drop(11).take(5)), 1020f, y, paint(34f, INK, align = Paint.Align.RIGHT))

        y += 50f
        c.drawLine(60f, y, 1020f, y, Paint().apply { color = LINE; strokeWidth = 3f })
        y += 70f
        c.drawText(tr("Received from"), 60f, y, paint(28f, MUTED))
        y += 54f
        c.drawText(ellipsize(d.member.fullName, paint(48f, INK, true), 700f), 60f, y, paint(48f, INK, true))
        c.drawText(tr("Roll") + " #" + d.member.rollNumber, 1020f, y, paint(34f, ORANGE, true, Paint.Align.RIGHT))
        y += 46f
        c.drawText("+" + WhatsAppHelper.normalizePhone(d.member.whatsappNumber), 60f, y, paint(30f, MUTED))

        // Table
        y += 70f
        val head = RectF(60f, y - 44f, 1020f, y + 22f)
        c.drawRoundRect(head, 16f, 16f, Paint().apply { color = 0xFFFFF7ED.toInt() })
        c.drawText(tr("Membership period"), 84f, y, paint(28f, ORANGE_DARK, true))
        c.drawText(tr("Amount"), 996f, y, paint(28f, ORANGE_DARK, true, Paint.Align.RIGHT))
        y += 70f
        d.lines.forEach { (cycle, amt) ->
            c.drawText("${DateUtils.formatShort(cycle.cycleStart)} – ${DateUtils.formatShort(cycle.cycleEnd)}", 84f, y, paint(32f))
            c.drawText(Money.format(amt), 996f, y, paint(32f, INK, true, Paint.Align.RIGHT))
            y += 70f
        }
        c.drawLine(60f, y - 30f, 1020f, y - 30f, Paint().apply { color = LINE; strokeWidth = 2f })

        // Totals
        y += 40f
        c.drawText(tr("Total Paid"), 60f, y, paint(36f, INK, true))
        c.drawText(Money.format(d.total), 1020f, y, paint(56f, GREEN, true, Paint.Align.RIGHT))
        y += 64f
        c.drawText(tr("Payment mode"), 60f, y, paint(30f, MUTED))
        c.drawText(I18n.payModeLabel(lang, d.mode), 1020f, y, paint(30f, INK, true, Paint.Align.RIGHT))
        y += 56f
        c.drawText(tr("Balance due"), 60f, y, paint(30f, MUTED))
        if (d.balanceDue > 0.005) c.drawText(Money.format(d.balanceDue), 1020f, y, paint(30f, RED, true, Paint.Align.RIGHT))
        else c.drawText(tr("No dues"), 1020f, y, paint(30f, GREEN, true, Paint.Align.RIGHT))

        // PAID stamp only when nothing is left to pay
        if (d.balanceDue <= 0.005) {
            val stamp = paint(60f, 0x4016A34A, true, Paint.Align.CENTER)
            c.save(); c.rotate(-14f, 560f, y - 140f); c.drawText(tr("PAID"), 560f, y - 120f, stamp); c.restore()
        }

        y += 110f
        c.drawText(tr("Thank you! Keep training hard."), 540f, y, paint(34f, INK, true, Paint.Align.CENTER))
        y += 48f
        c.drawText(tr("Computer-generated receipt • Gym Manager"), 540f, y, paint(24f, MUTED, align = Paint.Align.CENTER))
        return bmp
    }
}

object IdCardRenderer {
    fun qr(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (yy in 0 until size) for (xx in 0 until size) pixels[yy * size + xx] = if (matrix[xx, yy]) INK else Color.WHITE
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return bmp
    }

    fun render(member: MemberEntity, plan: MembershipEntity?, settings: GymSettingsEntity, lang: String): Bitmap {
        fun tr(s: String) = I18n.tr(lang, s)
        val w = 1200
        val h = 760
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val card = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        // Header
        c.save()
        c.clipRect(0f, 0f, w.toFloat(), 170f)
        c.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 170f, ORANGE, ORANGE_DARK, Shader.TileMode.CLAMP)
        })
        c.restore()
        c.drawText(ellipsize(settings.gymName, paint(54f, Color.WHITE, true), 800f), 56f, 96f, paint(54f, Color.WHITE, true))
        c.drawText(tr("MEMBER ID CARD"), 56f, 140f, paint(26f, 0xDDFFFFFF.toInt(), true))

        // Photo
        val cx = 170f; val cy = 360f; val r = 110f
        val photo = member.photoUri?.let { p -> runCatching { BitmapFactory.decodeFile(p) }.getOrNull() }
        if (photo != null) {
            val scale = (2 * r) / minOf(photo.width, photo.height)
            val m = android.graphics.Matrix().apply {
                postScale(scale, scale)
                postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
            }
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFEDD5.toInt() })
            val initials = member.fullName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            c.drawText(initials, cx, cy + 30f, paint(84f, 0xFF7C2D12.toInt(), true, Paint.Align.CENTER))
        }
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = ORANGE })

        // Details
        val x = 330f
        c.drawText(ellipsize(member.fullName, paint(50f, INK, true), 470f), x, 290f, paint(50f, INK, true))
        val badge = RectF(x, 316f, x + 170f, 372f)
        c.drawRoundRect(badge, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFEDD5.toInt() })
        c.drawText("#" + member.rollNumber, x + 85f, 356f, paint(32f, 0xFF7C2D12.toInt(), true, Paint.Align.CENTER))
        c.drawText(I18n.shiftLabel(lang, member.shift), x + 190f, 356f, paint(28f, ORANGE, true))
        c.drawText(DateUtils.formatTimeSlot(member.fromTime, member.toTime), x, 420f, paint(32f, MUTED))
        if (plan != null) {
            c.drawText(tr("Valid till") + ": " + DateUtils.formatShort(plan.expiryDate), x, 470f, paint(32f, INK, true))
        }
        c.drawText("+" + WhatsAppHelper.normalizePhone(member.whatsappNumber), x, 520f, paint(30f, MUTED))

        // QR
        val q = qr(GymRepository.qrContent(member.rollNumber), 300)
        c.drawBitmap(q, 860f, 220f, null)
        c.drawText(tr("Scan to check in"), 1010f, 560f, paint(24f, MUTED, align = Paint.Align.CENTER))

        // Footer
        c.drawLine(56f, 640f, 1144f, 640f, Paint().apply { color = LINE; strokeWidth = 2f })
        val contact = listOf(settings.gymAddress, settings.gymPhone).filter { it.isNotBlank() }.joinToString("  •  ")
        c.drawText(ellipsize(contact.ifBlank { tr("Show this card at the front desk") }, paint(26f, MUTED), 1080f), 56f, 700f, paint(26f, MUTED))
        return bmp
    }
}

object ShareUtil {
    fun saveToCache(context: Context, bmp: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** Shares an image, going straight to the member's WhatsApp chat when possible. */
    fun shareImage(context: Context, file: File, text: String, phone: String? = null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val wa = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { isInstalled(context, it) }
        if (wa != null && !phone.isNullOrBlank()) {
            val direct = Intent(base).apply {
                setPackage(wa)
                putExtra("jid", WhatsAppHelper.normalizePhone(phone) + "@s.whatsapp.net")
            }
            try {
                context.startActivity(direct); return
            } catch (_: ActivityNotFoundException) {
            }
        }
        try {
            context.startActivity(Intent.createChooser(base, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, file: File, mime: String, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Plain-text share sheet (e.g. today's collection summary). */
    fun shareText(context: Context, text: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        try {
            context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
