package app.locationlab.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.locationlab.R
import app.locationlab.platform.SimulationState
import app.locationlab.simulation.DriftDeadline
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationIcon
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun DriftScheduleStatus(active: SimulationState.Running) {
    if (!active.config.drift) return
    val deadline = active.nextDrift
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var wall by remember(deadline) { mutableStateOf(ZonedDateTime.now()) }
    var elapsed by remember(deadline) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline, lifecycle) {
        if (deadline == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                wall = ZonedDateTime.now()
                elapsed = SystemClock.elapsedRealtime()
                delay(1000)
            }
        }
    }
    val label = if (deadline == null) stringResource(R.string.drift_started_once) else {
        val remaining = deadline.remainingMilliseconds(elapsed, wall)
        val target = when (deadline) {
            is DriftDeadline.Daily -> deadline.time.withZoneSameInstant(wall.zone)
            is DriftDeadline.Interval -> wall.plusNanos(remaining * 1_000_000)
        }
        val pattern = if (target.toLocalDate() == wall.toLocalDate()) "HH:mm:ss" else "M/d HH:mm:ss"
        val nextTime = target.format(DateTimeFormatter.ofPattern(pattern))
        val seconds = (remaining + 999) / 1000
        val clock = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
        val countdown = when {
            seconds == 0L -> stringResource(R.string.drift_due_now)
            seconds >= 86_400 -> stringResource(R.string.drift_countdown_days, seconds / 86_400, clock)
            else -> stringResource(R.string.drift_countdown, clock)
        }
        stringResource(R.string.drift_next, nextTime, countdown)
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LocationIcon(Glyph.History, size = 15.dp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
