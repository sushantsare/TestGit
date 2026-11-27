package com.sushant.pageone.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.sushant.pageone.data.*
import com.sushant.pageone.pdf.CompressLevel
import com.sushant.pageone.pdf.MergeItem
import com.sushant.pageone.pdf.PdfTools
import com.sushant.pageone.ui.MainVm
import com.sushant.pageone.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun pdfInfo(context: Context, uri: Uri): Pair<Int, Long> = runCatching {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        val size = pfd.statSize
        val pages = runCatching { PdfRenderer(pfd).use { it.pageCount } }.getOrDefault(0)
        pages to size
    } ?: (0 to 0L)
}.getOrDefault(0 to 0L)

private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
}.getOrDefault(0L)

/** Copy any picked document to a private file the PDF tools can read. */
private fun localFile(context: Context, uri: Uri): File {
    if (uri.scheme == "file") return File(uri.path!!)
    val out = File(File(context.cacheDir, "tools").apply { mkdirs() }, "src_${uri.hashCode().toUInt()}.pdf")
    context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
    return out
}

private fun uriOf(seed: String): Uri? = seed.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("/")) Uri.fromFile(File(it)) else Uri.parse(it) }

@Composable
private fun ToolThumb(item: MergeItem) {
    val context = LocalContext.current
    if (!item.isImage) { PdfThumb(null, 0); return }
    val bmp by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
            }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp, 52.dp).shadow(1.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun ToolScaffold(title: String, subtitle: String?, nav: NavHostController, bottom: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SmallTopBar(title, subtitle, onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f), content = content)
        Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) { bottom() }
    }
}

@Composable
private fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, enabled: Boolean, tag: String, error: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag), shape = RoundedCornerShape(28.dp),
        colors = if (error) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WorkingDialog(title: String, done: Int, total: Int) {
    AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(title) }, text = {
        Column {
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth().testTag("working"))
            Spacer(Modifier.height(8.dp))
            Text(if (total > 0) "$done of $total" else "Working…", style = MaterialTheme.typography.bodySmall)
        }
    })
}

private fun afterCreate(vm: MainVm, nav: NavHostController, out: NewFile, what: String) {
    out.path?.let { vm.lib.afterFileChanged(it) }
    val folder = if (out.path != null) "Documents/PageOne" else "Download/PageOne"
    vm.message("$what · saved to $folder", "Open") {
        nav.navigate(MainVm.readerRoute(if (out.path != null) DocRef.ofFile(out.path) else DocRef(out.uri.toString(), null, out.name)))
    }
}

// ======================= MERGE =======================

