package com.sushant.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.sushant.gallery.R
import com.sushant.gallery.data.FileOps
import com.sushant.gallery.data.Formats
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.Source
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.GallerySheet
import com.sushant.gallery.ui.components.SheetRow
import com.sushant.gallery.ui.components.ThumbFetcher
import com.sushant.gallery.ui.theme.LocalFixedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/** Edit settings, all pure values so the same edit can be applied to the preview and the full photo. */
data class Edit(
    val rotation: Int = 0, val flip: Boolean = false,
    val crop: Rect = Rect(0f, 0f, 1f, 1f),
    val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f,
    val filter: Int = 0,
)

object EditMath {
    val FILTERS = listOf("Original", "Vivid", "Warm", "Cool", "Mono", "Fade", "Drama", "Retro")

    /** 4×5 colour matrix (android.graphics order) for the adjustments + filter. */
    fun matrix(e: Edit): FloatArray {
        val m = android.graphics.ColorMatrix()
        m.postConcat(filterMatrix(e.filter))
        m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(e.saturation) })
        val c = e.contrast; val t = (1f - c) * 128f + e.brightness * 120f
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        return m.array
    }

    private fun scale(r: Float, g: Float, b: Float, off: Float = 0f) =
        android.graphics.ColorMatrix(floatArrayOf(r, 0f, 0f, 0f, off, 0f, g, 0f, 0f, off, 0f, 0f, b, 0f, off, 0f, 0f, 0f, 1f, 0f))

    fun filterMatrix(i: Int): android.graphics.ColorMatrix = when (i) {
        1 -> android.graphics.ColorMatrix().apply { setSaturation(1.5f); postConcat(scale(1.08f, 1.08f, 1.08f, -10f)) }
        2 -> scale(1.1f, 1.02f, 0.88f, 6f)
        3 -> scale(0.9f, 1.0f, 1.12f, 4f)
        4 -> android.graphics.ColorMatrix().apply { setSaturation(0f) }
        5 -> android.graphics.ColorMatrix().apply { setSaturation(0.8f); postConcat(scale(0.85f, 0.85f, 0.85f, 30f)) }
        6 -> scale(1.35f, 1.35f, 1.35f, -55f)
        7 -> android.graphics.ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        else -> android.graphics.ColorMatrix()
    }

    /** Applies rotation/flip, then crop, then colour to [src]. */
    fun apply(src: Bitmap, e: Edit): Bitmap {
        val m = Matrix().apply { if (e.flip) postScale(-1f, 1f); postRotate(e.rotation.toFloat()) }
        val rotated = if (e.rotation % 360 == 0 && !e.flip) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val l = (e.crop.left * rotated.width).toInt().coerceIn(0, rotated.width - 1)
        val t = (e.crop.top * rotated.height).toInt().coerceIn(0, rotated.height - 1)
        val w = ((e.crop.width * rotated.width).toInt()).coerceIn(1, rotated.width - l)
        val h = ((e.crop.height * rotated.height).toInt()).coerceIn(1, rotated.height - t)
        val cropped = if (l == 0 && t == 0 && w == rotated.width && h == rotated.height) rotated else Bitmap.createBitmap(rotated, l, t, w, h)
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(cropped, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix(e)) })
        return out
    }

    /** Crop rect for an aspect ratio (w/h in pixels), centred, as large as possible. */
    fun aspectCrop(ratio: Float?, imgW: Int, imgH: Int): Rect {
        if (ratio == null || imgW <= 0 || imgH <= 0) return Rect(0f, 0f, 1f, 1f)
        val imgRatio = imgW.toFloat() / imgH
        return if (imgRatio > ratio) { val w = ratio / imgRatio; Rect((1 - w) / 2, 0f, (1 + w) / 2, 1f) }
        else { val h = imgRatio / ratio; Rect(0f, (1 - h) / 2, 1f, (1 + h) / 2) }
    }
}

