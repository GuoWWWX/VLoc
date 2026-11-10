package app.locationlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.platform.PlaceMatch
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.ButtonStyle
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon

@Composable
internal fun LocationSearchField(
    query: String,
    onQuery: (String) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(R.string.search_place_hint)
    val clearLabel = stringResource(R.string.clear_search)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.focusRequester(focus).onFocusChanged { if (it.isFocused) onFocus() },
        singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp, lineHeight = 18.sp),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { input ->
            Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(11.dp))
                .background(colors.background).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LocationIcon(Glyph.Search, size = 16.dp, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(hint, color = colors.onSurfaceVariant, fontSize = 13.sp,
                        lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    input()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .clickable(role = Role.Button) {
                                onQuery("")
                                focus.requestFocus()
                                keyboard?.show()
                            }
                            .semantics { contentDescription = clearLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        LocationIcon(Glyph.Close, size = 16.dp, color = colors.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

@Composable
internal fun LocationSearchResults(
    results: List<PlaceMatch>,
    message: Int?,
    onSelect: (PlaceMatch) -> Unit,
    onRetry: () -> Unit,
    onMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().heightIn(max = 240.dp).clip(shape)
        .background(colors.surface).border(1.dp, colors.outlineVariant, shape).verticalScroll(rememberScrollState())) {
        if (message != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(message), color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                if (message != R.string.searching_place) {
                    Spacer(Modifier.height(7.dp))
                    Text(stringResource(if (message == R.string.search_place_empty) R.string.search_empty_help
                        else R.string.search_unavailable_help), color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocationButton(stringResource(R.string.pick_on_map), onMap, Modifier.width(126.dp),
                            style = ButtonStyle.Secondary, minimumHeight = 34.dp)
                        if (message == R.string.search_place_unavailable) {
                            LocationButton(stringResource(R.string.retry_search), onRetry, Modifier.width(78.dp),
                                style = ButtonStyle.Secondary, minimumHeight = 34.dp, glyph = Glyph.Refresh)
                        }
                    }
                }
            }
        }
        results.forEachIndexed { index, result ->
            if (index != 0) HorizontalDivider(Modifier.padding(start = 58.dp, end = 14.dp), color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable { onSelect(result) }
                .padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center) {
                    LocationIcon(Glyph.Position, size = 19.dp, color = colors.primary)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                    Text(result.name, color = colors.onSurface, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                    if (result.area == result.point.display()) {
                        CopyableCoordinate(result.point, onClick = { onSelect(result) })
                    } else if (result.area.isNotBlank()) {
                        Text(result.area, color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                LocationIcon(Glyph.Next, size = 16.dp, color = colors.onSurfaceVariant)
            }
        }
    }
}
