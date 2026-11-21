package com.sushant.gallery.ui.viewer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pinch
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.sushant.gallery.R
import com.sushant.gallery.data.Dates
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.Source
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.AlbumPickerSheet
import com.sushant.gallery.ui.components.ConfirmDialog
import com.sushant.gallery.ui.components.ShareSheet
import com.sushant.gallery.ui.components.fullModel
import com.sushant.gallery.ui.components.setAs
import com.sushant.gallery.ui.components.shareItems
import com.sushant.gallery.ui.components.thumbModel
import com.sushant.gallery.ui.theme.LocalFixedColors
import com.sushant.gallery.ui.vault.VaultGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@Composable
fun ViewerScreen(vm: MainViewModel, nav: Nav, source: Source, startIndex: Int) {
    if (source == Source.Vault) { VaultGate(vm, nav) { ViewerBody(vm, nav, source, startIndex) }; return }
    ViewerBody(vm, nav, source, startIndex)
}

@Composable
private fun ViewerBody(vm: MainViewModel, nav: Nav, source: Source, startIndex: Int) {
    val all by vm.items.collectAsStateWithLifecycle()
    val favs by vm.favourites.collectAsStateWithLifecycle()
    val hidden by vm.vaultItems.collectAsStateWithLifecycle()
    val list = remember(all, favs, hidden, source) { vm.list(source) }
    var currentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var film by rememberSaveable { mutableStateOf(false) }
    var lastIndex by rememberSaveable { mutableIntStateOf(startIndex) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()

    if (list.isEmpty()) {
        // Last photo deleted (or nothing to show): leave the viewer. Wait only for the very first library load.
        val canLeave = loaded || source == Source.External || source == Source.Vault
        if (canLeave) LaunchedEffect(Unit) { nav.back() }
        Box(Modifier.fillMaxSize().background(LocalFixedColors.current.viewerBg))
        return
    }
    val index = run {
        val k = currentKey
        val i = if (k == null) -1 else list.indexOfFirst { it.key == k }
        if (i >= 0) i else lastIndex.coerceIn(0, list.lastIndex)
    }

    AnimatedContent(
        film, label = "mode",
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = if (targetState) 1.15f else 0.85f)) togetherWith fadeOut(tween(160)) },
    ) { isFilm ->
        if (isFilm) {
            Filmstrip(
                vm = vm, list = list, start = index,
                title = titleFor(source),
                onIndex = { i -> lastIndex = i; currentKey = list.getOrNull(i)?.key },
                onExit = { i -> lastIndex = i; currentKey = list.getOrNull(i)?.key; film = false },
            )
        } else {
            PhotoPager(
                vm = vm, nav = nav, source = source, list = list, start = index,
                onIndex = { i -> lastIndex = i; currentKey = list.getOrNull(i)?.key },
                onFilmstrip = { film = true },
            )
        }
    }
}

@Composable
private fun titleFor(source: Source): String = when (source) {
    Source.Favourites -> stringResource(R.string.favourites)
    Source.Videos -> stringResource(R.string.videos)
    Source.Vault -> stringResource(R.string.hidden)
    is Source.Folder -> File(source.folder).name
    else -> stringResource(R.string.tab_photos)
}