@Composable
fun EditorScreen(vm: MainViewModel, nav: Nav, source: Source, index: Int) {
    val item = remember(source, index) { vm.list(source).getOrNull(index) }
    if (item == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val ctx = LocalContext.current
    val fixed = LocalFixedColors.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(1) } // 0 crop 1 adjust 2 filters 3 rotate
    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var flip by rememberSaveable { mutableStateOf(false) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var aspect by rememberSaveable { mutableIntStateOf(0) }
    var bright by rememberSaveable { mutableFloatStateOf(0f) }
    var contrast by rememberSaveable { mutableFloatStateOf(1f) }
    var sat by rememberSaveable { mutableFloatStateOf(1f) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var saveSheet by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val edit = Edit(rotation, flip, crop, bright, contrast, sat, filter)

    LaunchedEffect(item.cacheKey) { preview = withContext(Dispatchers.IO) { ThumbFetcher.decodeSampled(item.path, 1600) } }
    // rotated/flipped preview (crop and colour drawn live on top)
    val shown: ImageBitmap? = remember(preview, rotation, flip) {
        preview?.let { EditMath.apply(it, Edit(rotation = rotation, flip = flip)).asImageBitmap() }
    }
    val cm = remember(bright, contrast, sat, filter) { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(EditMath.matrix(edit))) }

    fun save(copy: Boolean) {
        saving = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { saveEdit(vm, item, edit, copy) }
            saving = false
            if (path != null) { vm.countEdit(); vm.toast(ctx.getString(if (copy) R.string.msg_saved_copy else R.string.msg_saved))
                vm.repo.refresh(); nav.back() }
            else vm.toast(ctx.getString(R.string.msg_save_failed))
        }
    }
    BackHandler(saveSheet) { saveSheet = false }

    Column(Modifier.fillMaxSize().background(fixed.viewerBg).windowInsetsPadding(WindowInsets.statusBars).testTag("editor")) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel), tint = fixed.onMedia) }
            Text(stringResource(R.string.edit), color = fixed.onMedia, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { saveSheet = true }, enabled = !saving && shown != null, modifier = Modifier.testTag("edit_save")) {
                Text(stringResource(R.string.save), color = fixed.editorAccent)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val img = shown
            if (img == null || saving) CircularProgressIndicator(color = fixed.editorAccent)
            if (img != null) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }; val boxH = with(density) { maxHeight.toPx() }
                val s = minOf(boxW / img.width, boxH / img.height)
                val dw = img.width * s; val dh = img.height * s
                Box(Modifier.size(with(density) { dw.toDp() }, with(density) { dh.toDp() })) {
                    Image(img, null, colorFilter = cm, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().then(
                        if (tab != 0) Modifier.drawWithContent {
                            // show the crop result outside crop mode
                            clipRect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height) { this@drawWithContent.drawContent() }
                        } else Modifier,
                    ))
                    if (tab == 0) CropOverlay(crop, ratioFor(aspect, img.width, img.height)) { crop = it }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(fixed.editorPanel).windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                when (tab) {
                    0 -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(R.string.crop_free, R.string.crop_square, R.string.crop_4_3, R.string.crop_16_9).forEachIndexed { i, l ->
                            Chip(stringResource(l), i == aspect) {
                                aspect = i
                                val im = shown
                                crop = EditMath.aspectCrop(ratioFor(i, im?.width ?: 1, im?.height ?: 1), im?.width ?: 1, im?.height ?: 1)
                            }
                        }
                    }
                    1 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SliderRow(stringResource(R.string.brightness), bright, -1f..1f) { bright = it }
                        SliderRow(stringResource(R.string.contrast), contrast, 0.5f..1.5f) { contrast = it }
                        SliderRow(stringResource(R.string.saturation), sat, 0f..2f) { sat = it }
                    }
                    2 -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(EditMath.FILTERS) { i, name ->
                            val f = remember(i) { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(EditMath.matrix(Edit(filter = i)))) }
                            Column(Modifier.clickable { filter = i }.testTag("filter_$i"), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp))
                                    .then(if (filter == i) Modifier.border(2.dp, fixed.editorAccent, RoundedCornerShape(10.dp)) else Modifier)) {
                                    shown?.let { Image(it, null, colorFilter = f, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(name, color = fixed.onMedia, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { rotation = (rotation + 270) % 360; crop = Rect(0f, 0f, 1f, 1f) }) { Icon(Icons.Rounded.RotateLeft, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.left)) }
                        FilledTonalButton(onClick = { rotation = (rotation + 90) % 360; crop = Rect(0f, 0f, 1f, 1f) }, modifier = Modifier.testTag("rot_right")) { Icon(Icons.Rounded.RotateRight, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.right)) }
                        FilledTonalButton(onClick = { flip = !flip }) { Icon(Icons.Rounded.Flip, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.flip)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf(Triple(Icons.Rounded.Crop, R.string.crop, 0), Triple(Icons.Rounded.Tune, R.string.adjust, 1),
                    Triple(Icons.Rounded.FilterVintage, R.string.filters, 2), Triple(Icons.Rounded.RotateRight, R.string.rotate, 3)).forEach { (ic, l, i) ->
                    val on = tab == i
                    Column(Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable { tab = i }.padding(vertical = 4.dp).testTag("etab_$i"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) fixed.editorChipOn else fixed.editorPanel), contentAlignment = Alignment.Center) {
                            Icon(ic, null, tint = if (on) fixed.editorOnChip else fixed.editorMuted)
                        }
                        Text(stringResource(l), color = if (on) fixed.onMedia else fixed.editorMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (saveSheet) GallerySheet({ saveSheet = false }, stringResource(R.string.save_changes)) {
        SheetRow(Icons.Rounded.ContentCopy, stringResource(R.string.save_copy), { saveSheet = false; save(true) }, sub = stringResource(R.string.save_copy_sub), tag = "save_copy")
        SheetRow(Icons.Rounded.Save, stringResource(R.string.overwrite), { saveSheet = false; save(false) }, tag = "save_over")
    }
}

private fun ratioFor(i: Int, w: Int, h: Int): Float? = when (i) {
    1 -> 1f
    2 -> if (w >= h) 4f / 3f else 3f / 4f
    3 -> if (w >= h) 16f / 9f else 9f / 16f
    else -> null
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val fixed = LocalFixedColors.current
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) fixed.editorChipOn else fixed.editorPanel)
            .border(1.dp, if (on) fixed.editorChipOn else fixed.editorMuted, RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = if (on) fixed.editorOnChip else fixed.onMedia, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val fixed = LocalFixedColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = fixed.onMedia, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(88.dp))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f).height(32.dp),
            colors = SliderDefaults.colors(thumbColor = fixed.editorAccent, activeTrackColor = fixed.editorAccent))
    }
}

