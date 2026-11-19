package com.sushant.gymmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sushant.gymmanager.data.local.MembershipEntity
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.ui.theme.StatusAmber
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.ui.theme.WhatsAppGreen
import com.sushant.gymmanager.ui.theme.WhatsAppInk
import com.sushant.gymmanager.ui.theme.StatusBlue
import com.sushant.gymmanager.ui.theme.StatusGray
import androidx.compose.runtime.ReadOnlyComposable
import com.sushant.gymmanager.util.DateUtils
import java.io.File

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(1.dp, LocalExtraColors.current.cardBorder)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (filled) color else color.copy(alpha = 0.13f),
        contentColor = if (filled) Color.White else color
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RollBadge(roll: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            "#$roll",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
fun ShiftPill(shift: String, modifier: Modifier = Modifier) {
    val morning = shift == Shift.MORNING
    Pill(
        text = t(if (morning) "MORNING" else "EVENING"),
        color = if (morning) StatusAmber else MaterialTheme.colorScheme.tertiary,
        modifier = modifier
    )
}

@Composable
fun MemberAvatar(photoPath: String?, name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(photoPath)).crossfade(true).build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else if (initials.isNotEmpty()) {
            Text(
                initials,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        } else {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun WhatsAppButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "WhatsApp") {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = WhatsAppGreen.copy(alpha = 0.15f),
            contentColor = WhatsAppInk
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = if (destructive) Icons.Default.WarningAmber else null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private object PastOrToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= (DateUtils.isoToUtcMillis(DateUtils.today()) ?: Long.MAX_VALUE)
    override fun isSelectableYear(year: Int): Boolean = year <= java.time.LocalDate.now().year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(initialIso: String?, onPick: (String) -> Unit, onDismiss: () -> Unit, pastOnly: Boolean = false) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DateUtils.isoToUtcMillis(initialIso),
        selectableDates = if (pastOnly) PastOrToday else DatePickerDefaults.AllDates
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(DateUtils.utcMillisToIso(it)) }
                onDismiss()
            }) { Text(t("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(title: String, initial: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val t = DateUtils.parseTime(initial)
    val state = rememberTimePickerState(initialHour = t?.hour ?: 6, initialMinute = t?.minute ?: 0, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = {
            TextButton(onClick = { onPick(DateUtils.toHHmm(state.hour, state.minute)); onDismiss() }) { Text(t("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}

data class PlanStatus(val label: String, val color: Color)

val FrozenBlue: Color @Composable @ReadOnlyComposable get() = StatusBlue

/** Labels are English keys; translate with t(status.label). */
@Composable
fun planStatus(membership: MembershipEntity?, thresholdDays: Int, frozen: Boolean = false): PlanStatus = when {
    frozen -> PlanStatus("Frozen", StatusBlue)
    membership == null -> PlanStatus("No Plan", StatusGray)
    DateUtils.isExpired(membership.expiryDate) -> PlanStatus("Expired", StatusRed)
    DateUtils.isExpiringSoon(membership.expiryDate, thresholdDays) -> PlanStatus("Expiring Soon", StatusAmber)
    else -> PlanStatus("Active", StatusGreen)
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Rounded search box used above long lists. */
@Composable
fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    tag: String = "search"
) {
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().testTag(tag),
        placeholder = { Text(placeholder, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onChange("") }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Clear, t("Clear search"))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** Matches a member by name, roll number or (3+ digits of) WhatsApp number. */
fun com.sushant.gymmanager.data.local.MemberEntity.matches(query: String): Boolean {
    val term = query.trim()
    if (term.isEmpty()) return true
    val digits = term.filter { it.isDigit() }
    return fullName.contains(term, ignoreCase = true) ||
        rollNumber.contains(term, ignoreCase = true) ||
        (digits.length >= 3 && whatsappNumber.filter { it.isDigit() }.contains(digits))
}
