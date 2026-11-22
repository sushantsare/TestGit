@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.MediaButtonReceiver
import com.sushant.tarang.MainActivity
import com.sushant.tarang.R
import com.sushant.tarang.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the widget shows; written by the playback service whenever the song or play state changes. */
object WidgetState {
    data class S(val title: String, val artist: String, val playing: Boolean, val hasSong: Boolean, val cover: Bitmap?)

    private fun coverFile(ctx: Context) = File(ctx.filesDir, "widget_cover.png")

    suspend fun publish(ctx: Context, song: Song?, playing: Boolean, cover: Bitmap?) = withContext(Dispatchers.IO) {
        val p = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
        p.edit().putString("title", song?.title ?: "").putString("artist", song?.artist ?: "")
            .putBoolean("playing", playing).putBoolean("hasSong", song != null).putBoolean("hasCover", cover != null).apply()
        if (cover != null) runCatching {
            val s = Bitmap.createScaledBitmap(cover, 256, 256 * cover.height / cover.width.coerceAtLeast(1), true)
            coverFile(ctx).outputStream().use { s.compress(Bitmap.CompressFormat.PNG, 95, it) }
        }
        runCatching { TarangWidget().updateAll(ctx) }
    }

    fun read(ctx: Context): S {
        val p = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val cover = if (p.getBoolean("hasCover", false)) runCatching { BitmapFactory.decodeFile(coverFile(ctx).path) }.getOrNull() else null
        return S(p.getString("title", "") ?: "", p.getString("artist", "") ?: "", p.getBoolean("playing", false), p.getBoolean("hasSong", false), cover)
    }
}

class TarangWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 80.dp)
        val BIG = DpSize(250.dp, 220.dp)
    }
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, BIG))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val st = WidgetState.read(context)
        provideContent { GlanceTheme { Content(context, st) } }
    }

    private fun key(ctx: Context, code: Int) = actionSendBroadcast(
        Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(ctx, MediaButtonReceiver::class.java))
            .setData(Uri.parse("tarang://key/$code"))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
    )

    @Composable
    private fun Content(ctx: Context, st: WidgetState.S) {
        val size = LocalSize.current
        val bg = GlanceTheme.colors.widgetBackground
        val fg = GlanceTheme.colors.onSurface
        val sub = GlanceTheme.colors.onSurfaceVariant
        val open = actionStartActivity(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true))
        val playIcon = if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = if (st.hasSong) st.title else "Tarang"
        val artist = if (st.hasSong) st.artist else "Tap to open your music"

        @Composable fun cover(sz: androidx.compose.ui.unit.Dp) {
            val img = st.cover?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.splash_icon)
            Image(img, contentDescription = "Album cover", modifier = GlanceModifier.size(sz).cornerRadius(sz / 4).clickable(open))
        }
        @Composable fun btn(icon: Int, desc: String, code: Int, big: Boolean = false) {
            Box(GlanceModifier.size(if (big) 52.dp else 44.dp).cornerRadius(26.dp)
                .background(if (big) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
                .clickable(key(ctx, code)), contentAlignment = Alignment.Center) {
                Image(ImageProvider(icon), contentDescription = desc, modifier = GlanceModifier.size(if (big) 26.dp else 22.dp),
                    colorFilter = ColorFilter.tint(if (big) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer))
            }
        }

        Box(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(bg).padding(12.dp)) {
            when {
                size.width < WIDE.width -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    cover(56.dp); Spacer(GlanceModifier.height(8.dp)); btn(playIcon, "Play or pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true)
                }
                size.height < BIG.height -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    cover(56.dp); Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight().clickable(open)) {
                        Text(title, maxLines = 1, style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        Text(artist, maxLines = 1, style = TextStyle(color = sub, fontSize = 12.sp))
                    }
                    btn(android.R.drawable.ic_media_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    Spacer(GlanceModifier.width(4.dp)); btn(playIcon, "Play or pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true)
                    Spacer(GlanceModifier.width(4.dp)); btn(android.R.drawable.ic_media_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT)
                }
                else -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    cover(110.dp); Spacer(GlanceModifier.height(8.dp))
                    Text(title, maxLines = 1, style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.clickable(open))
                    Text(artist, maxLines = 1, style = TextStyle(color = sub, fontSize = 13.sp))
                    Spacer(GlanceModifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        btn(android.R.drawable.ic_media_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS); Spacer(GlanceModifier.width(14.dp))
                        btn(playIcon, "Play or pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true); Spacer(GlanceModifier.width(14.dp))
                        btn(android.R.drawable.ic_media_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT)
                    }
                }
            }
        }
    }
}

class TarangWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TarangWidget()
}

