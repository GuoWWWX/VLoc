package app.locationlab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val FavoriteYellow = Color(0xFFF8D96A)
internal val DestructiveRed = Color(0xFFA33834)

internal enum class ButtonStyle { Primary, Secondary, Destructive }

@Composable
internal fun LocationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Primary,
    minimumHeight: Dp = 52.dp,
    glyph: Glyph? = null,
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (style) {
        ButtonStyle.Primary -> colors.primary to colors.onPrimary
        ButtonStyle.Secondary -> colors.background to colors.primary
        ButtonStyle.Destructive -> DestructiveRed to Color.White
    }
    val interaction = remember { MutableInteractionSource() }
    val ink by animateColorAsState(if (enabled || loading) foreground else colors.onSurface.copy(alpha = 0.38f),
        tween(180), label = "Button ink")
    val fill by animateColorAsState(if (enabled || loading) background else colors.onSurface.copy(alpha = 0.12f),
        tween(180), label = "Button fill")
    Box(
        modifier.heightIn(min = maxOf(48.dp, minimumHeight))
            .clickable(interaction, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minimumHeight).clip(CircleShape)
                .background(fill)
                .indication(interaction, ripple(color = ink))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = ink, strokeWidth = 2.dp)
            else if (glyph != null) LocationIcon(glyph, size = 15.dp, color = ink)
            Text(label, color = ink, fontSize = if (minimumHeight < 48.dp) 13.sp else 14.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun LocationIconButton(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    color: Color = MaterialTheme.colorScheme.onSurface,
    visibleSize: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visibleSize).clip(shape).background(background)
            .indication(interaction, ripple(color = color)),
            contentAlignment = Alignment.Center) {
            LocationIcon(glyph, size = iconSize, color = color)
        }
    }
}

@Composable
internal fun LocationPill(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = true,
    minimumHeight: Dp = 26.dp,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.heightIn(min = minimumHeight).clip(CircleShape)
            .background(if (highlighted) colors.secondaryContainer else colors.surface)
            .padding(horizontal = 10.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (highlighted) colors.onSecondaryContainer else colors.onSurfaceVariant,
            fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SegmentedIconControl(
    rightSelected: Boolean,
    leftLabel: String,
    rightLabel: String,
    leftIcon: Glyph,
    rightIcon: Glyph,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val leftInteraction = remember { MutableInteractionSource() }
    val rightInteraction = remember { MutableInteractionSource() }
    Box(modifier.size(96.dp, 48.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.size(80.dp, 36.dp).clip(shape).background(colors.surface)) {
            for (right in listOf(false, true)) {
                val selected = right == rightSelected
                val interaction = if (right) rightInteraction else leftInteraction
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (selected) colors.secondaryContainer else colors.surface)
                        .indication(interaction, ripple(color = colors.primary)),
                    contentAlignment = Alignment.Center,
                ) {
                    LocationIcon(if (right) rightIcon else leftIcon, size = 17.dp,
                        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.size(1.dp, 22.dp).background(colors.outlineVariant))
        Box(Modifier.size(80.dp, 36.dp).border(1.dp, colors.outlineVariant, shape))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            for (right in listOf(false, true)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(
                            selected = right == rightSelected,
                            interactionSource = if (right) rightInteraction else leftInteraction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(right) },
                        )
                        .semantics { contentDescription = if (right) rightLabel else leftLabel },
                )
            }
        }
    }
}
