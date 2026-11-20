package com.sushant.inknote.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.inknote.R
import com.sushant.inknote.data.AppSettings
import com.sushant.inknote.data.Orientation
import com.sushant.inknote.data.PageEntity
import com.sushant.inknote.data.PageType
import com.sushant.inknote.editor.EditorViewModel
import com.sushant.inknote.ink.PagePalette
import com.sushant.inknote.ink.PageRenderer
import com.sushant.inknote.ui.home.NewNoteSheet
import com.sushant.inknote.ui.toast
import com.sushant.inknote.ui.theme.LocalInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PagesScreen(vm: EditorViewModel, settings: AppSettings, onBack: () -> Unit, expanded: Boolean) {
    val pages by vm.pages.collectAsState()
    val note by vm.note.collectAsState()
    val ink = LocalInk.current
    val ctx = LocalContext.current
    var sel by rememberSaveable { mutableIntStateOf(vm.currentPage) }
    var paperSheet by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { ctx.toast(it) } }
    LaunchedEffect(pages.size) { if (sel >= pages.size) sel = (pages.size - 1).coerceAtLeast(0) }
    val cur = pages.getOrNull(sel)
    Column(Modifier.fillMaxSize().background(ink.bg).then(if (expanded) Modifier else Modifier.statusBarsPadding()).testTag("pages_screen")) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.pages_n, pages.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.currentPage = sel; vm.addPage() }, Modifier.testTag("pages_add")) { Icon(Icons.Outlined.Add, stringResource(R.string.add_page)) }
        }
        Text(stringResource(R.string.pages_hint), style = MaterialTheme.typography.bodyMedium, color = ink.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val cols = when { maxWidth < 600.dp -> 2; maxWidth < 840.dp -> 3; else -> 5 }
            LazyVerticalGrid(GridCells.Fixed(cols), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(pages, key = { _, p -> p.id }) { i, p -> PageThumb(vm, p, i, i == sel, ink.dark) { sel = i } }
            }
        }
        Surface(color = ink.card, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val canTurn = cur != null && cur.pdfFile == null && note?.pageType != PageType.ENDLESS
                Action(Icons.Outlined.ScreenRotation, stringResource(if (cur?.orientation == Orientation.PORTRAIT) R.string.make_horizontal_short else R.string.make_vertical_short), canTurn, "pg_turn") { cur?.let { vm.turnPage(it) } }
                Action(Icons.Outlined.Description, stringResource(R.string.paper_style), cur?.pdfFile == null, "pg_paper") { paperSheet = true }
                Action(Icons.Outlined.ArrowBackIosNew, stringResource(R.string.move_earlier), sel > 0, "pg_up") { vm.movePage(sel, sel - 1); sel -= 1 }
                Action(Icons.Outlined.ArrowForwardIos, stringResource(R.string.move_later), sel < pages.size - 1, "pg_down") { vm.movePage(sel, sel + 1); sel += 1 }
                Action(Icons.Outlined.ContentCopy, stringResource(R.string.duplicate), cur != null, "pg_dup") { cur?.let { vm.duplicatePage(it) } }
                Action(Icons.Outlined.Delete, stringResource(R.string.delete), pages.size > 1, "pg_delete") { confirmDelete = true }
            }
        }
    }
    if (paperSheet && cur != null) NewNoteSheet(settings, true, cur.paper, { paperSheet = false }, { spec -> vm.setPaper(cur, spec.paper); paperSheet = false }, {}, {})
    if (confirmDelete && cur != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_page_q, sel + 1)) },
        text = { Text(stringResource(R.string.delete_page_body)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePage(cur) }, modifier = Modifier.testTag("confirm_delete_page")) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun PageThumb(vm: EditorViewModel, p: PageEntity, i: Int, on: Boolean, dark: Boolean, onClick: () -> Unit) {
    val content = vm.content(p.id)
    val bmp by produceState<Bitmap?>(null, p, content, dark) {
        val rendered = if (content == null) null else withContext(Dispatchers.Default) { runCatching { PageRenderer.bitmap(vm.files, vm.noteId, p, content, 360, if (dark) PagePalette.DARK else PagePalette.LIGHT) }.getOrNull() }
        value = rendered
    }
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).testTag("pthumb_$i"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(if (p.width > p.height) 1f else 1f).aspectRatio(p.width / p.height).clip(RoundedCornerShape(6.dp))
                .border(if (on) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, LocalInk.current.divider), RoundedCornerShape(6.dp))) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
            }
        }
        Text(stringResource(R.string.page_dir, i + 1, stringResource(if (p.orientation == Orientation.LANDSCAPE) R.string.horizontal else R.string.vertical)),
            style = MaterialTheme.typography.bodySmall, color = LocalInk.current.muted, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).widthIn(min = 56.dp).padding(6.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else LocalInk.current.muted.copy(alpha = 0.5f)
        Icon(icon, null, tint = c); Text(label, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
    }
}
