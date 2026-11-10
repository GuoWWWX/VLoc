package app.locationlab.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.simulation.DriftIntervalUnit
import app.locationlab.simulation.DriftTiming
import app.locationlab.ui.components.ButtonStyle
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationSheet

private enum class TimingMode(val label: Int, val description: Int) {
    OnStart(R.string.drift_on_start, R.string.drift_on_start_description),
    Interval(R.string.drift_repeat_interval, R.string.drift_interval_description),
    Daily(R.string.drift_daily_time, R.string.drift_daily_description),
}

@Composable
internal fun DriftTiming.label(): String = when (this) {
    DriftTiming.OnStart -> stringResource(R.string.drift_on_start)
    is DriftTiming.Interval -> stringResource(R.string.drift_interval_summary, amount, stringResource(unit.label))
    is DriftTiming.Daily -> stringResource(R.string.drift_daily_summary, hour, minute)
}

@Composable
internal fun DriftTimingDialog(current: DriftTiming, onDismiss: () -> Unit, onConfirm: (DriftTiming) -> Unit) {
    val interval = current as? DriftTiming.Interval
    val daily = current as? DriftTiming.Daily
    var mode by rememberSaveable {
        mutableStateOf(when (current) {
            DriftTiming.OnStart -> TimingMode.OnStart
            is DriftTiming.Interval -> TimingMode.Interval
            is DriftTiming.Daily -> TimingMode.Daily
        })
    }
    var amount by rememberSaveable { mutableStateOf((interval?.amount ?: 1).toString()) }
    var unit by rememberSaveable { mutableStateOf(interval?.unit ?: DriftIntervalUnit.Hours) }
    var hour by rememberSaveable { mutableIntStateOf(daily?.hour ?: 8) }
    var minute by rememberSaveable { mutableIntStateOf(daily?.minute ?: 30) }
    val count = amount.toIntOrNull()?.takeIf { it in 1..9999 }
    val chosen = when (mode) {
        TimingMode.OnStart -> DriftTiming.OnStart
        TimingMode.Interval -> count?.let { DriftTiming.Interval(it, unit) }
        TimingMode.Daily -> DriftTiming.Daily(hour, minute)
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    LocationSheet(stringResource(R.string.drift_timing), onDismiss) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.selectableGroup()) {
                for (option in TimingMode.entries) {
                    val selected = option == mode
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                        .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier)
                        .selectable(selected, role = Role.RadioButton, onClick = { mode = option })
                        .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null)
                        Column {
                            Text(stringResource(option.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(option.description), color = colors.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
            when (mode) {
                TimingMode.OnStart -> Unit
                TimingMode.Interval -> Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    NumberField(amount, { amount = it }, count != null, Modifier.weight(1f))
                    UnitPicker(unit) { unit = it }
                }
                TimingMode.Daily -> Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.every_day), Modifier.weight(1f), fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                            hour = selectedHour
                            minute = selectedMinute
                        }, hour, minute, true).show()
                    }) { Text(stringResource(R.string.drift_clock_display, hour, minute)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LocationButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f),
                    style = ButtonStyle.Secondary, minimumHeight = 42.dp)
                LocationButton(stringResource(R.string.save), { if (chosen != null) onConfirm(chosen) },
                    Modifier.weight(1f), enabled = chosen != null, minimumHeight = 42.dp)
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, valid: Boolean, modifier: Modifier) {
    OutlinedTextField(value, { if (it.length <= 4 && it.all { character -> character in '0'..'9' }) onChange(it) },
        modifier = modifier, label = { Text(stringResource(R.string.drift_interval_amount)) }, singleLine = true, isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun UnitPicker(unit: DriftIntervalUnit, onUnit: (DriftIntervalUnit) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(unit.label) + " ▾") }
        DropdownMenu(open, { open = false }) {
            for (option in DriftIntervalUnit.entries) {
                DropdownMenuItem(text = { Text(stringResource(option.label)) },
                    onClick = { onUnit(option); open = false })
            }
        }
    }
}

private val DriftIntervalUnit.label: Int get() = when (this) {
    DriftIntervalUnit.Seconds -> R.string.drift_unit_seconds
    DriftIntervalUnit.Minutes -> R.string.drift_unit_minutes
    DriftIntervalUnit.Hours -> R.string.drift_unit_hours
    DriftIntervalUnit.Days -> R.string.drift_unit_days
}