@Composable
private fun PhotoPager(
    vm: MainViewModel, nav: Nav, source: Source, list: List<MediaItem>, start: Int,
    onIndex: (Int) -> Unit, onFilmstrip: () -> Unit,
) {
    val ctx = LocalContext.current
    val fixed = LocalFixedColors.current
    val favs by vm.favourites.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = start) { list.size }
    var bars by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf(false) }
    var share by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableIntStateOf(0) }
    var confirmForever by rememberSaveable { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val isVault = source == Source.Vault
    val isExternal = source == Source.External
    val hintKey = "viewer"
    var showHint by remember { mutableStateOf(!isExternal && vm.prefs.hintCount(hintKey) < 3) }
    LaunchedEffect(Unit) { if (showHint) { vm.prefs.bumpHint(hintKey); delay(5000); showHint = false } }

    // keep the pager on the same photo when the list changes (e.g. after a delete)
    LaunchedEffect(start, list.size) { if (pager.currentPage != start && start in list.indices && !pager.isScrollInProgress) pager.scrollToPage(start) }
    LaunchedEffect(pager.currentPage) { onIndex(pager.currentPage); playing = null }
    BackHandler(info || share || picker != 0) { info = false; share = false; picker = 0 }

    val item = list.getOrNull(pager.currentPage) ?: list.last()
    Box(
        Modifier.fillMaxSize().background(fixed.viewerBg).testTag("viewer")
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(list.lastIndex)) }; true }
                    Key.DirectionLeft -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                    Key.Delete, Key.Backspace -> { if (!isVault && !isExternal) vm.moveToBin(listOf(item)); true }
                    else -> false
                }
            },
    ) {
        HorizontalPager(pager, key = { list.getOrNull(it)?.key ?: "gone-$it" }, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            val m = list.getOrNull(page) ?: return@HorizontalPager
            if (m.isVideo && playing == m.key) {
                val uri: Uri? = remember(m.key) {
                    when {
                        m.vaultId != null -> vm.repo.vaultEntity(m.vaultId)?.let { vm.vault.viewFile(it) }?.let { Uri.fromFile(it) }
                        m.folder.isEmpty() -> m.uri
                        else -> Uri.fromFile(File(m.path))
                    }
                }
                if (uri != null) VideoPlayer(uri, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).testTag("player"))
            } else {
                ZoomableMedia(
                    item = m,
                    onTap = { bars = !bars; menu = false },
                    onPinchIn = { if (!isExternal) onFilmstrip() },
                    onPlay = { playing = m.key },
                )
            }
        }

        // top bar
        AnimatedVisibility(bars, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }, modifier = Modifier.align(Alignment.TopCenter)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(fixed.mediaScrim, Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars).padding(bottom = 20.dp),
            ) {
                com.sushant.gallery.ui.components.AppTopBar(
                    title = Dates.titleFor(item.dateTaken), subtitle = Dates.timeOf(item.dateTaken), onBack = { nav.back() }, contentColor = fixed.onMedia,
                ) {
                    if (!isVault && !isExternal) {
                        val fav = item.path in favs
                        IconButton(onClick = { vm.setFavourite(listOf(item), !fav) }, modifier = Modifier.testTag("fav")) {
                            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                stringResource(if (fav) R.string.remove_from_favourites else R.string.add_to_favourites), tint = fixed.onMedia)
                        }
                    }
                    if (!isExternal) Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("viewer_more")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options), tint = fixed.onMedia) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (isVault) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.unhide)) }, leadingIcon = { Icon(Icons.Rounded.Visibility, null) },
                                    onClick = { menu = false; vm.unhide(listOf(item)) })
                            } else {
                                if (!item.isVideo) DropdownMenuItem(text = { Text(stringResource(R.string.rotate)) }, leadingIcon = { Icon(Icons.Rounded.RotateRight, null) },
                                    onClick = { menu = false; vm.rotate(item) }, modifier = Modifier.testTag("menu_rotate"))
                                if (!item.isVideo) DropdownMenuItem(text = { Text(stringResource(R.string.set_as)) }, leadingIcon = { Icon(Icons.Rounded.Wallpaper, null) },
                                    onClick = { menu = false; setAs(ctx, vm.repo.ops, item) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_album)) }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) },
                                    onClick = { menu = false; picker = 1 })
                                DropdownMenuItem(text = { Text(stringResource(R.string.copy_to_album)) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = { menu = false; picker = 2 })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_hidden)) }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                                    onClick = { menu = false; vm.hide(listOf(item)) }, modifier = Modifier.testTag("menu_hide"))
                            }
                        }
                    }
                }
            }
        }

        // hint
        AnimatedVisibility(showHint && bars && !item.isVideo, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)) {
            Row(
                Modifier.background(fixed.hintFill, RoundedCornerShape(50)).border(1.dp, fixed.hintBorder, RoundedCornerShape(50))
                    .clickable { showHint = false; onFilmstrip() }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("film_hint"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Pinch, null, tint = fixed.onMedia, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.hint_pinch), color = fixed.onMedia, style = MaterialTheme.typography.bodySmall)
            }
        }

        // bottom bar
        AnimatedVisibility(bars, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }, modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, fixed.mediaScrim)))
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                if (!isVault) IconButton(onClick = { share = true }, modifier = Modifier.testTag("v_share")) { Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = fixed.onMedia) }
                if (!isVault && !isExternal && !item.isVideo) IconButton(onClick = { nav.editor(source, pager.currentPage) }, modifier = Modifier.testTag("v_edit")) {
                    Icon(Icons.Rounded.Edit, stringResource(R.string.edit), tint = fixed.onMedia)
                }
                IconButton(onClick = { info = true }, modifier = Modifier.testTag("v_info")) { Icon(Icons.Outlined.Info, stringResource(R.string.details), tint = fixed.onMedia) }
                if (isVault) IconButton(onClick = { confirmForever = true }, modifier = Modifier.testTag("v_delete")) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever), tint = fixed.onMedia) }
                else if (!isExternal) IconButton(onClick = { vm.moveToBin(listOf(item)) }, modifier = Modifier.testTag("v_delete")) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = fixed.onMedia) }
            }
        }
    }

    if (info) InfoSheet(vm, item) { info = false }
    if (share) ShareSheet(1, vm.prefs.stripLocation.value, hasPhotos = !item.isVideo,
        onShare = { strip -> share = false; shareItems(ctx, vm.repo.ops, listOf(item), strip); vm.countShare(1) }, onDismiss = { share = false })
    if (picker != 0) AlbumPickerSheet(
        copy = picker == 2, albums = albums,
        onPick = { f -> vm.transfer(listOf(item), f, picker == 2); picker = 0 },
        onNew = { n -> vm.transfer(listOf(item), vm.repo.ops.albumFolder(n), picker == 2); picker = 0 },
        onDismiss = { picker = 0 },
    )
    if (confirmForever) ConfirmDialog(
        stringResource(R.string.delete_forever_q), stringResource(R.string.delete_forever_body_1), stringResource(R.string.delete_forever),
        onConfirm = { confirmForever = false; vm.deleteHidden(listOf(item)) }, onDismiss = { confirmForever = false },
    )
}

