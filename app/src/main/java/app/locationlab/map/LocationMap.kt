package app.locationlab.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.view.doOnLayout
import app.locationlab.R
import app.locationlab.simulation.Coordinate
import java.io.File
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private const val MAX_MAP_LATITUDE = 85.05112878

@Composable
@SuppressLint("ClickableViewAccessibility") // Interactive maps have an accessible coordinate input alongside the map.
internal fun LocationMap(
    camera: MapCamera,
    onPointSelected: ((Coordinate) -> Unit)?,
    modifier: Modifier = Modifier,
    viewport: MapViewport?,
    onViewportChanged: ((MapViewport) -> Unit)?,
    showAttribution: Boolean,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val copyrightUrl = stringResource(R.string.osm_copyright)
    val onSelect by rememberUpdatedState(onPointSelected)
    val reportViewport by rememberUpdatedState(onViewportChanged)
    var mapLoadFailed by remember { mutableStateOf(false) }
    var receivedTile by remember { mutableStateOf(false) }
    val view = remember {
        Configuration.getInstance().apply {
            userAgentValue = "LocationLab/0.4 (app.locationlab)"
            osmdroidBasePath = File(context.cacheDir, "maps")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            tileFileSystemCacheMaxBytes = 32L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 24L * 1024 * 1024
            tileDownloadThreads = 2
        }
        MapView(context).apply {
            setTileSource(
                XYTileSource("OpenStreetMap", 0, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/")),
            )
            tileProvider.tileRequestCompleteHandlers.add(Handler(Looper.getMainLooper()) { message ->
                when (message.what) {
                    MapTileProviderBase.MAPTILE_SUCCESS_ID -> {
                        receivedTile = true
                        mapLoadFailed = false
                    }
                    MapTileProviderBase.MAPTILE_FAIL_ID -> if (!receivedTile) mapLoadFailed = true
                }
                true
            })
            setBackgroundColor(Color.rgb(230, 238, 230))
            setMultiTouchControls(onPointSelected != null)
            setFlingEnabled(onPointSelected != null)
            isFocusable = false
            isFocusableInTouchMode = false
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isHorizontalMapRepetitionEnabled = true
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(viewport?.zoom ?: 16.0)
            controller.setCenter((viewport?.center ?: camera.focus).geoPoint())
            if (onPointSelected == null) {
                setOnTouchListener { _, _ -> true }
            }
        }
    }
    val range = remember(view) { Polygon(view).also(view.overlays::add) }
    val marker = remember(view) {
        Marker(view).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = context.selectionPin()
            view.overlays.add(this)
        }
    }
    val driftMarkers = remember(view) {
        listOf(true, false).map { previous ->
            Marker(view).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = context.driftPin(previous)
                isEnabled = false
                view.overlays.add(this)
            }
        }
    }
    if (onPointSelected != null) {
        DisposableEffect(view) {
            // osmdroid dispatches taps in reverse overlay order.
            val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                    onSelect?.invoke(Coordinate(point.latitude, point.longitude.normalizeLongitude()))
                    return true
                }

                override fun longPressHelper(point: GeoPoint) = false
            })
            view.overlays.add(tapOverlay)
            onDispose { view.overlays.remove(tapOverlay) }
        }
        DisposableEffect(view) {
            val publish = Runnable {
                val center = view.mapCenter
                reportViewport?.invoke(MapViewport(Coordinate(center.latitude.coerceIn(-90.0, 90.0),
                    center.longitude.normalizeLongitude()), view.zoomLevelDouble))
            }
            val listener = object : MapListener {
                private fun schedule(): Boolean {
                    view.removeCallbacks(publish)
                    view.postDelayed(publish, 120)
                    return false
                }
                override fun onScroll(event: ScrollEvent) = schedule()
                override fun onZoom(event: ZoomEvent) = schedule()
            }
            view.addMapListener(listener)
            onDispose {
                view.removeMapListener(listener)
                view.removeCallbacks(publish)
            }
        }
    }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    LifecycleResumeEffect(view) {
        view.onResume()
        onPauseOrDispose { view.onPause() }
    }
    LaunchedEffect(view) {
        delay(12_000)
        if (!receivedTile) mapLoadFailed = true
    }
    Box(modifier) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize(),
            onReset = null,
            onRelease = { it.onDetach() },
        )
        var lastMoveRequest by remember(view) { mutableIntStateOf(camera.moveRequest) }
        LaunchedEffect(view, camera.moveRequest) {
            if (lastMoveRequest != camera.moveRequest) {
                lastMoveRequest = camera.moveRequest
                view.controller.setZoom(view.zoomLevelDouble.coerceAtLeast(16.0).coerceAtMost(view.maxZoomLevel))
                view.controller.setCenter(camera.focus.geoPoint())
            }
        }
        if (onPointSelected == null) {
            LaunchedEffect(view, camera.focus, camera.radiusMeters) {
                view.doOnLayout {
                    view.controller.setZoom(camera.previewZoom(view, tileSize = 256))
                    view.controller.setCenter(camera.focus.geoPoint())
                }
            }
        }
        var lastZoomSteps by remember(view) { mutableIntStateOf(camera.zoomSteps) }
        LaunchedEffect(view, camera.zoomSteps) {
            val delta = camera.zoomSteps - lastZoomSteps
            lastZoomSteps = camera.zoomSteps
            if (delta != 0) {
                view.controller.setZoom((view.zoomLevelDouble + delta).coerceIn(view.minZoomLevel, view.maxZoomLevel))
            }
        }
        LaunchedEffect(view, camera.focus, camera.radiusMeters, camera.showCenter, accent) {
            val point = camera.focus.geoPoint()
            marker.position = point
            marker.isEnabled = camera.showCenter
            range.fillPaint.color = rangeFill(accent)
            range.outlinePaint.color = accent
            range.outlinePaint.strokeWidth = 2 * context.resources.displayMetrics.density
            val radius = camera.radiusMeters
            range.isEnabled = radius != null
            if (radius != null) range.points = Polygon.pointsAsCircle(point, radius.toDouble())
            view.invalidate()
        }
        LaunchedEffect(view, camera.currentPoint, camera.previousPoint) {
            listOf(camera.previousPoint, camera.currentPoint).forEachIndexed { index, point ->
                driftMarkers[index].apply {
                    isEnabled = point != null
                    if (point != null) position = point.geoPoint()
                }
            }
            view.invalidate()
        }
        if (showAttribution) Text(
            text = stringResource(R.string.map_attribution),
            modifier = Modifier.align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { uriHandler.openUri(copyrightUrl) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        if (mapLoadFailed) {
            val editable = onPointSelected != null
            MapLoadMessage(editable, Modifier.align(if (editable) Alignment.TopCenter else Alignment.Center))
        }
    }
}

private fun Coordinate.geoPoint() = GeoPoint(latitude.coerceIn(-MAX_MAP_LATITUDE, MAX_MAP_LATITUDE), longitude)


