package app.locationlab.map

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import app.locationlab.simulation.Coordinate
import kotlinx.coroutines.delay

@Composable
internal fun RasterPreviewMap(
    source: MapSource,
    camera: MapCamera,
    onPointSelected: ((Coordinate) -> Unit)?,
    modifier: Modifier,
    viewport: MapViewport?,
    onViewportChanged: ((MapViewport) -> Unit)?,
    showAttribution: Boolean,
) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onPointSelected)
    val viewportChanged by rememberUpdatedState(onViewportChanged)
    var receivedTile by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val surface = remember {
        RasterPreviewSurface(
            context, source, onPointSelected != null, viewport, showAttribution,
            onSelect = { select?.invoke(it) },
            onViewportChanged = { viewportChanged?.invoke(it) },
            onTiles = { visible ->
                receivedTile = receivedTile || visible
                failed = !receivedTile
            },
        )
    }
    LaunchedEffect(surface) {
        delay(12_000)
        if (!receivedTile) failed = true
    }
    Box(modifier) {
        AndroidView(
            factory = { surface.view },
            modifier = Modifier.fillMaxSize(),
            onReset = null,
            onRelease = { surface.close() },
            update = { surface.render(camera) },
        )
        if (failed) {
            val editable = onPointSelected != null
            MapLoadMessage(editable, Modifier.align(if (editable) Alignment.TopCenter else Alignment.Center))
        }
    }
}

private class RasterPreviewSurface(
    context: Context,
    private val source: MapSource,
    private val editable: Boolean,
    private val viewport: MapViewport?,
    private val showAttribution: Boolean,
    private val onSelect: (Coordinate) -> Unit,
    private val onViewportChanged: (MapViewport) -> Unit,
    private val onTiles: (Boolean) -> Unit,
) {
    private val tileEndpoint = when (source) {
        MapSource.AMapPreview -> "https://wprd01.is.autonavi.com/appmaptile"
        MapSource.BaiduPreview -> "https://maponline0.bdimg.com/tile/"
        MapSource.GooglePreview -> "https://mt1.google.com/vt"
        else -> error("Unsupported WebView map source: $source")
    }.toUri()
    private var camera: MapCamera? = null
    private var rendered: MapCamera? = null
    private var ready = false
    private var closed = false

    @SuppressLint("SetJavaScriptEnabled") // Only the packaged document and scripts execute in this WebView.
    val view = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportZoom(false)
        isFocusable = false
        isFocusableInTouchMode = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        addJavascriptInterface(Bridge(), BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (request.method == "GET" && url.scheme == "https") {
                    if (url.host == LOCAL_HOST) {
                        val asset = ASSETS[url.path]
                        if (asset != null) {
                            return WebResourceResponse(
                                asset.second, "UTF-8", context.assets.open("map-preview/${asset.first}"),
                            )
                        }
                    }
                    if (!request.isForMainFrame && url.host == tileEndpoint.host && url.path == tileEndpoint.path) return null
                }
                return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url == PAGE_URL && !ready && !closed) {
                    ready = true
                    camera?.let(::render)
                }
            }
        }
        loadUrl(PAGE_URL)
    }

    fun render(next: MapCamera) {
        camera = next
        if (!ready || closed || next == rendered) return
        val previous = rendered
        val move = previous?.moveRequest != next.moveRequest
        val zoom = next.zoomSteps - (previous?.zoomSteps ?: next.zoomSteps)
        fun point(value: Coordinate?): String = value?.let { "[${it.latitude},${it.longitude}]" } ?: "null"
        val initialViewport = viewport?.let { "[${it.center.latitude},${it.center.longitude},${it.zoom}]" } ?: "null"
        view.evaluateJavascript(
            "mapPreview.render('${source.name}', $editable, ${next.focus.latitude}, ${next.focus.longitude}, " +
                "${next.radiusMeters ?: 0}, $move, $zoom, ${point(next.currentPoint)}, ${point(next.previousPoint)}, " +
                "$initialViewport, $showAttribution, ${next.showCenter})",
            null,
        )
        rendered = next
    }

    fun close() {
        closed = true
        view.stopLoading()
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.destroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun pick(latitude: Double, longitude: Double) {
            if (!editable || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
            view.post { if (!closed) onSelect(Coordinate(latitude, longitude)) }
        }

        @JavascriptInterface
        fun tiles(visible: Boolean) {
            view.post { if (!closed) onTiles(visible) }
        }

        @JavascriptInterface
        fun viewport(latitude: Double, longitude: Double, zoom: Double) {
            if (!editable || latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || !zoom.isFinite()) return
            view.post { if (!closed) onViewportChanged(MapViewport(Coordinate(latitude, longitude), zoom)) }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "LocationLabBridge"
        const val LOCAL_HOST = "appassets.androidplatform.net"
        const val PAGE_URL = "https://$LOCAL_HOST/map-preview/index.html"
        val ASSETS = mapOf(
            "/map-preview/index.html" to ("index.html" to "text/html"),
            "/map-preview/map.js" to ("map.js" to "application/javascript"),
            "/map-preview/leaflet.js" to ("leaflet.js" to "application/javascript"),
            "/map-preview/leaflet.css" to ("leaflet.css" to "text/css"),
            "/map-preview/chinese-crs.js" to ("chinese-crs.js" to "application/javascript"),
        )
    }
}
