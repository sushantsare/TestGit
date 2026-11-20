package com.sushant.gallery.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.sushant.gallery.R
import com.sushant.gallery.data.Dates
import com.sushant.gallery.data.Formats
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.components.GallerySheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ExifInfo(val camera: String?, val settings: String?, val lat: Double?, val lon: Double?, val width: Int, val height: Int)

fun readExif(path: String): ExifInfo? = runCatching {
    val f = File(path); if (!f.exists()) return@runCatching null
    val e = ExifInterface(path)
    val make = e.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
    val model = e.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
    val camera = (if (model.startsWith(make, true)) model else "$make $model").trim().ifBlank { null }
    val parts = ArrayList<String>()
    e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { parts += String.format(Locale.ENGLISH, "f/%.1f", it) }
    e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
        parts += if (it < 1) "1/${(1 / it).toInt()}s" else String.format(Locale.ENGLISH, "%.1fs", it)
    }
    e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { parts += "ISO $it" }
    e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { parts += String.format(Locale.ENGLISH, "%.1f mm", it) }
    val ll = e.latLong
    ExifInfo(camera, parts.joinToString(" · ").ifBlank { null }, ll?.get(0), ll?.get(1),
        e.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0), e.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0))
}.getOrNull()

@Composable
fun InfoSheet(vm: MainViewModel, item: MediaItem, onDismiss: () -> Unit) {
    val exif by produceState<ExifInfo?>(null, item.cacheKey) {
        value = if (item.isVideo || item.vaultId != null || item.folder.isEmpty()) null else withContext(Dispatchers.IO) { readExif(item.path) }
    }
    val w = if (item.width > 0) item.width else exif?.width ?: 0
    val h = if (item.height > 0) item.height else exif?.height ?: 0
    GallerySheet(onDismiss, stringResource(R.string.details)) {
        Column(Modifier.testTag("info_sheet")) {
            InfoRow(Icons.Rounded.CalendarToday, Dates.fullDate(item.dateTaken) + " · " + Dates.timeOf(item.dateTaken))
            InfoRow(
                if (item.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image, item.name,
                listOfNotNull(if (w > 0 && h > 0) "$w × $h" else null, Formats.size(item.size), if (item.isVideo) Formats.duration(item.durationMs) else null).joinToString(" · "),
            )
            if (item.vaultId == null && item.folder.isNotEmpty()) InfoRow(Icons.Rounded.Folder, item.bucket, item.path)
            exif?.camera?.let { InfoRow(Icons.Rounded.PhotoCamera, it, exif?.settings) }
            val lat = exif?.lat; val lon = exif?.lon
            if (lat != null && lon != null) InfoRow(Icons.Rounded.LocationOn, String.format(Locale.ENGLISH, "%.4f, %.4f", lat, lon), stringResource(R.string.location_note))
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
