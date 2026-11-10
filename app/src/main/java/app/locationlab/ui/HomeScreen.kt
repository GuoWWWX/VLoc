package app.locationlab.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.data.AppData
import app.locationlab.data.HistoryEntry
import app.locationlab.map.MapCamera
import app.locationlab.map.MapSource
import app.locationlab.map.SourceMap
import app.locationlab.platform.SimulationState
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.SimulationConfig
import app.locationlab.ui.components.ButtonStyle
import app.locationlab.ui.components.DestructiveRed
import app.locationlab.ui.components.FavoriteYellow
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton
import app.locationlab.ui.components.LocationPill
import app.locationlab.ui.components.LocationSheet
import app.locationlab.ui.components.LocationSwitch
import app.locationlab.ui.components.SegmentedIconControl
import app.locationlab.ui.components.SwipeActionsRow
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    data: AppData,
    active: SimulationState.Running?,
    @StringRes busyStatus: Int?,
    drawerOpen: Boolean,
    onSettings: () -> Unit,
    onPick: () -> Unit,
    onAdd: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelect: (HistoryEntry) -> Unit,
    onEdit: (HistoryEntry, Coordinate, String?, onSaved: () -> Unit, onFailure: () -> Unit) -> Unit,
    onPin: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleDrift: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val running = active != null || busyStatus == R.string.simulation_stopping
    val config = active?.config ?: data.config
    val name = if (active != null) active.name else data.placeName
    var favorites by rememberSaveable { mutableStateOf(false) }
    val entries = remember(data.history, favorites) { data.visibleHistory(favorites) }
    val listState = rememberLazyListState()
    var menuKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var shareKey by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var expandedBounds by remember { mutableStateOf<Rect?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BackHandler(enabled = expandedKey != null && !drawerOpen) { expandedKey = null }
    LaunchedEffect(drawerOpen, entries) {
        if (drawerOpen || entries.none { it.key == expandedKey }) expandedKey = null
    }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { coordinates = it }
            .pointerInput(expandedKey) {
                if (expandedKey == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val rootPoint = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position)
                    if (rootPoint != null && expandedBounds?.contains(rootPoint) == false) {
                        down.consume()
                        expandedKey = null
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val pinPreview = maxHeight >= 480.dp
        val preview: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().background(colors.background)) {
                CurrentLocationCard(config, name, data.mapSource, active, data.lastSimulation, running, busyStatus,
                    onPick, onAdd, onStart, onStop, onRefresh, onToggleDrift)
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history), Modifier.weight(1f), color = colors.onSurface,
                        fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    SegmentedIconControl(
                        rightSelected = favorites,
                        leftLabel = stringResource(R.string.history_all_filter),
                        rightLabel = stringResource(R.string.history_favorites_filter),
                        leftIcon = Glyph.List,
                        rightIcon = if (favorites) Glyph.StarFilled else Glyph.Star,
                        onSelect = { favorites = it; expandedKey = null },
                        modifier = Modifier.offset(x = 8.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        LazyColumn(
            Modifier.widthIn(max = 540.dp).fillMaxWidth().safeDrawingPadding(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocationIconButton(Glyph.Menu, stringResource(R.string.settings), onSettings,
                        modifier = Modifier.offset(x = (-2).dp), background = colors.surface, iconSize = 23.dp)
                    Text(
                        stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp),
                        color = colors.onSurface, fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                    )
                    SimulationStatus(
                        stringResource(busyStatus ?: if (running) R.string.home_running else R.string.idle),
                        running, busyStatus != null,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
            if (pinPreview) stickyHeader(key = "map-and-history-header") { preview() }
            else item(key = "map-and-history-header") { preview() }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(if (favorites) R.string.favorites_empty else R.string.history_empty),
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(colors.surface).padding(16.dp),
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val top = if (index == 0) 17.dp else 0.dp
                val bottom = if (index == entries.lastIndex) 17.dp else 0.dp
                val expanded = expandedKey == entry.key
                var rowBounds by remember { mutableStateOf<Rect?>(null) }
                HistoryRow(
                    entry = entry,
                    selected = data.selectedHistoryKey == entry.key,
                    canSelect = !running && busyStatus == null,
                    expanded = expanded,
                    onExpand = { open ->
                        if (open) {
                            expandedBounds = rowBounds
                            expandedKey = entry.key
                        } else if (expandedKey == entry.key) {
                            expandedKey = null
                        }
                    },
                    onSelect = { if (expanded) expandedKey = null else onSelect(entry) },
                    onMenu = { expandedKey = null; menuKey = entry.key },
                    onPin = { expandedKey = null; onPin(entry.key) },
                    onFavorite = { onFavorite(entry.key) },
                    onDelete = { expandedKey = null; onDelete(entry.key) },
                    modifier = Modifier.clip(RoundedCornerShape(top, top, bottom, bottom))
                        .onGloballyPositioned {
                            rowBounds = it.boundsInRoot()
                            if (expanded) expandedBounds = rowBounds
                        },
                    separator = index != 0,
                )
            }
        }
    }
    data.history.firstOrNull { it.key == menuKey }?.let { entry ->
        HistoryActionSheet(entry, onClose = { menuKey = null },
            onPin = { menuKey = null; onPin(entry.key) },
            onEdit = { menuKey = null; editKey = entry.key },
            onShare = { menuKey = null; shareKey = entry.key },
            onDelete = { menuKey = null; onDelete(entry.key) },
        )
    }
    data.history.firstOrNull { it.key == editKey }?.let { entry ->
        LocationEntryDialog(entry.coordinate, entry.placeName, importing = false, onClose = { editKey = null },
            onSave = { point, name, onFailure -> onEdit(entry, point, name, { editKey = null }, onFailure) },
        )
    }
    data.history.firstOrNull { it.key == shareKey }?.let { entry ->
        ShareLocationSheet(entry, onClose = { shareKey = null })
    }
}

@Composable
private fun SimulationStatus(label: String, running: Boolean, busy: Boolean) {
    val colors = MaterialTheme.colorScheme
    val dot = when {
        busy -> Color(0xFFBE7A30)
        running -> Color(0xFF209467)
        else -> colors.onSurfaceVariant
    }
    val fill by animateColorAsState(if (running && !busy) colors.secondaryContainer else colors.surface,
        tween(200), label = "Status background")
    Row(
        Modifier.widthIn(min = 70.dp).heightIn(min = 28.dp)
            .clip(CircleShape).background(fill)
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(dot.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            if (running && !busy) {
                val motion = rememberInfiniteTransition(label = "Status waves")
                val progress = motion.animateFloat(0f, 1f,
                    animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "Wave progress")
                Canvas(Modifier.size(10.dp)) {
                    for (wave in listOf(progress.value, (progress.value + 0.5f) % 1f)) {
                        drawCircle(dot.copy(alpha = (1f - wave) * 0.48f), radius = (3 + 7 * wave).dp.toPx(),
                            style = Stroke(1.dp.toPx()))
                    }
                }
            }
            Box(Modifier.size(5.dp).background(dot, CircleShape))
        }
        Crossfade(label, animationSpec = tween(160), label = "Simulation status") { status ->
            Text(status, color = if (running && !busy) colors.onSecondaryContainer else colors.onSurfaceVariant,
                fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CurrentLocationCard(
    config: SimulationConfig?,
    placeName: String?,
    mapSource: MapSource,
    active: SimulationState.Running?,
    lastSimulation: Coordinate?,
    running: Boolean,
    @StringRes busyStatus: Int?,
    onPick: () -> Unit,
    onAdd: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDrift: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val busy = busyStatus != null
    val previous = if (active != null) active.previousPoint else lastSimulation
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(colors.surface)
            .padding(start = 16.dp, top = 13.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                placeName ?: stringResource(if (config == null) R.string.no_point else R.string.coordinate_title),
                Modifier.weight(1f).padding(end = 8.dp), color = colors.onSurface, fontSize = 19.sp,
                lineHeight = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (config != null) {
                LocationPill(
                    if (config.drift) stringResource(R.string.drift_summary, config.radiusMeters)
                    else stringResource(R.string.fixed),
                    Modifier.widthIn(min = 80.dp), minimumHeight = 25.dp,
                )
            }
        }
        if (config != null) {
            Spacer(Modifier.height(4.dp))
            CopyableCoordinate(config.center)
        }
        Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            LocationMapPreview(config, active, previous, mapSource, canOpen = config != null && !running && !busy,
                onOpen = onPick)
            LocationIconButton(Glyph.Plus, stringResource(R.string.add_location), onAdd,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-17).dp, y = (-24).dp),
                background = colors.primary, color = colors.onPrimary, visibleSize = 44.dp, iconSize = 23.dp,
                enabled = !busy, shape = CircleShape)
        }
        if (config != null && mapSource in listOf(MapSource.OpenStreetMap, MapSource.OpenFreeMap, MapSource.VersaTiles)) {
            val uri = LocalUriHandler.current
            val copyright = stringResource(R.string.osm_copyright)
            Text(stringResource(R.string.map_attribution), Modifier.align(Alignment.End)
                .clickable { uri.openUri(copyright) }.padding(top = 2.dp),
                color = colors.onSurfaceVariant, fontSize = 9.sp, lineHeight = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LocationSwitch(config?.drift == true, stringResource(R.string.home_drift),
                    enabled = config != null && !running && !busy, showLabel = true) { onToggleDrift() }
                val available = active?.config?.drift == true && !busy
                val refreshing = active?.refreshing == true
                val ink = if (available) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.64f)
                val refreshLabel = stringResource(if (refreshing) R.string.refreshing_location else R.string.refresh_location)
                Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = available && !refreshing, role = Role.Button, onClick = onRefresh)
                    .semantics { contentDescription = refreshLabel }.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = ink, strokeWidth = 2.dp)
                    else LocationIcon(Glyph.Refresh, size = 20.dp, color = ink)
                    Text(stringResource(R.string.refresh_short), color = ink, fontSize = 11.sp, maxLines = 1)
                }
            }
            LocationButton(
                stringResource(busyStatus ?: if (running) R.string.simulation_stop else R.string.start),
                if (running) onStop else onStart, Modifier.width(116.dp),
                enabled = running || config != null, loading = busy,
                style = if (running) ButtonStyle.Destructive else ButtonStyle.Primary,
                minimumHeight = 38.dp, glyph = if (running) Glyph.Stop else null,
            )
        }
        if (active != null) DriftScheduleStatus(active)
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    selected: Boolean,
    canSelect: Boolean,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    separator: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val date = historyDate(entry.selectedAt)
    val pin = stringResource(if (entry.pinned) R.string.unpin else R.string.pin)
    val delete = stringResource(R.string.delete_history_entry)
    SwipeActionsRow(
        expanded, onExpand, actionsWidth = 144.dp,
        modifier = modifier.semantics {
            if (!expanded) customActions = listOf(
                CustomAccessibilityAction(pin) { onPin(); true },
                CustomAccessibilityAction(delete) { onDelete(); true },
            )
        },
        actions = {
            HistoryAction(pin, if (entry.pinned) Glyph.Unpin else Glyph.Pin, 80.dp,
                colors.secondaryContainer, colors.onSecondaryContainer, onPin)
            HistoryAction(delete, Glyph.Trash, 64.dp, DestructiveRed, Color.White, onDelete)
        },
    ) {
        Box(Modifier.fillMaxWidth().background(colors.surface)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 65.dp)
                    .padding(start = 14.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).heightIn(min = 65.dp)
                        .combinedClickable(
                            onClick = { if (canSelect || expanded) onSelect() },
                            onLongClickLabel = stringResource(R.string.location_actions),
                            onLongClick = onMenu,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(33.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.secondaryContainer else colors.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        LocationIcon(Glyph.Position, size = 19.dp,
                            color = if (selected) colors.primary else colors.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp, end = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.placeName == null) {
                                CopyableCoordinate(entry.coordinate, Modifier.weight(1f, fill = false),
                                    fontSize = 13.sp, onClick = { if (canSelect || expanded) onSelect() })
                            } else {
                                Text(entry.placeName, Modifier.weight(1f, fill = false),
                                    color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (entry.pinned) {
                                Spacer(Modifier.width(5.dp))
                                LocationPill(stringResource(R.string.pin), minimumHeight = 18.dp)
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.placeName != null) {
                                CopyableCoordinate(entry.coordinate, fontSize = 9.sp,
                                    onClick = { if (canSelect || expanded) onSelect() })
                            }
                            Text(if (entry.placeName == null) date else "  ·  $date", color = colors.onSurfaceVariant,
                                fontSize = 9.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                LocationIconButton(
                    if (entry.favorite) Glyph.StarFilled else Glyph.Star,
                    stringResource(if (entry.favorite) R.string.unfavorite else R.string.favorite),
                    { if (expanded) onExpand(false) else onFavorite() }, iconSize = 20.dp,
                    color = if (entry.favorite) FavoriteYellow else colors.onSurfaceVariant,
                )
            }
            if (separator) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 58.dp, end = 14.dp)
                    .height(1.dp).background(colors.outlineVariant.copy(alpha = 0.58f)))
            }
        }
    }
}

