package app.locationlab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.data.HistoryEntry
import app.locationlab.map.MapCamera
import app.locationlab.map.MapSource
import app.locationlab.map.MapSourcePicker
import app.locationlab.map.MapViewport
import app.locationlab.map.SourceMap
import app.locationlab.platform.PlaceMatch
import app.locationlab.platform.decimalCoordinate
import app.locationlab.platform.parseCoordinateSearch
import app.locationlab.platform.reversePlace
import app.locationlab.platform.searchPlaces
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DEFAULT_DRIFT_RADIUS
import app.locationlab.simulation.DriftTiming
import app.locationlab.simulation.SimulationConfig
import app.locationlab.simulation.decodeDriftTiming
import app.locationlab.simulation.encode
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton
import app.locationlab.ui.components.LocationPill
import app.locationlab.ui.components.LocationSlider
import app.locationlab.ui.components.LocationSwitch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val initialMapCenter = Coordinate(22.543096, 114.057865)
private val timingSaver = Saver<DriftTiming, String>(save = { it.encode() }, restore = { decodeDriftTiming(it) })

@Composable
internal fun PickerScreen(
    initial: SimulationConfig?,
    initialName: String?,
    adding: Boolean,
    history: List<HistoryEntry>,
    source: MapSource,
    onMapSource: (MapSource) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (SimulationConfig, String?, onFailure: () -> Unit) -> Unit,
) {
    val initialCenter = initial?.center ?: initialMapCenter
    var latitude by rememberSaveable { mutableDoubleStateOf(initialCenter.latitude) }
    var longitude by rememberSaveable { mutableDoubleStateOf(initialCenter.longitude) }
    var drift by rememberSaveable { mutableStateOf(initial?.drift ?: false) }
    var radius by rememberSaveable { mutableIntStateOf(initial?.radiusMeters ?: DEFAULT_DRIFT_RADIUS) }
    var timing by rememberSaveable(stateSaver = timingSaver) { mutableStateOf(initial?.timing ?: DriftTiming.OnStart) }
    var positionRequest by rememberSaveable { mutableIntStateOf(0) }
    var zoomSteps by rememberSaveable { mutableIntStateOf(0) }
    var locationNotice by rememberSaveable { mutableStateOf<Int?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var placeName by rememberSaveable { mutableStateOf(initialName) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<PlaceMatch>()) }
    var searchMessage by remember { mutableStateOf<Int?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    val center = Coordinate(latitude, longitude)
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val closeSearch: () -> Unit = {
        searching = false
        focus.clearFocus()
        keyboard?.hide()
    }
    val panelExpanded = expanded && !searching
    fun selectPoint(point: Coordinate, name: String? = null, moveCamera: Boolean = false, searchText: String = "") {
        if (point != center || name != null) placeName = name
        latitude = point.latitude
        longitude = point.longitude
        if (moveCamera) {
            viewport = MapViewport(point, (viewport?.zoom ?: 16.0).coerceAtLeast(16.0))
            positionRequest++
        }
        query = searchText
        locationNotice = null
        expanded = false
        if (searching) closeSearch()
    }
    BackHandler(enabled = searching) { closeSearch() }
    LaunchedEffect(searching) {
        if (searching) keyboard?.show()
    }
    LaunchedEffect(center) {
        if (placeName == null) {
            delay(350)
            placeName = reversePlace(context, center)
        }
    }
    val coordinateLabel = stringResource(R.string.search_coordinate)
    LaunchedEffect(query, searching, retry, history) {
        results = emptyList()
        searchMessage = null
        val input = query.trim()
        if (!searching || input.isEmpty()) return@LaunchedEffect
        val coordinate = parseCoordinateSearch(input)
        if (coordinate != null) {
            results = listOf(PlaceMatch(coordinate, coordinateLabel, coordinate.display()))
        } else {
            val local = history.asSequence()
                .filter { it.placeName?.contains(input, ignoreCase = true) == true }
                .distinctBy { it.coordinate }
                .take(5)
                .map { PlaceMatch(it.coordinate, requireNotNull(it.placeName), it.coordinate.display()) }
                .toList()
            results = local
            if (local.isEmpty()) searchMessage = R.string.searching_place
            delay(350)
            val matches = searchPlaces(context, input)
            results = (local + matches.orEmpty()).distinctBy { it.point }.take(5)
            searchMessage = when {
                results.isNotEmpty() -> null
                matches == null -> R.string.search_place_unavailable
                matches.isEmpty() -> R.string.search_place_empty
                else -> null
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val panelHeight by animateDpAsState(targetValue = minOf(when {
            !panelExpanded -> 156.dp
            drift -> 344.dp
            else -> 220.dp
        }, maxHeight * 0.68f), animationSpec = tween(240, easing = FastOutSlowInEasing), label = "Picker panel height")
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SourceMap(
                    source = source,
                    camera = MapCamera(center, positionRequest, radius.takeIf { drift }, zoomSteps),
                    onPointSelected = { point -> selectPoint(point) },
                    modifier = Modifier.matchParentSize(),
                    viewport = viewport,
                    onViewportChanged = { viewport = it },
                )
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding()
                        .padding(start = 18.dp, top = 24.dp, end = 18.dp)
                        .fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                        .background(colors.surface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocationIconButton(Glyph.Back, stringResource(R.string.back),
                        if (searching) closeSearch else onCancel,
                        color = colors.primary, iconSize = 21.dp)
                    LocationSearchField(query, { query = it }, { searching = true }, Modifier.weight(1f))
                    MapSourcePicker(source, onMapSource) { open ->
                        LocationIconButton(Glyph.Layers, stringResource(R.string.map_source), open,
                            color = colors.primary, iconSize = 19.dp)
                    }
                }
                if (!searching) {
                    MapControls(
                        onZoom = { zoomSteps += it },
                        onLocate = { point -> selectPoint(point, moveCamera = true) },
                        onNotice = { locationNotice = it },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                            .padding(top = 96.dp, end = 12.dp),
                    )
                    if (expanded || locationNotice != null) LocationPill(
                        stringResource(locationNotice ?: R.string.map_drag_hint),
                        Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        highlighted = false, minimumHeight = 27.dp,
                    )
                } else if (results.isNotEmpty() || searchMessage != null) {
                    LocationSearchResults(results, searchMessage,
                        onSelect = { match ->
                            val name = match.name.takeUnless { it == coordinateLabel }
                            selectPoint(match.point, name, moveCamera = true,
                                searchText = name ?: match.point.display())
                        },
                        onRetry = { retry++ },
                        onMap = closeSearch,
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                            .padding(start = 18.dp, top = 84.dp, end = 18.dp),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().height(panelHeight).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.surface).navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
            ) {
                PanelHandle(panelExpanded) {
                    if (searching) closeSearch()
                    expanded = it
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(placeName ?: stringResource(R.string.coordinate_title), color = colors.onSurface,
                                fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            CopyableCoordinate(center)
                        }
                        if (panelExpanded) SmallControl(stringResource(R.string.edit), onClick = { editing = true })
                    }
                    AnimatedVisibility(visible = panelExpanded, enter = fadeIn(tween(180)), exit = fadeOut(tween(100))) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                val label = stringResource(R.string.drift)
                                Text(label, Modifier.weight(1f), color = colors.onSurface,
                                    fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                                LocationSwitch(drift, label) { drift = it }
                            }
                            if (drift) DriftSettings(radius, timing, { radius = it }, { timing = it })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LocationButton(
                    stringResource(if (adding) R.string.save_new_location else R.string.confirm_location),
                    onClick = {
                        if (!saving) {
                            saving = true
                            onConfirm(SimulationConfig(center, drift, radius, timing), placeName) { saving = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    minimumHeight = if (adding) 46.dp else 52.dp,
                )
            }
        }
    }
    if (editing) {
        CoordinateDialog(
            center = center,
            onCancel = { editing = false },
            onConfirm = { point ->
                selectPoint(point, moveCamera = true)
                editing = false
            },
        )
    }
}

@Composable
private fun PanelHandle(expanded: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(if (expanded) R.string.collapse_location_options else R.string.expand_location_options)
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(48.dp, 24.dp)
                .pointerInput(expanded) {
                    var distance = 0f
                    detectVerticalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                        if (distance < -12.dp.toPx()) onChange(true)
                        else if (distance > 12.dp.toPx()) onChange(false)
                    }) { change, delta ->
                        change.consume()
                        distance += delta
                    }
                }
                .clickable(interaction, indication = null, role = Role.Button, onClick = { onChange(!expanded) })
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(42.dp, 4.dp).clip(CircleShape).background(colors.outlineVariant)
                .indication(interaction, ripple(color = colors.primary)))
        }
    }
}

