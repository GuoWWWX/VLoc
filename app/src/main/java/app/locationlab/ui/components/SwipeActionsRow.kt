package app.locationlab.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
internal fun SwipeActionsRow(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actionsWidth: Dp,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val latestExpanded by rememberUpdatedState(expanded)
    val changeExpanded by rememberUpdatedState(onExpandedChange)
    var dragWidth by remember { mutableStateOf<Dp?>(null) }
    val reveal by animateDpAsState(
        targetValue = dragWidth ?: if (expanded) actionsWidth else 0.dp,
        animationSpec = if (dragWidth != null) snap() else tween(160),
        label = "History actions reveal",
    )
    val showingActions by remember { derivedStateOf { reveal > 0.dp } }

    Box(
        modifier.fillMaxWidth().clipToBounds().pointerInput(actionsWidth) {
            val maximum = actionsWidth.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val wasExpanded = latestExpanded
                var width = if (wasExpanded) maximum else 0f
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overslop ->
                    if (wasExpanded || overslop < 0) {
                        change.consume()
                        width = (width - overslop).coerceIn(0f, maximum)
                        dragWidth = width.toDp()
                    }
                } ?: return@awaitEachGesture

                try {
                    val completed = horizontalDrag(drag.id) { change ->
                        width = (width - change.positionChange().x).coerceIn(0f, maximum)
                        dragWidth = width.toDp()
                        change.consume()
                    }
                    val threshold = maximum * (if (wasExpanded) 0.75f else 0.28f)
                    if (completed) changeExpanded(width >= threshold)
                } finally {
                    dragWidth = null
                }
            }
        },
    ) {
        content()
        if (showingActions) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Row(Modifier.offset { IntOffset((actionsWidth - reveal).roundToPx(), 0) }
                    .width(actionsWidth).fillMaxHeight(), content = actions)
            }
        }
    }
}