/** Crop frame with a rule-of-thirds grid; drag a corner to resize or the middle to move. */
@Composable
private fun CropOverlay(rect: Rect, ratio: Float?, onChange: (Rect) -> Unit) {
    val fixed = LocalFixedColors.current
    var cur by remember { mutableStateOf(rect) }
    LaunchedEffect(rect) { cur = rect }
    Box(
        Modifier.fillMaxSize().testTag("crop")
            .pointerInput(ratio) {
                var mode = 0 // 0 move, 1 TL, 2 TR, 3 BL, 4 BR
                detectDragGestures(
                    onDragStart = { p ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val corners = listOf(Offset(cur.left * w, cur.top * h), Offset(cur.right * w, cur.top * h), Offset(cur.left * w, cur.bottom * h), Offset(cur.right * w, cur.bottom * h))
                        val near = corners.indexOfFirst { (it - p).getDistance() < 48.dp.toPx() }
                        mode = near + 1
                    },
                    onDragEnd = { onChange(cur) },
                ) { ch, d ->
                    ch.consume()
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val dx = d.x / w; val dy = d.y / h
                    var r = cur
                    val min = 0.1f
                    r = when (mode) {
                        0 -> {
                            val nl = (r.left + dx).coerceIn(0f, 1f - r.width); val nt = (r.top + dy).coerceIn(0f, 1f - r.height)
                            Rect(nl, nt, nl + r.width, nt + r.height)
                        }
                        1 -> Rect((r.left + dx).coerceIn(0f, r.right - min), (r.top + dy).coerceIn(0f, r.bottom - min), r.right, r.bottom)
                        2 -> Rect(r.left, (r.top + dy).coerceIn(0f, r.bottom - min), (r.right + dx).coerceIn(r.left + min, 1f), r.bottom)
                        3 -> Rect((r.left + dx).coerceIn(0f, r.right - min), r.top, r.right, (r.bottom + dy).coerceIn(r.top + min, 1f))
                        else -> Rect(r.left, r.top, (r.right + dx).coerceIn(r.left + min, 1f), (r.bottom + dy).coerceIn(r.top + min, 1f))
                    }
                    if (ratio != null && mode != 0) {
                        // keep the aspect: height follows width
                        val hNorm = (r.width * w / ratio) / h
                        r = if (mode == 1 || mode == 2) Rect(r.left, (r.bottom - hNorm).coerceAtLeast(0f), r.right, r.bottom)
                        else Rect(r.left, r.top, r.right, (r.top + hNorm).coerceAtMost(1f))
                    }
                    cur = r
                }
            }
            .drawWithContent {
                val w = size.width; val h = size.height
                val l = cur.left * w; val t = cur.top * h; val r = cur.right * w; val b = cur.bottom * h
                val dim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset(0f, 0f), Size(w, t)); drawRect(dim, Offset(0f, b), Size(w, h - b))
                drawRect(dim, Offset(0f, t), Size(l, b - t)); drawRect(dim, Offset(r, t), Size(w - r, b - t))
                val line = fixed.onMedia
                drawRect(line, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                for (i in 1..2) {
                    drawLine(line.copy(alpha = 0.5f), Offset(l + (r - l) * i / 3, t), Offset(l + (r - l) * i / 3, b), 1.dp.toPx())
                    drawLine(line.copy(alpha = 0.5f), Offset(l, t + (b - t) * i / 3), Offset(r, t + (b - t) * i / 3), 1.dp.toPx())
                }
                val c = 18.dp.toPx(); val sw = 4.dp.toPx()
                listOf(Offset(l, t) to Offset(1f, 1f), Offset(r, t) to Offset(-1f, 1f), Offset(l, b) to Offset(1f, -1f), Offset(r, b) to Offset(-1f, -1f)).forEach { (p, dir) ->
                    drawLine(line, p, Offset(p.x + c * dir.x, p.y), sw); drawLine(line, p, Offset(p.x, p.y + c * dir.y), sw)
                }
            },
    )
}