@Composable
private fun DriftSettings(radius: Int, timing: DriftTiming, onRadius: (Int) -> Unit, onTiming: (DriftTiming) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showTiming by rememberSaveable { mutableStateOf(false) }
    var editRadius by rememberSaveable { mutableStateOf(false) }
    val radiusLabel = stringResource(R.string.radius_title)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(radiusLabel, Modifier.weight(1f), color = colors.onSurface, fontSize = 12.sp,
            lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        SmallControl(stringResource(R.string.radius_value, radius), onClick = { editRadius = true },
            emphasized = true, leading = Glyph.Edit)
    }
    LocationSlider(radius.toFloat(), 1f..5000f, radiusLabel) { onRadius(it.roundToInt()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.radius_value, 1), color = colors.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        Text(stringResource(R.string.radius_value, 5000), color = colors.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
    }
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.drift_timing), Modifier.weight(1f), color = colors.onSurface,
            fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        SmallControl(timing.label(), onClick = { showTiming = true }, trailing = Glyph.Down)
    }
    if (showTiming) DriftTimingDialog(timing, { showTiming = false }) { chosen ->
        onTiming(chosen)
        showTiming = false
    }
    if (editRadius) {
        var input by rememberSaveable { mutableStateOf(radius.toString()) }
        val value = input.toIntOrNull()?.takeIf { it in 1..5000 }
        AlertDialog(
            onDismissRequest = { editRadius = false },
            title = { Text(radiusLabel) },
            text = {
                OutlinedTextField(input, { next ->
                    if (next.length <= 4 && next.all { it in '0'..'9' }) input = next
                }, label = { Text(stringResource(R.string.radius)) }, isError = value == null,
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = {
                TextButton(enabled = value != null, onClick = { if (value != null) onRadius(value); editRadius = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { editRadius = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SmallControl(
    label: String,
    onClick: () -> Unit,
    trailing: Glyph? = null,
    emphasized: Boolean = false,
    leading: Glyph? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 29.dp).clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            LocationIcon(leading, size = 13.dp, color = colors.primary)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (trailing == null) colors.primary else colors.onSurface,
            fontSize = if (emphasized) 16.sp else 11.sp, lineHeight = if (emphasized) 23.sp else 16.sp,
            fontWeight = FontWeight.SemiBold)
        if (trailing != null) {
            Spacer(Modifier.width(5.dp))
            LocationIcon(trailing, size = 11.dp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoordinateDialog(center: Coordinate, onCancel: () -> Unit, onConfirm: (Coordinate) -> Unit) {
    var latitude by rememberSaveable { mutableStateOf(center.latitude.toString()) }
    var longitude by rememberSaveable { mutableStateOf(center.longitude.toString()) }
    val lat = latitude.decimalCoordinate()?.takeIf { it in -90.0..90.0 }
    val lon = longitude.decimalCoordinate()?.takeIf { it in -180.0..180.0 }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.coordinate_input)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    latitude, { latitude = it }, label = { Text(stringResource(R.string.lat)) },
                    singleLine = true, isError = lat == null,
                )
                OutlinedTextField(
                    longitude, { longitude = it }, label = { Text(stringResource(R.string.lon)) },
                    singleLine = true, isError = lon == null,
                )
                if (lat == null || lon == null) {
                    Text(stringResource(R.string.coordinate_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = lat != null && lon != null,
                onClick = { if (lat != null && lon != null) onConfirm(Coordinate(lat, lon)) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
