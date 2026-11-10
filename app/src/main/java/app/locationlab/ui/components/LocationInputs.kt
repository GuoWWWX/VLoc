package app.locationlab.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LocationSwitch(
    checked: Boolean,
    label: String,
    enabled: Boolean = true,
    showLabel: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val thumb by animateDpAsState(if (checked) 18.dp else 2.dp, label = "Location switch thumb")
    Row(
        Modifier.widthIn(min = 44.dp).heightIn(min = 44.dp).toggleable(
            value = checked, enabled = enabled, interactionSource = interaction, indication = null,
            role = Role.Switch, onValueChange = onCheckedChange,
        )
            .semantics { contentDescription = label },
        horizontalArrangement = if (showLabel) Arrangement.spacedBy(7.dp) else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp, 20.dp).clip(CircleShape)
            .background(if (checked) colors.primary.copy(alpha = if (enabled) 1f else 0.48f) else colors.outlineVariant)
            .indication(interaction, ripple(color = if (checked) colors.onPrimary else colors.primary)),
            contentAlignment = Alignment.CenterStart) {
            Box(Modifier.offset { IntOffset(thumb.roundToPx(), 0) }.size(16.dp)
                .clip(CircleShape).background(colors.surface))
        }
        if (showLabel) Text(label, color = if (enabled) colors.primary else colors.onSurfaceVariant, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationSlider(value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = label },
        thumb = {
            Box(Modifier.size(16.dp).clip(CircleShape).background(colors.surface)
                .border(1.dp, colors.primary, CircleShape))
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val start = Offset(0f, center.y)
                drawLine(colors.outlineVariant, start, Offset(size.width, center.y), 4.dp.toPx(), StrokeCap.Round)
                drawLine(colors.primary, start, Offset(size.width * fraction, center.y), 4.dp.toPx(), StrokeCap.Round)
            }
        },
    )
}
