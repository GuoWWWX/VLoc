package app.locationlab.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.locationlab.R
import app.locationlab.platform.CurrentLocation
import app.locationlab.platform.hasForegroundLocation
import app.locationlab.platform.readCurrentLocation
import app.locationlab.simulation.Coordinate
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun MapControls(
    onZoom: (Int) -> Unit,
    onLocate: (Coordinate) -> Unit,
    onNotice: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locate by rememberUpdatedState(onLocate)
    val notice by rememberUpdatedState(onNotice)
    var request by remember { mutableStateOf<Job?>(null) }
    var locating by remember { mutableStateOf(false) }

    fun startLocation() {
        if (request?.isActive == true) return
        notice(null)
        request = scope.launch {
            locating = true
            try {
                when (val result = readCurrentLocation(context)) {
                    is CurrentLocation.Fix -> {
                        locate(result.point)
                        if (result.approximate) notice(R.string.location_approximate)
                    }
                    is CurrentLocation.Failure -> notice(result.message)
                }
            } finally {
                locating = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasForegroundLocation()) startLocation() else notice(R.string.location_permission_denied)
    }
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose { request?.cancel() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MapControl(Glyph.Plus, R.string.map_zoom_in) { onZoom(1) }
        MapControl(Glyph.Minus, R.string.map_zoom_out) { onZoom(-1) }
        val locateLabel = stringResource(if (locating) R.string.locating else R.string.locate_current)
        if (locating) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).semantics { contentDescription = locateLabel },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else {
            LocationIconButton(Glyph.Locate, locateLabel, onClick = {
                if (context.hasForegroundLocation()) startLocation()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, background = MaterialTheme.colorScheme.surface, color = MaterialTheme.colorScheme.primary, visibleSize = 40.dp)
        }
    }
}

@Composable
private fun MapControl(glyph: Glyph, @StringRes label: Int, onClick: () -> Unit) {
    LocationIconButton(glyph, stringResource(label), onClick,
        background = MaterialTheme.colorScheme.surface, color = MaterialTheme.colorScheme.primary, visibleSize = 40.dp)
}