/** Writes the edited photo. Copy → Pictures/Edited; otherwise replaces the original (EXIF kept, orientation reset). */
private suspend fun saveEdit(vm: MainViewModel, item: MediaItem, e: Edit, copy: Boolean): String? = runCatching {
    val full = ThumbFetcher.decodeSampled(item.path, 4096) ?: return@runCatching null
    val out = EditMath.apply(full, e)
    val src = File(item.path)
    val dest = if (copy) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Edited").apply { mkdirs() }
        val base = src.nameWithoutExtension + "_edited"
        File(dir, Formats.uniqueName("$base.jpg") { File(dir, it).exists() })
    } else src
    val png = !copy && item.mime == "image/png"
    val tmp = File(dest.parentFile, ".tmp_" + dest.name)
    FileOutputStream(tmp).use { out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
    if (!png && item.isJpeg) runCatching {
        val old = ExifInterface(item.path); val nw = ExifInterface(tmp.path)
        listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ).plus(FileOps.GPS_TAGS).forEach { t -> old.getAttribute(t)?.let { nw.setAttribute(t, it) } }
        nw.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        nw.saveAttributes()
    }
    if (!FileOps.moveFile(tmp, dest)) { if (dest.exists() && !copy) dest.delete(); if (!tmp.renameTo(dest)) return@runCatching null }
    vm.repo.media.scan(listOf(dest.path))
    dest.path
}.getOrNull()