@Composable
private fun HistoryAction(label: String, glyph: Glyph, width: Dp, fill: Color, ink: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(width).fillMaxHeight().background(fill).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LocationIcon(glyph, size = 17.dp, color = ink)
        Spacer(Modifier.height(4.dp))
        Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun LocationMapPreview(
    config: SimulationConfig?,
    active: SimulationState.Running?,
    previous: Coordinate?,
    source: MapSource,
    canOpen: Boolean,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showPrevious by rememberSaveable { mutableStateOf(false) }
    val current = active?.point?.takeIf { active.config.drift }
    Box(Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(12.dp)).background(colors.background),
        contentAlignment = Alignment.Center) {
        if (config != null) {
            key(config.center) {
                SourceMap(source, MapCamera(config.center, 0, config.radiusMeters.takeIf { config.drift },
                    currentPoint = current, previousPoint = previous),
                    onPointSelected = null, modifier = Modifier.matchParentSize(), showAttribution = false)
            }
        } else {
            LocationIcon(Glyph.Position, size = 30.dp, color = colors.onSurfaceVariant)
        }
        if (canOpen) {
            val label = stringResource(R.string.open_map_preview)
            Box(Modifier.matchParentSize().clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = label })
        }
        if (current != null || previous != null) {
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp).clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.78f)).padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                current?.let { DriftLegend(R.string.current_drift, Color(0xFF236DBB), it) }
                previous?.let {
                    DriftLegend(R.string.previous_drift, Color(0xFF687977), it, onClick = { showPrevious = true })
                }
            }
        }
    }
    if (showPrevious && previous != null) {
        LocationSheet(stringResource(R.string.last_simulation_position), onClose = { showPrevious = false }) {
            Spacer(Modifier.height(8.dp))
            CopyableCoordinate(previous)
            Spacer(Modifier.height(12.dp))
            SourceMap(source, MapCamera(previous, 0, null, previousPoint = previous, showCenter = false),
                onPointSelected = null, modifier = Modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
private fun DriftLegend(label: Int, color: Color, point: Coordinate, onClick: (() -> Unit)? = null) {
    val title = stringResource(label)
    Row(Modifier.then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
        .semantics { contentDescription = "$title ${point.display()}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(4.dp).background(color, CircleShape))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

internal fun Coordinate.display(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

@Composable
private fun historyDate(time: Long): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val clock = remember(time, zone) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)) }
    return when (day) {
        today -> stringResource(R.string.history_today, clock)
        today.minusDays(1) -> stringResource(R.string.history_yesterday, clock)
        else -> remember(time, zone) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time)) }
    }
}

