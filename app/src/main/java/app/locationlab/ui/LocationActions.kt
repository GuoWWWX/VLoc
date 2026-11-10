package app.locationlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.locationlab.R
import app.locationlab.data.HistoryEntry
import app.locationlab.data.normalizedName
import app.locationlab.platform.decimalCoordinate
import app.locationlab.simulation.Coordinate
import app.locationlab.ui.components.ButtonStyle
import app.locationlab.ui.components.DestructiveRed
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationSheet

@Composable
internal fun HistoryActionSheet(
    entry: HistoryEntry,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    LocationSheet(entry.placeName ?: stringResource(R.string.coordinates), onClose) {
        Spacer(Modifier.height(4.dp))
        CopyableCoordinate(entry.coordinate)
        HorizontalDivider(Modifier.padding(top = 15.dp, bottom = 5.dp))
        LocationActionRow(if (entry.pinned) R.string.unpin else R.string.pin,
            if (entry.pinned) Glyph.Unpin else Glyph.Pin, onPin)
        LocationActionRow(R.string.edit_location, Glyph.Edit, onEdit)
        LocationActionRow(R.string.share_location, Glyph.Share, onShare)
        LocationActionRow(R.string.delete_location, Glyph.Trash, onDelete, destructive = true)
    }
}

@Composable
internal fun ShareLocationSheet(entry: HistoryEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    LocationSheet(stringResource(R.string.share_location), onClose) {
        Spacer(Modifier.height(14.dp))
        LocationInfo(entry.coordinate, entry.placeName)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LocationButton(stringResource(R.string.copy_location), {
                context.copyLocationText(entry.shareText(), R.string.share_location)
                onClose()
            }, Modifier.weight(1f), style = ButtonStyle.Secondary, minimumHeight = 42.dp, glyph = Glyph.Copy)
            LocationButton(stringResource(R.string.system_share), {
                onClose()
                context.shareLocation(entry)
            }, Modifier.weight(1f), minimumHeight = 42.dp, glyph = Glyph.Share)
        }
    }
}

@Composable
internal fun LocationEntryDialog(
    coordinate: Coordinate,
    name: String?,
    importing: Boolean,
    onClose: () -> Unit,
    onSave: (Coordinate, String?, onFailure: () -> Unit) -> Unit,
) {
    val initialLatitude = coordinate.display().substringBefore(',')
    val initialLongitude = coordinate.display().substringAfter(", ")
    var title by rememberSaveable { mutableStateOf(name.orEmpty()) }
    var latitude by rememberSaveable { mutableStateOf(initialLatitude) }
    var longitude by rememberSaveable { mutableStateOf(initialLongitude) }
    var saving by remember { mutableStateOf(false) }
    val lat = if (latitude == initialLatitude) coordinate.latitude else
        latitude.decimalCoordinate()?.takeIf { it in -90.0..90.0 }
    val lon = if (longitude == initialLongitude) coordinate.longitude else
        longitude.decimalCoordinate()?.takeIf { it in -180.0..180.0 }
    val point = if (lat != null && lon != null) Coordinate(lat, lon) else null
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 390.dp).fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp), color = colors.surface,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (importing) R.string.save_shared_location else R.string.edit_location),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (importing) Text(stringResource(R.string.location_from_clipboard),
                    color = colors.onSurfaceVariant, fontSize = 12.sp)
                LocationEntryField(title, { title = it.take(80) }, R.string.location_name, Modifier.fillMaxWidth())
                if (importing) {
                    LocationInfo(coordinate)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LocationEntryField(latitude, { latitude = it }, R.string.latitude,
                            Modifier.weight(1f), error = lat == null)
                        LocationEntryField(longitude, { longitude = it }, R.string.longitude,
                            Modifier.weight(1f), error = lon == null)
                    }
                    Text(stringResource(if (point == null) R.string.coordinate_error else R.string.decimal_coordinates),
                        color = if (point == null) colors.error else colors.onSurfaceVariant, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LocationButton(stringResource(R.string.cancel), onClose, Modifier.weight(1f),
                        style = ButtonStyle.Secondary, minimumHeight = 40.dp)
                    LocationButton(
                        stringResource(if (importing) R.string.save_location else R.string.save_edit),
                        {
                            if (point != null && !saving) {
                                saving = true
                                onSave(point, title.normalizedName()) { saving = false }
                            }
                        },
                        Modifier.weight(1f), enabled = point != null && !saving, minimumHeight = 40.dp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CopyableCoordinate(
    point: Coordinate,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val value = point.display()
    val copyLabel = stringResource(R.string.copy_coordinates)
    Text(value, modifier.combinedClickable(
        interactionSource = null, indication = null,
        onClick = { onClick?.invoke() },
        onLongClickLabel = copyLabel,
        onLongClick = { context.copyLocationText(value, R.string.copy_coordinates) },
    ), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = fontSize,
        lineHeight = fontSize * 1.42f, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun LocationEntryField(value: String, change: (String) -> Unit, label: Int, modifier: Modifier, error: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fieldLabel = stringResource(label)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(fieldLabel, color = if (error) colors.error else colors.onSurfaceVariant, fontSize = 12.sp)
        BasicTextField(value, change, modifier = Modifier.fillMaxWidth().semantics { contentDescription = fieldLabel },
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp, lineHeight = 20.sp),
            decorationBox = { input ->
                Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape).background(colors.background)
                    .border(1.dp, if (error) colors.error else colors.outlineVariant, shape).padding(horizontal = 13.dp),
                    contentAlignment = Alignment.CenterStart) { input() }
            },
        )
    }
}

@Composable
private fun LocationInfo(point: Coordinate, name: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(15.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LocationIcon(Glyph.Position, color = colors.primary, size = 21.dp)
        Column {
            Text(name ?: stringResource(R.string.coordinates), color = colors.onSurface,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            CopyableCoordinate(point, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LocationActionRow(
    label: Int,
    glyph: Glyph,
    onClick: () -> Unit,
    description: Int? = null,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val ink = if (destructive) DestructiveRed else colors.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = if (description == null) 48.dp else 64.dp)
        .clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LocationIcon(glyph, color = if (destructive) ink else colors.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (description != null) Text(stringResource(description), color = colors.onSurfaceVariant, fontSize = 11.sp)
        }
        if (description != null) LocationIcon(Glyph.Next, color = colors.onSurfaceVariant, size = 16.dp)
    }
}
