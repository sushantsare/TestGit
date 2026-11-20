package com.sushant.inknote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sushant.inknote.InkApp
import com.sushant.inknote.data.NoteEntity
import com.sushant.inknote.ink.PagePalette
import com.sushant.inknote.ink.PageRenderer
import com.sushant.inknote.ui.theme.LocalInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Context.toast(msg: String) = ContextCompat.getMainExecutor(this).execute { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

fun Context.app() = applicationContext as InkApp

object Dates {
    fun short(t: Long): String {
        val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = t }
        return when {
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) -> "Today"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
            else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))
        }
    }
    fun full(t: Long): String = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(t))
    fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }
}

object ThumbCache {
    private val lock = Mutex()
    suspend fun load(app: InkApp, n: NoteEntity, dark: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        if (n.locked) return@withContext null
        lock.withLock {
            val name = "${n.id}_${n.thumbVersion}_${if (dark) "d" else "l"}.png"
            val f = File(app.files.thumbs, name)
            if (f.exists()) return@withLock BitmapFactory.decodeFile(f.path)
            val page = app.repo.pages(n.id).firstOrNull() ?: return@withLock null
            val c = app.repo.readPage(n.id, page.id)
            val bmp = runCatching { PageRenderer.bitmap(app.files, n.id, page, c, 420, if (dark) PagePalette.DARK else PagePalette.LIGHT) }.getOrNull() ?: return@withLock null
            app.files.thumbs.listFiles { x -> x.name.startsWith("${n.id}_") }?.forEach { it.delete() }
            runCatching { FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
            bmp
        }
    }
}

@Composable
fun NoteThumb(n: NoteEntity, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, n.id, n.thumbVersion, n.locked, ink.dark) { value = ThumbCache.load(ctx.app(), n, ink.dark) }
    Box(
        modifier.clip(RoundedCornerShape(11.dp)).background(if (n.locked) ink.surf2 else if (ink.dark) Color(0xFF1E2024) else Color.White)
            .border(1.dp, ink.divider, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (n.locked) Icon(Icons.Outlined.Lock, null, tint = ink.muted)
        else bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
    }
}

@Composable
fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
}
