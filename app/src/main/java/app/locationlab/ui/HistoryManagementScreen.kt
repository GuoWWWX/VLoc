package app.locationlab.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.data.HistoryEntry
import app.locationlab.simulation.Coordinate
import app.locationlab.ui.components.ButtonStyle
import app.locationlab.ui.components.DestructiveRed
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton
import app.locationlab.ui.components.LocationSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class HistoryPeriod(@StringRes val label: Int, val days: Long? = null) {
    All(R.string.history_all_dates), Today(R.string.history_date_today, 1),
    Week(R.string.history_last_week, 7), Month(R.string.history_last_month, 30),
    Custom(R.string.history_custom_dates),
}

@Composable
internal fun HistoryManagementScreen(
    entries: List<HistoryEntry>,
    canApply: Boolean,
    onBack: () -> Unit,
    onApply: (HistoryEntry) -> Unit,
    onPin: (String) -> Unit,
    onEdit: (HistoryEntry, Coordinate, String?, () -> Unit, () -> Unit) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onFavorite: (Set<String>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.All) }
    var start by rememberSaveable { mutableLongStateOf(today.minusDays(6).toEpochDay()) }
    var end by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var multi by rememberSaveable { mutableStateOf(false) }
    var checked by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf(emptySet<String>()) }
    var menuKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var shareKey by rememberSaveable { mutableStateOf<String?>(null) }
    val first = period.days?.let { today.minusDays(it - 1) } ?: LocalDate.ofEpochDay(start)
    val last = if (period == HistoryPeriod.Custom) LocalDate.ofEpochDay(end) else today
    val groups = remember(entries, query, period, first, last, zone) {
        val text = query.trim()
        entries.asSequence().sortedByDescending { it.selectedAt }.filter { entry ->
            val matches = text.isEmpty() || entry.placeName?.contains(text, ignoreCase = true) == true ||
                entry.coordinate.display().contains(text)
            matches && (period == HistoryPeriod.All || localDay(entry.selectedAt, zone) in first..last)
        }.groupBy { localDay(it.selectedAt, zone) }
    }
    val visibleKeys = remember(groups) { groups.values.flatten().mapTo(mutableSetOf()) { it.key } }
    val selected = checked intersect visibleKeys

    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 540.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocationIconButton(Glyph.Back, stringResource(R.string.back), onBack, iconSize = 21.dp)
                Text(stringResource(R.string.history_management), Modifier.weight(1f),
                    fontSize = 21.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { multi = !multi; checked = emptySet() }) {
                    Text(stringResource(if (multi) R.string.history_done else R.string.history_multiselect))
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(query, { query = it; checked = emptySet() }, Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.history_search), maxLines = 1) },
                    leadingIcon = { LocationIcon(Glyph.Search, size = 17.dp) },
                    trailingIcon = if (query.isEmpty()) null else {{
                        LocationIconButton(Glyph.Close, stringResource(R.string.clear_search),
                            { query = ""; checked = emptySet() }, visibleSize = 32.dp, iconSize = 15.dp)
                    }},
                )
                TextButton(onClick = { showDates = true }) {
                    Text(stringResource(if (period == HistoryPeriod.All) R.string.history_date else period.label))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                if (multi) Row(verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(
                        state = when {
                            selected.isEmpty() -> ToggleableState.Off
                            selected.size == visibleKeys.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = { checked = if (selected.size == visibleKeys.size) emptySet() else visibleKeys },
                        enabled = visibleKeys.isNotEmpty(),
                    )
                    Text(stringResource(R.string.history_select_all), fontSize = 12.sp, color = colors.onSurfaceVariant)
                } else Box(Modifier.size(1.dp))
                Text(stringResource(R.string.history_count, visibleKeys.size), color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (groups.isEmpty()) item {
                    Text(stringResource(R.string.history_no_results), Modifier.padding(vertical = 20.dp),
                        color = colors.onSurfaceVariant)
                }
                for ((day, rows) in groups) {
                    item(key = "day-$day") {
                        val label = when (day) {
                            today -> stringResource(R.string.history_date_today)
                            today.minusDays(1) -> stringResource(R.string.history_date_yesterday)
                            else -> day.format(DateTimeFormatter.ofPattern("MM/dd"))
                        }
                        Text(label, Modifier.padding(top = 14.dp, bottom = 6.dp), color = colors.onSurfaceVariant, fontSize = 12.sp)
                    }
                    itemsIndexed(rows, key = { _, entry -> entry.key }) { index, entry ->
                        val top = if (index == 0) 16.dp else 0.dp
                        val bottom = if (index == rows.lastIndex) 16.dp else 0.dp
                        val marked = entry.key in selected
                        val toggle = { checked = if (marked) selected - entry.key else selected + entry.key }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom)).background(colors.surface)) {
                            if (index != 0) HorizontalDivider(Modifier.padding(start = 49.dp, end = 12.dp), color = colors.outlineVariant)
                            Row(Modifier.fillMaxWidth().heightIn(min = 63.dp).then(
                                if (multi) Modifier.toggleable(marked, role = Role.Checkbox) { toggle() }
                                else Modifier.clickable { menuKey = entry.key }
                            ).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (multi) Checkbox(marked, onCheckedChange = null, modifier = Modifier.size(21.dp))
                                else Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(colors.background),
                                    contentAlignment = Alignment.Center) {
                                    LocationIcon(Glyph.Position, size = 17.dp, color = colors.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(entry.placeName ?: stringResource(R.string.coordinate_title),
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    CopyableCoordinate(entry.coordinate, fontSize = 10.sp)
                                }
                                if (!multi) TextButton(enabled = canApply, onClick = { onApply(entry) }) {
                                    Text(stringResource(R.string.history_apply))
                                }
                            }
                        }
                    }
                }
            }
            if (multi) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = selected.isNotEmpty(), onClick = { onFavorite(selected); checked = emptySet() }) {
                        Text(stringResource(R.string.favorite))
                    }
                    TextButton(enabled = selected.isNotEmpty(), onClick = { deleting = selected }) {
                        Text(stringResource(R.string.delete_history_entry), color = if (selected.isEmpty())
                            colors.onSurfaceVariant.copy(alpha = 0.48f) else DestructiveRed)
                    }
                    LocationButton(stringResource(R.string.history_apply),
                        { entries.firstOrNull { it.key in selected }?.let(onApply) }, Modifier.width(74.dp),
                        enabled = canApply && selected.size == 1, minimumHeight = 36.dp)
                    Text(stringResource(R.string.history_selected_count, selected.size), Modifier.weight(1f),
                        color = colors.onSurfaceVariant, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
    if (showDates) HistoryDateSheet(period, start, end, onClose = { showDates = false }) { option, from, to ->
        period = option
        start = from
        end = to
        checked = emptySet()
        showDates = false
    }
    entries.firstOrNull { it.key == menuKey }?.let { entry ->
        HistoryActionSheet(entry, onClose = { menuKey = null },
            onPin = { menuKey = null; onPin(entry.key) },
            onEdit = { menuKey = null; editKey = entry.key },
            onShare = { menuKey = null; shareKey = entry.key },
            onDelete = { menuKey = null; deleting = setOf(entry.key) },
        )
    }
    entries.firstOrNull { it.key == editKey }?.let { entry ->
        LocationEntryDialog(entry.coordinate, entry.placeName, importing = false, onClose = { editKey = null },
            onSave = { point, name, onFailure -> onEdit(entry, point, name, { editKey = null }, onFailure) })
    }
    entries.firstOrNull { it.key == shareKey }?.let { entry ->
        ShareLocationSheet(entry, onClose = { shareKey = null })
    }
    if (deleting.isNotEmpty()) {
        AlertDialog(onDismissRequest = { deleting = emptySet() },
            title = { Text(stringResource(R.string.history_delete_confirm, deleting.size)) },
            dismissButton = { TextButton(onClick = { deleting = emptySet() }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = {
                onDelete(deleting)
                checked = checked - deleting
                deleting = emptySet()
            }) { Text(stringResource(R.string.delete_history_entry), color = DestructiveRed) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDateSheet(
    initial: HistoryPeriod,
    initialStart: Long,
    initialEnd: Long,
    onClose: () -> Unit,
    onApply: (HistoryPeriod, Long, Long) -> Unit,
) {
    var option by rememberSaveable { mutableStateOf(initial) }
    var start by rememberSaveable { mutableLongStateOf(initialStart) }
    var end by rememberSaveable { mutableLongStateOf(initialEnd) }
    var dateField by rememberSaveable { mutableStateOf<Int?>(null) }
    val colors = MaterialTheme.colorScheme
    LocationSheet(stringResource(R.string.history_filter_dates), onClose) {
        for (period in HistoryPeriod.entries) {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(11.dp))
                .then(if (option == period) Modifier.background(colors.secondaryContainer) else Modifier)
                .clickable { option = period }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LocationIcon(Glyph.History, size = 17.dp, color = colors.primary)
                Text(stringResource(period.label), Modifier.weight(1f), fontSize = 13.sp)
                if (option == period) LocationIcon(Glyph.Check, size = 16.dp, color = colors.primary)
            }
        }
        if (option == HistoryPeriod.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TextButton(onClick = { dateField = 0 }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_start_date, LocalDate.ofEpochDay(start)))
                }
                TextButton(onClick = { dateField = 1 }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_end_date, LocalDate.ofEpochDay(end)))
                }
            }
            if (start > end) Text(stringResource(R.string.history_invalid_dates), color = colors.error, fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LocationButton(stringResource(R.string.cancel), onClose, Modifier.weight(1f),
                style = ButtonStyle.Secondary, minimumHeight = 38.dp)
            LocationButton(stringResource(R.string.history_apply_filter), { onApply(option, start, end) },
                Modifier.weight(1f), enabled = option != HistoryPeriod.Custom || start <= end, minimumHeight = 38.dp)
        }
    }
    dateField?.let { field ->
        val date = LocalDate.ofEpochDay(if (field == 0) start else end)
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val day = localDay(millis, ZoneOffset.UTC).toEpochDay()
                        if (field == 0) start = day else end = day
                    }
                    dateField = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { dateField = null }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(picker) }
    }
}

private fun localDay(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