/**
 * One photo with pinch-zoom (up to 8×), pan and double-tap zoom.
 * Pinching in while the photo fits the screen shrinks it and opens the filmstrip.
 */
@Composable
fun ZoomableMedia(item: MediaItem, onTap: () -> Unit, onPinchIn: () -> Unit, onPlay: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember(item.key) { mutableFloatStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }
    val shrink = remember(item.key) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val request = remember(item.cacheKey) {
        ImageRequest.Builder(ctx).data(fullModel(item)).size(2560).precision(Precision.INEXACT).crossfade(120).build()
    }
    Box(
        Modifier.fillMaxSize().clipToBounds()
            .pointerInput(item.key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var shrinking = false
                    var opened = false
                    do {
                        val e = awaitPointerEvent()
                        val pressed = e.changes.count { it.pressed }
                        if (pressed >= 2 && !opened) {
                            val zoom = e.calculateZoom()
                            run {
                                if ((scale <= 1.01f && zoom < 1f) || shrinking) {
                                    shrinking = true
                                    val s = (shrink.value * zoom).coerceIn(0.4f, 1f)
                                    scope.launch { shrink.snapTo(s) }
                                    if (s < 0.72f) { opened = true; onPinchIn() }
                                } else {
                                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                                    val c = e.calculateCentroid(useCurrent = true) - Offset(size.width / 2f, size.height / 2f)
                                    val pan = e.calculatePan()
                                    val raw = (offset - c) * (newScale / scale) + c + pan
                                    scale = newScale
                                    offset = clamp(raw, scale, size.width.toFloat(), size.height.toFloat())
                                }
                            }
                            e.changes.forEach { it.consume() }
                        } else if (pressed == 1 && scale > 1.01f && !opened) {
                            val pan = e.calculatePan()
                            val next = clamp(offset + pan, scale, size.width.toFloat(), size.height.toFloat())
                            val blockedX = abs(next.x - (offset.x + pan.x)) > 0.5f && abs(pan.x) > abs(pan.y)
                            offset = next
                            if (!blockedX) e.changes.forEach { it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                    if (shrinking && !opened) scope.launch { shrink.animateTo(1f, tween(200)) }
                    if (scale < 1.02f) { scale = 1f; offset = Offset.Zero }
                }
            }
            .pointerInput(item.key) {
                detectTapGestures(
                    onTap = { if (item.isVideo) onPlay() else onTap() },
                    onDoubleTap = { p ->
                        if (item.isVideo) return@detectTapGestures
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else {
                            scale = 2.5f
                            val c = p - Offset(size.width / 2f, size.height / 2f)
                            offset = clamp(-c * 1.5f, scale, size.width.toFloat(), size.height.toFloat())
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (item.isVideo) thumbModelFull(item) else request,
            contentDescription = (if (item.isVideo) "Video, " else "Photo, ") + Dates.fullDate(item.dateTaken),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = scale * shrink.value
                scaleX = s; scaleY = s; translationX = offset.x; translationY = offset.y
            }.testTag("photo"),
        )
        if (item.isVideo) {
            Box(
                Modifier.size(72.dp).background(LocalFixedColors.current.mediaScrim, CircleShape).clickable(onClick = onPlay).testTag("play"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
    }
}

private fun thumbModelFull(item: MediaItem): Any = thumbModel(item)

private fun clamp(o: Offset, scale: Float, w: Float, h: Float): Offset {
    val mx = (w * (scale - 1f)) / 2f
    val my = (h * (scale - 1f)) / 2f
    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
}
