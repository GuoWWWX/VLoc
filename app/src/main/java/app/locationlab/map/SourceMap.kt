package app.locationlab.map

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.locationlab.R
import app.locationlab.simulation.Coordinate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2

internal data class MapCamera(
    val focus: Coordinate,
    val moveRequest: Int,
    val radiusMeters: Int?,
    val zoomSteps: Int = 0,
    val currentPoint: Coordinate? = null,
    val previousPoint: Coordinate? = null,
    val showCenter: Boolean = true,
)

internal data class MapViewport(val center: Coordinate, val zoom: Double)

@Composable
internal fun SourceMap(
    source: MapSource,
    camera: MapCamera,
    onPointSelected: ((Coordinate) -> Unit)?,
    modifier: Modifier,
    viewport: MapViewport? = null,
    onViewportChanged: ((MapViewport) -> Unit)? = null,
    showAttribution: Boolean = true,
) {
    val initialViewport = remember(source) { viewport }
    key(source, showAttribution) {
        when (source) {
            MapSource.OpenStreetMap -> LocationMap(camera, onPointSelected, modifier, initialViewport, onViewportChanged, showAttribution)
            MapSource.OpenFreeMap, MapSource.VersaTiles ->
                VectorMap(requireNotNull(source.styleUrl), camera, onPointSelected, modifier, initialViewport, onViewportChanged, showAttribution)
            MapSource.AMapPreview, MapSource.BaiduPreview ->
                RasterPreviewMap(source, camera, onPointSelected, modifier, initialViewport, onViewportChanged, showAttribution)
        }
    }
}

@Composable
private fun VectorMap(
    styleUrl: String,
    camera: MapCamera,
    onPointSelected: ((Coordinate) -> Unit)?,
    modifier: Modifier,
    viewport: MapViewport?,
    onViewportChanged: ((MapViewport) -> Unit)?,
    showAttribution: Boolean,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val latestSelection by rememberUpdatedState(onPointSelected)
    val latestViewportChanged by rememberUpdatedState(onViewportChanged)
    var ready by remember(accent) { mutableStateOf(false) }
    var failed by remember(accent) { mutableStateOf(false) }
    val surface = remember(accent) {
        try {
            VectorMapSurface(
                context = context,
                styleUrl = styleUrl,
                accent = accent,
                onPointSelected = onPointSelected?.let { { point -> latestSelection?.invoke(point) } },
                viewport = viewport,
                showAttribution = showAttribution,
                onViewportChanged = { latestViewportChanged?.invoke(it) },
                onLoaded = {
                    ready = true
                    failed = false
                },
                onFailed = { failed = true },
            )
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    if (surface != null) {
        MapLifecycle(surface)
        LaunchedEffect(surface) {
            delay(15_000)
            if (!ready) failed = true
        }
    }

    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (surface != null) {
            AndroidView(
                factory = { surface.view },
                modifier = Modifier.fillMaxSize(),
                update = { surface.render(camera) },
            )
        }
        if (surface == null || failed) {
            val editable = onPointSelected != null
            MapLoadMessage(editable, Modifier.align(if (editable) Alignment.TopCenter else Alignment.Center))
        }
    }
}

@Composable
private fun MapLifecycle(surface: VectorMapSurface) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, surface) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> surface.view.onStart()
                Lifecycle.Event.ON_RESUME -> surface.view.onResume()
                Lifecycle.Event.ON_PAUSE -> surface.view.onPause()
                Lifecycle.Event.ON_STOP -> surface.view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface.view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) surface.view.onStop()
            surface.destroy()
        }
    }
}

internal fun Double.normalizeLongitude(): Double = (this + 540) % 360 - 180

internal fun rangeFill(accent: Int): Int = (accent and 0x00FFFFFF) or 0x24000000

internal fun Context.selectionPin() = requireNotNull(ContextCompat.getDrawable(this, R.drawable.ic_position_pin))

internal fun Context.driftPin(previous: Boolean) = requireNotNull(ContextCompat.getDrawable(
    this, if (previous) R.drawable.ic_drift_previous else R.drawable.ic_drift_current,
))

internal fun MapCamera.previewZoom(view: View, tileSize: Int): Double {
    val radius = radiusMeters ?: return 16.0
    val availablePixels = minOf(view.width, view.height) - 32 * view.resources.displayMetrics.density
    return zoomForRadius(focus.latitude, radius, availablePixels.toDouble(), tileSize)
}

internal fun zoomForRadius(latitude: Double, radius: Int, availablePixels: Double, tileSize: Int): Double {
    val circumference = 2 * PI * 6_378_137 * cos(Math.toRadians(latitude.coerceIn(-85.0, 85.0)))
    return log2(circumference * availablePixels.coerceAtLeast(1.0) / (2.0 * radius * tileSize)).coerceIn(3.0, 18.0)
}

@Composable
internal fun MapLoadMessage(editable: Boolean, modifier: Modifier = Modifier) {
    val inset = if (editable) Modifier.statusBarsPadding().padding(start = 18.dp, top = 82.dp, end = 68.dp)
        else Modifier.padding(horizontal = 12.dp)
    Text(
        stringResource(if (editable) R.string.map_load_error else R.string.map_preview_error),
        modifier.then(inset).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(8.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