@Composable
fun MergeScreen(vm: MainVm, nav: NavHostController, seed: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<MergeItem>()) }
    var name by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 75.dp.toPx() }

    fun addPdfs(uris: List<Uri>) = scope.launch {
        val add = withContext(Dispatchers.IO) {
            uris.map { u -> val (p, s) = pdfInfo(context, u); MergeItem(u, Library.displayName(context.contentResolver, u) ?: u.lastPathSegment ?: "PDF", false, p, s) }
        }
        items = items + add
        if (name.isEmpty()) items.firstOrNull()?.let { name = it.name.removeSuffix(".pdf") + " merged" }
    }
    LaunchedEffect(seed) { uriOf(seed)?.let { addPdfs(listOf(it)) } }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addPdfs(it) }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        scope.launch {
            val add = withContext(Dispatchers.IO) { uris.map { u -> MergeItem(u, Library.displayName(context.contentResolver, u) ?: "Image", true, 1, sizeOf(context, u)) } }
            items = items + add
            if (name.isEmpty()) name = if (add.size == items.size) "Photos" else (items.first().name.removeSuffix(".pdf") + " merged")
        }
    }
    val pdfCount = items.count { !it.isImage }; val imgCount = items.size - pdfCount
    val pages = items.sumOf { maxOf(it.pages, 0) }
    ToolScaffold("Merge PDFs & images",
        if (items.isEmpty()) null else listOfNotNull(
            pdfCount.takeIf { it > 0 }?.let { "$it PDF${if (it > 1) "s" else ""}" },
            imgCount.takeIf { it > 0 }?.let { "$it image${if (it > 1) "s" else ""}" },
        ).joinToString(" + ") + " · $pages pages", nav,
        bottom = { BigButton(if (items.isEmpty()) "Add files to merge" else "Merge ${items.size} file${if (items.size > 1) "s" else ""}", Icons.Rounded.MergeType,
            items.isNotEmpty() && (items.size > 1 || items.first().isImage) && working == null, "merge_go") {
            scope.launch {
                working = 0 to items.size
                val r = runCatching {
                    withContext(Dispatchers.IO) { Outputs.create(context, name.ifBlank { "Merged" }) { out -> PdfTools.merge(context, items, out) { d, t -> working = d to t } } }
                }
                working = null
                r.onSuccess { afterCreate(vm, nav, it, "Merged ${items.size} files"); vm.a.local.count("merge") }
                    .onFailure { vm.message("Couldn’t merge: ${it.message ?: "unknown error"}") }
            }
        } },
    ) {
        Text("Drag to set the order. Each image becomes one page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
        LazyColumn(Modifier.weight(1f, fill = false).testTag("merge_list")) {
            itemsIndexed(items, key = { i, it -> it.uri.toString() + i }) { i, it ->
                val isDrag = dragging == i
                Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp).zIndex(if (isDrag) 1f else 0f).graphicsLayer { translationY = if (isDrag) dragY else 0f }) {
                    GroupedRow(i, items.size, onClick = {}, minHeight = 72.dp, onLongClick = {
                        val removed = it; items = items - it
                        vm.message("Removed ${removed.name}", "Undo") { items = items.toMutableList().apply { add(minOf(i, size), removed) } }
                    }) {
                        ToolThumb(it)
                        RowTexts(it.name, (if (it.pages == 1) "1 page" else "${it.pages} pages") + " · " + Format.size(it.size), Modifier.weight(1f))
                        AssistChip({}, { Text(if (it.isImage) "Image" else "PDF") }, modifier = Modifier.height(28.dp))
                        Box(Modifier.size(44.dp).pointerInput(i, items.size) {
                            detectDragGestures(onDragStart = { dragging = i; dragY = 0f }, onDragEnd = { dragging = null; dragY = 0f }, onDragCancel = { dragging = null; dragY = 0f }) { ch, d ->
                                ch.consume(); dragY += d.y
                                val idx = dragging ?: return@detectDragGestures
                                if (dragY > rowPx * 0.6f && idx < items.size - 1) { items = items.toMutableList().apply { add(idx + 1, removeAt(idx)) }; dragging = idx + 1; dragY -= rowPx }
                                else if (dragY < -rowPx * 0.6f && idx > 0) { items = items.toMutableList().apply { add(idx - 1, removeAt(idx)) }; dragging = idx - 1; dragY += rowPx }
                            }
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragIndicator, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f).testTag("add_pdfs")) {
                Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Add PDFs") }
            FilledTonalButton(onClick = { pickImg.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f).testTag("add_images")) {
                Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Add images") }
        }
        if (items.isNotEmpty()) {
            Text("Save as", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 6.dp))
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("merge_name"), suffix = { Text(".pdf") })
        }
    }
    working?.let { WorkingDialog("Merging…", it.first, it.second) }
}

// ======================= SPLIT =======================

@Composable
fun SplitScreen(vm: MainVm, nav: NavHostController, seed: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Uri?>(uriOf(seed)) }
    var srcName by remember { mutableStateOf("") }
    var pages by remember { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var ranges by rememberSaveable { mutableStateOf("") }
    var every by rememberSaveable { mutableStateOf("1") }
    var name by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(src) {
        val u = src ?: return@LaunchedEffect
        val (p, _) = withContext(Dispatchers.IO) { pdfInfo(context, u) }
        pages = p
        srcName = (Library.displayName(context.contentResolver, u) ?: u.lastPathSegment ?: "PDF")
        if (name.isEmpty()) name = srcName.removeSuffix(".pdf") + " (pages)"
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) { src = it; name = "" } }
    val parsed = Format.pageRanges(ranges, pages)
    val n = every.toIntOrNull()?.takeIf { it in 1..maxOf(1, pages) }
    ToolScaffold("Split or extract pages", if (src != null) "$srcName · $pages pages" else null, nav,
        bottom = {
            BigButton(if (mode == 0) "Extract ${parsed?.size ?: 0} page${if (parsed?.size == 1) "" else "s"}" else "Split into ${if (n != null && pages > 0) (pages + n - 1) / n else 0} files",
                Icons.Rounded.ContentCut, src != null && pages > 0 && !working && (if (mode == 0) parsed != null else n != null), "split_go") {
                val u = src ?: return@BigButton
                scope.launch {
                    working = true
                    val r = runCatching {
                        withContext(Dispatchers.IO) {
                            val f = localFile(context, u)
                            if (mode == 0) listOf(Outputs.create(context, name) { PdfTools.extract(f, null, parsed!!, it) })
                            else (0 until pages step n!!).mapIndexed { k, start ->
                                Outputs.create(context, srcName.removeSuffix(".pdf") + " part ${k + 1}") { PdfTools.extract(f, null, (start until minOf(pages, start + n)).toList(), it) }
                            }
                        }
                    }
                    working = false
                    r.onSuccess { outs -> if (outs.size == 1) afterCreate(vm, nav, outs[0], "Pages extracted") else { outs.forEach { o -> o.path?.let { vm.lib.afterFileChanged(it) } }; vm.message("Made ${outs.size} files in Documents/PageOne") }; vm.a.local.count("split") }
                        .onFailure { vm.message(if (it is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) "This PDF is protected. Open it in PageOne first." else "Couldn’t split: ${it.message}") }
                }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Group {
                SettingNav(Icons.Rounded.PictureAsPdf, if (src == null) "Choose a PDF" else srcName, if (src == null) null else "$pages pages · tap to change",
                    { pick.launch(arrayOf("application/pdf")) }, 0, 1, "split_pick")
            }
            if (src != null) {
                Spacer(Modifier.height(16.dp))
                ConnectedButtons(listOf("Extract pages", "Split every"), mode, { mode = it }, fill = true, height = 44.dp, tagPrefix = "split_mode")
                Spacer(Modifier.height(16.dp))
                if (mode == 0) {
                    OutlinedTextField(ranges, { ranges = it }, label = { Text("Pages, e.g. 1-3, 7") }, singleLine = true, isError = ranges.isNotBlank() && parsed == null,
                        supportingText = { Text(if (ranges.isNotBlank() && parsed == null) "Use numbers from 1 to $pages, like 1-3, 7" else "Pages are copied in this order") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth().testTag("split_ranges"))
                    OutlinedTextField(name, { name = it }, label = { Text("Save as") }, singleLine = true, suffix = { Text(".pdf") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("split_name"))
                } else {
                    OutlinedTextField(every, { every = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Pages per file") }, singleLine = true, isError = n == null,
                        supportingText = { Text("Each file gets this many pages") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("split_every"))
                }
            }
        }
    }
    if (working) WorkingDialog("Saving pages…", 0, 0)
}

// ======================= COMPRESS =======================

@Composable
fun CompressScreen(vm: MainVm, nav: NavHostController, seed: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Uri?>(uriOf(seed)) }
    var srcName by remember { mutableStateOf("") }
    var size by remember { mutableLongStateOf(0L) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var result by remember { mutableStateOf<Pair<NewFile, Long>?>(null) }
    LaunchedEffect(src) {
        val u = src ?: return@LaunchedEffect
        size = withContext(Dispatchers.IO) { sizeOf(context, u) }
        srcName = Library.displayName(context.contentResolver, u) ?: u.lastPathSegment ?: "PDF"
        result = null
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) src = it }
    val factors = listOf(0.75f, 0.4f, 0.22f)
    ToolScaffold("Compress PDF", if (src != null) "$srcName · ${Format.size(size)}" else null, nav,
        bottom = {
            if (progress != null) OutlinedButton(onClick = { job?.cancel(); job = null; progress = null }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("compress_cancel"),
                shape = RoundedCornerShape(28.dp)) { Text("Cancel") }
            else BigButton("Compress", Icons.Rounded.Compress, src != null, "compress_go") {
                val u = src ?: return@BigButton
                job = scope.launch {
                    progress = 0 to 0
                    val lv = CompressLevel.entries[level]
                    val r = runCatching {
                        withContext(Dispatchers.IO) {
                            val f = localFile(context, u)
                            Outputs.create(context, srcName.removeSuffix(".pdf") + " (small)") { PdfTools.compress(f, null, lv, it) { d, t -> progress = d to t } }
                        }
                    }
                    progress = null
                    r.onSuccess { out ->
                        val newSize = out.path?.let { File(it).length() } ?: sizeOf(context, out.uri)
                        result = out to newSize
                        out.path?.let { vm.lib.afterFileChanged(it) }
                        vm.a.local.count("compress")
                    }.onFailure { if (it !is kotlinx.coroutines.CancellationException) vm.message(if (it is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) "This PDF is protected. Open it in PageOne first." else "Couldn’t compress: ${it.message}") }
                }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Group {
                SettingNav(Icons.Rounded.PictureAsPdf, if (src == null) "Choose a PDF" else srcName, if (src == null) null else "${Format.size(size)} · tap to change",
                    { pick.launch(arrayOf("application/pdf")) }, 0, 1, "compress_pick")
            }
            if (src != null) {
                Text("How small?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("Light", "Balanced", "Strong").forEachIndexed { i, l ->
                        val on = i == level
                        Surface(onClick = { level = i }, shape = when { on -> RoundedCornerShape(24.dp); i == 0 -> RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp); i == 2 -> RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp); else -> RoundedCornerShape(8.dp) },
                            color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f).testTag("level_$i")) {
                            Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(l, style = MaterialTheme.typography.titleMedium)
                                Text("≈ " + Format.size((size * factors[i]).toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(listOf("Light keeps photos almost as they are.", "Balanced keeps text sharp and shrinks images. Good for WhatsApp and form uploads.",
                    "Strong makes the smallest file. Photos look softer.")[level], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp))
                progress?.let { (d, t) ->
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("compress_progress")) {
                        Column(Modifier.padding(20.dp)) {
                            Row { Text("Compressing…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(if (t > 0) "${d * 100 / t}%" else "", style = MaterialTheme.typography.titleMedium) }
                            Spacer(Modifier.height(12.dp))
                            WavyProgress(if (t > 0) d.toFloat() / t else 0f)
                            if (t > 0) Text("Page $d of $t", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                result?.let { (out, newSize) ->
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("compress_result")) {
                        Column(Modifier.padding(20.dp)) {
                            Text(if (newSize < size) "${Format.size(size)} → ${Format.size(newSize)}" else "Already small: ${Format.size(newSize)}", style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Saved as ${out.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { nav.navigate(MainVm.readerRoute(if (out.path != null) DocRef.ofFile(out.path) else DocRef(out.uri.toString(), null, out.name))) }) { Text("Open") }
                                FilledTonalButton(onClick = { Outputs.share(context, if (out.path != null) DocRef.ofFile(out.path) else DocRef(out.uri.toString(), null, out.name)) }) { Text("Share") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyProgress(f: Float) {
    LinearWavyProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
}

// ======================= DUPLICATES =======================

@Composable
fun DuplicatesScreen(vm: MainVm, nav: NavHostController) {
    val context = LocalContext.current
    val pdfs by vm.lib.pdfs.collectAsState()
    val thumbs by vm.lib.thumbVersion.collectAsState()
    val s by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val groups = remember(pdfs) {
        pdfs.filter { it.fingerprint.isNotEmpty() }.groupBy { it.fingerprint }.values.filter { it.size > 1 }
            .map { g -> g.sortedBy { p -> keepScore(p) } }.sortedByDescending { it.first().size }
    }
    var keep by remember(groups) { mutableStateOf(groups.associate { it.first().fingerprint to it.first().path }) }
    val toDelete = groups.flatMap { g -> g.filter { it.path != keep[it.fingerprint] } }
    val freed = toDelete.sumOf { it.size }
    var confirm by remember { mutableStateOf(false) }
    val pending = pdfs.count { it.fingerprint.isEmpty() }
    ToolScaffold("Duplicate PDFs", if (groups.isEmpty()) null else "${groups.size} group${if (groups.size > 1) "s" else ""} · ${Format.size(freed)} can be freed", nav,
        bottom = { if (groups.isNotEmpty()) BigButton("Delete ${toDelete.size} cop${if (toDelete.size == 1) "y" else "ies"} · free ${Format.size(freed)}", Icons.Rounded.Delete, toDelete.isNotEmpty(), "dup_delete", error = true) { confirm = true } },
    ) {
        if (groups.isEmpty()) EmptyState(EmptyType.CLEARED, if (pending > 0) "Still checking files…" else "No duplicates", if (pending > 0) "$pending PDFs still being read. Come back in a moment." else "Every PDF on your phone is saved only once.",
            Modifier.padding(top = 100.dp), icon = Icons.Rounded.DoneAll, kind = ExpressiveKind.SUNNY)
        else LazyColumn(Modifier.fillMaxSize().testTag("dup_list")) {
            groups.forEach { g ->
                item { SectionHeader("${g.first().name.removeSuffix(".pdf")} · ${Format.size(g.first().size)} each", color = MaterialTheme.colorScheme.primary) }
                itemsIndexed(g, key = { _, p -> p.path }) { i, p ->
                    val kept = keep[p.fingerprint] == p.path
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp)) {
                        PdfRow(vm, p, i, g.size, s.copy(rowProgress = false, rowPages = false), thumbs, onClick = { keep = keep + (p.fingerprint to p.path) },
                            metaPrefix = Format.folderName(p.folder),
                            trailing = { FilterChip(kept, { keep = keep + (p.fingerprint to p.path) }, { Text(if (kept) "Keep" else "Delete") }, modifier = Modifier.testTag("dup_chip")) })
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete ${toDelete.size} cop${if (toDelete.size == 1) "y" else "ies"}?") },
        text = { Text("One copy of each PDF stays. This frees ${Format.size(freed)} and can't be undone.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            scope.launch { val n = deleteFiles(context, toDelete.map { it.path }); vm.lib.scan(force = true); vm.message("Deleted $n cop${if (n == 1) "y" else "ies"}") }
        }, modifier = Modifier.testTag("confirm_dup_delete")) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

/** Lower = better to keep: your own folders before WhatsApp/Telegram/Download copies, then the oldest. */
fun keepScore(p: PdfEntity): Long {
    val f = p.folder.lowercase()
    val penalty = when { "whatsapp" in f || "telegram" in f -> 2L; "/download" in f -> 1L; else -> 0L }
    return penalty * 10_000_000_000_000L + p.modified
}
