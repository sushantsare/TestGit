package com.sushant.pageone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.pageone.R
import com.sushant.pageone.ui.theme.*

/*
 Shared components. Screens MUST reuse these; no screen builds its own version.
 Full list (builder implements all; signatures below are the contract for the core ones):
  ExpressiveIconShape, GroupedRow, FolderRow, PdfRow, PdfThumb, ReadingProgress,
  SectionHeader, ConnectedSortButtons, LargeTitleHeader, SmallTopBar, SearchPill,
  ContinueReadingCard, EmptyState, ErrorState, LoadingSkeleton, ScanningCard,
  SettingsItem (switch / nav / info), ReaderTopChrome, ReaderFloatingToolbar, PagePill,
  FastScrollHandle, AnnotateToolbar, ColorDots, SelectionPopup, FindBar,
  SheetSegmentedTabs, ReadAloudCard, SaveChangesDialog, PasswordDialog,
  ToolsFabMenu (FloatingActionButtonMenu), SetupStep, ShapeMorphNextButton.
*/

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** One row of a grouped list: surfaceContainerLow, 24/6dp segmented corners, 3dp gaps. */
@Composable
fun GroupedRow(
    index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier,
    selected: Boolean = false, minHeight: androidx.compose.ui.unit.Dp = Sizes.rowMin,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = GroupShapes.of(index, count),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Reader top: floating back circle · plain title + page (no container) · split pill (search | ⋮). Over a top fade. */
@Composable
fun ReaderTopChrome(
    title: String, subtitle: String, onBack: () -> Unit, onSearch: () -> Unit, onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth()
        .background(Brush.verticalGradient(0f to c.surfaceContainerHigh, 0.55f to c.surfaceContainerHigh, 1f to Color.Transparent))
        .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = onBack, shape = CircleShape, color = c.surfaceContainerHighest,
                shadowElevation = 3.dp, modifier = Modifier.size(Sizes.readerCircle)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(onClick = onSearch, shape = PageOneCorners.splitLeft, color = c.surfaceContainerHighest,
                    shadowElevation = 3.dp, modifier = Modifier.size(Sizes.readerCircle)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, stringResource(R.string.cd_find)) }
                }
                Surface(onClick = onMenu, shape = PageOneCorners.splitRight, color = c.surfaceContainerHighest,
                    shadowElevation = 3.dp, modifier = Modifier.size(Sizes.readerCircle)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more)) }
                }
            }
        }
    }
}

enum class EmptyType { FIRST_TIME, CLEARED, NO_RESULTS }

@Composable
fun EmptyState(
    type: EmptyType, title: String, body: String, modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.fillMaxHeight(0.18f))
        if (illustration != null && type != EmptyType.NO_RESULTS) { illustration(); Spacer(Modifier.height(24.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            if (type == EmptyType.FIRST_TIME) Button(onClick = onAction) { Text(actionLabel) }
            else TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit,
    index: Int, count: Int,
) {
    GroupedRow(index, count, onClick = { onChange(!checked) }, minHeight = 52.dp) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
