package app.locationlab.map

import android.content.Context
import android.os.Bundle
import androidx.core.view.doOnLayout
import app.locationlab.simulation.Coordinate
import kotlin.math.PI
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal class VectorMapSurface(
    context: Context,
    styleUrl: String,
    private val accent: Int,
    onPointSelected: ((Coordinate) -> Unit)?,
    private val viewport: MapViewport?,
    showAttribution: Boolean,
    onViewportChanged: (MapViewport) -> Unit,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var camera: MapCamera? = null
    private var destroyed = false
    private val editable = onPointSelected != null

    init {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        view = MapView(context, options).apply { onCreate(Bundle()) }
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.addOnDidFailLoadingMapListener { if (!destroyed) onFailed() }
        view.addOnDidFinishLoadingMapListener { if (!destroyed) onLoaded() }
        view.getMapAsync { mapLibre ->
            if (destroyed) return@getMapAsync
            map = mapLibre
            mapLibre.uiSettings.apply {
                val canMove = onPointSelected != null
                isScrollGesturesEnabled = canMove
                isZoomGesturesEnabled = canMove
                isFlingVelocityAnimationEnabled = canMove
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isAttributionEnabled = showAttribution
            }
            if (onPointSelected != null) {
                mapLibre.addOnMapClickListener { point ->
                    onPointSelected(point.coordinate())
                    true
                }
                mapLibre.addOnCameraIdleListener {
                    val position = mapLibre.cameraPosition
                    position.target?.let { onViewportChanged(MapViewport(it.coordinate(), position.zoom)) }
                }
            }
            camera?.let {
                if (editable) move(mapLibre, viewport?.center ?: it.focus, viewport?.zoom ?: 16.0)
                else fitPreview(mapLibre)
            }
            mapLibre.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                if (!destroyed) {
                    style.addSource(GeoJsonSource(RANGE_SOURCE))
                    style.addLayer(FillLayer("locationlab-range-fill", RANGE_SOURCE).withProperties(
                        PropertyFactory.fillColor(rangeFill(accent)),
                    ))
                    style.addLayer(LineLayer("locationlab-range-outline", RANGE_SOURCE).withProperties(
                        PropertyFactory.lineColor(accent),
                        PropertyFactory.lineWidth(2f),
                    ))
                    style.addSource(GeoJsonSource(SELECTION_SOURCE))
                    style.addImage(SELECTION_IMAGE, context.selectionPin())
                    style.addLayer(SymbolLayer("locationlab-marker", SELECTION_SOURCE).withProperties(
                        PropertyFactory.iconImage(SELECTION_IMAGE),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconSize(if (editable) 1f else 0.68f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    ))
                    addDriftLayer(style, context, PREVIOUS_SOURCE, previous = true)
                    addDriftLayer(style, context, CURRENT_SOURCE, previous = false)
                    camera?.let {
                        drawSelection(mapLibre, it.focus)
                        drawPoint(style, PREVIOUS_SOURCE, it.previousPoint)
                        drawPoint(style, CURRENT_SOURCE, it.currentPoint)
                    }
                }
            }
        }
    }

    fun render(camera: MapCamera) {
        val previous = this.camera
        this.camera = camera
        val mapLibre = map ?: return
        if (!editable) {
            if (previous?.focus != camera.focus || previous.radiusMeters != camera.radiusMeters) fitPreview(mapLibre)
        } else if (previous == null) {
            move(mapLibre, viewport?.center ?: camera.focus, viewport?.zoom ?: 16.0)
        } else if (previous.moveRequest != camera.moveRequest) {
            move(mapLibre, camera.focus, mapLibre.cameraPosition.zoom.coerceAtLeast(16.0))
        } else {
            val zoomDelta = camera.zoomSteps - previous.zoomSteps
            if (zoomDelta != 0) mapLibre.moveCamera(CameraUpdateFactory.zoomBy(zoomDelta.toDouble()))
        }
        if (previous?.focus != camera.focus || previous.radiusMeters != camera.radiusMeters ||
            previous.showCenter != camera.showCenter
        ) {
            drawSelection(mapLibre, camera.focus)
        }
        mapLibre.style?.let { style ->
            if (previous?.previousPoint != camera.previousPoint) drawPoint(style, PREVIOUS_SOURCE, camera.previousPoint)
            if (previous?.currentPoint != camera.currentPoint) drawPoint(style, CURRENT_SOURCE, camera.currentPoint)
        }
    }

    private fun move(mapLibre: MapLibreMap, focus: Coordinate, zoom: Double) {
        val target = LatLng(focus.latitude.coerceIn(-85.05112878, 85.05112878), focus.longitude)
        mapLibre.moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoom))
    }

    private fun fitPreview(mapLibre: MapLibreMap) {
        view.doOnLayout {
            if (!destroyed) camera?.let { move(mapLibre, it.focus, it.previewZoom(view, tileSize = 512)) }
        }
    }

    private fun addDriftLayer(style: Style, context: Context, id: String, previous: Boolean) {
        style.addSource(GeoJsonSource(id))
        style.addImage("$id-image", context.driftPin(previous))
        style.addLayer(SymbolLayer("$id-layer", id).withProperties(
            PropertyFactory.iconImage("$id-image"),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ))
    }

    private fun drawPoint(style: Style, id: String, point: Coordinate?) {
        val source = style.getSourceAs<GeoJsonSource>(id) ?: return
        if (point == null) source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        else source.setGeoJson(Point.fromLngLat(point.longitude, point.latitude))
    }

    private fun drawSelection(mapLibre: MapLibreMap, center: Coordinate) {
        val style = mapLibre.style ?: return
        drawPoint(style, SELECTION_SOURCE, center.takeIf { camera?.showCenter != false })
        val source = style.getSourceAs<GeoJsonSource>(RANGE_SOURCE) ?: return
        val radius = camera?.radiusMeters
        if (radius == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            return
        }
        val ring = (0 until 64).map { index ->
            val edge = center.moved(radius.toDouble(), index * 2 * PI / 64)
            val longitude = center.longitude + (edge.longitude - center.longitude).normalizeLongitude()
            Point.fromLngLat(longitude, edge.latitude)
        }
        source.setGeoJson(Polygon.fromLngLats(listOf(ring + ring.first())))
    }

    fun destroy() {
        destroyed = true
        view.onDestroy()
    }

    private fun LatLng.coordinate() = Coordinate(latitude, longitude.normalizeLongitude())

    private companion object {
        const val RANGE_SOURCE = "locationlab-range"
        const val SELECTION_SOURCE = "locationlab-selection"
        const val SELECTION_IMAGE = "locationlab-position-pin"
        const val CURRENT_SOURCE = "locationlab-drift-current"
        const val PREVIOUS_SOURCE = "locationlab-drift-previous"
    }
}
