package com.sushant.grabbit

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.data.Sites
import com.sushant.grabbit.download.Downloads
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.engine.Errors
import com.sushant.grabbit.engine.Media
import com.sushant.grabbit.engine.MediaInfo
import com.sushant.grabbit.ui.FetchState
import com.sushant.grabbit.ui.MediaCard
import com.sushant.grabbit.ui.PermissionExplainDialog
import com.sushant.grabbit.ui.StorageBlockedDialog
import com.sushant.grabbit.ui.components.BrandMark
import com.sushant.grabbit.ui.components.ErrorCard
import com.sushant.grabbit.ui.components.SkeletonBlock
import com.sushant.grabbit.ui.neededPermissions
import com.sushant.grabbit.ui.theme.GrabbitTheme
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.PillShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Opened from the system share sheet ("Share → Grabbit"). Shows a self-sizing bottom sheet over the app the
 * user is in; Download starts the job, closes the sheet and confirms with a toast. Progress lives in notifications.
 */
class ShareActivity : ComponentActivity() {
    private val vm: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent?.dataString
            val link = text?.let { Sites.extractUrl(it) }
            if (link == null) {
                Toast.makeText(applicationContext, "No link found in what you shared", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            vm.start(link)
        }
        setContent {
            GrabbitTheme {
                ShareSheet(
                    vm,
                    onClose = { finish() },
                    onStarted = { msg ->
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_URL, vm.url)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

class ShareViewModel(app: android.app.Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
        private set
    var state by mutableStateOf<FetchState>(FetchState.Loading)
        private set
    private var job: Job? = null

    fun start(link: String) {
        url = link
        fetch()
    }

    fun fetch() {
        job?.cancel()
        state = FetchState.Loading
        job = viewModelScope.launch {
            state = try {
                FetchState.Loaded(Media.fetchInfo(getApplication(), url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.sushant.grabbit.engine.FriendlyError) {
                FetchState.Failed(e.message ?: "", e.raw)
            } catch (e: Throwable) {
                val raw = e.message ?: e.toString()
                FetchState.Failed(Errors.friendly(raw), raw)
            }
        }
    }

    /** Queues the download and returns the confirmation text. */
    fun download(info: MediaInfo, quality: String, opts: DlOpts): String {
        if (info.isPlaylist && info.entries.isNotEmpty()) com.sushant.grabbit.download.PlaylistStart.start(getApplication(), info, quality, opts)
        else Downloads.enqueue(
            getApplication(),
            Downloads.Job(info.url, info.title, info.thumbnail, info.site, quality, info.isPlaylist, opts, items = info.gallery),
        )
        return if (info.isPlaylist) "Downloading ${info.entries.size} videos" else "Download started"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(vm: ShareViewModel, onClose: () -> Unit, onStarted: (String) -> Unit, onOpenApp: () -> Unit) {
    val sp = LocalSpacing.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Sheet height follows its content: loading, error and the full picker are all different sizes
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeThen(action: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { action() }
    }

    var pending by remember { mutableStateOf<Triple<MediaInfo, String, DlOpts>?>(null) }
    var explain by remember { mutableStateOf<List<String>>(emptyList()) }
    var storageBlocked by remember { mutableStateOf(false) }
    val startNow: (MediaInfo, String, DlOpts) -> Unit = { info, q, o ->
        val msg = vm.download(info, q, o)
        closeThen { onStarted(msg) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val p = pending
        pending = null
        if (Build.VERSION.SDK_INT <= 28 && res[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false) storageBlocked = true
        else if (p != null) startNow(p.first, p.second, p.third)
    }
    val onDownload: (MediaInfo, String, DlOpts) -> Unit = { info, q, o ->
        val need = neededPermissions(ctx)
        if (need.isEmpty()) startNow(info, q, o) else { pending = Triple(info, q, o); explain = need }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("shareSheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = sp.screen).padding(bottom = sp.l).navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(28.dp)
                Spacer(Modifier.width(sp.m))
                Text("Grabbit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { closeThen(onClose) }) { Icon(Icons.Rounded.Close, "Close") }
            }
            Spacer(Modifier.height(sp.s))
            when (val s = vm.state) {
                FetchState.Loading, FetchState.Idle -> {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), MaterialTheme.shapes.large)
                    Spacer(Modifier.height(sp.l))
                    SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(18.dp))
                    Spacer(Modifier.height(sp.s))
                    SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(18.dp))
                    Spacer(Modifier.height(sp.m))
                    Text("Getting video details…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is FetchState.Failed -> {
                    ErrorCard("Couldn't get this video", s.message, s.raw, onRetry = { vm.fetch() },
                        modifier = Modifier.testTag("fetchError"))
                    Spacer(Modifier.height(sp.m))
                    FilledTonalButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth().height(48.dp), shape = PillShape) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(sp.s))
                        Text("Open in Grabbit")
                    }
                }
                is FetchState.Loaded -> MediaCard(s.info, framed = false) { i, q, o -> onDownload(i, q, o) }
            }
        }
    }

    if (explain.isNotEmpty()) PermissionExplainDialog(
        need = explain,
        onContinue = {
            if (Manifest.permission.POST_NOTIFICATIONS in explain) Prefs.notifAsked = true
            permLauncher.launch(explain.toTypedArray()); explain = emptyList()
        },
        onSkip = {
            Prefs.notifAsked = true
            val p = pending
            pending = null
            val storageNeeded = Manifest.permission.WRITE_EXTERNAL_STORAGE in explain
            explain = emptyList()
            if (storageNeeded) storageBlocked = true else if (p != null) startNow(p.first, p.second, p.third)
        },
    )
    if (storageBlocked) StorageBlockedDialog(ctx) { storageBlocked = false }
}
