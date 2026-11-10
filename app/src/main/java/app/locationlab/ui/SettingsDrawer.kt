package app.locationlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.data.Appearance
import app.locationlab.map.MapSource
import app.locationlab.map.MapSourcePicker
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton

private enum class SettingsDialog { Appearance, About }

@Composable
fun SettingsDrawer(
    appearance: Appearance,
    mapSource: MapSource,
    onMapSource: (MapSource) -> Unit,
    onClose: () -> Unit,
    onAppearance: (Appearance) -> Unit,
    onMockSettings: () -> Unit,
    onNotifications: () -> Unit,
    onHistory: () -> Unit,
    openAbout: Boolean,
    onAboutOpened: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    LaunchedEffect(openAbout) {
        if (openAbout) {
            dialog = SettingsDialog.About
            onAboutOpened()
        }
    }
    val colors = MaterialTheme.colorScheme
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 312.dp),
        drawerShape = RectangleShape,
        drawerContainerColor = colors.surface,
    ) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 18.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings), Modifier.weight(1f).padding(start = 6.dp),
                        color = colors.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    LocationIconButton(Glyph.Close, stringResource(R.string.close), onClose,
                        background = colors.background, visibleSize = 34.dp, iconSize = 21.dp)
                }
                Spacer(Modifier.height(22.dp))
                Section(R.string.settings_app)
                Setting(R.string.appearance, Glyph.Appearance) { dialog = SettingsDialog.Appearance }
                Setting(R.string.notification_settings, Glyph.Notification, onClick = onNotifications)
                DrawerDivider()
                Section(R.string.settings_data)
                Setting(R.string.mock_settings, Glyph.Position, onClick = onMockSettings)
                Setting(R.string.history, Glyph.History, onClick = onHistory)
                MapSourcePicker(mapSource, onMapSource) { open ->
                    Setting(R.string.map_source, Glyph.Layers, onClick = open)
                }
                DrawerDivider()
                Setting(R.string.about, Glyph.Info) { dialog = SettingsDialog.About }
            }
            Text(stringResource(R.string.drawer_footer), Modifier.padding(start = 6.dp, top = 12.dp, bottom = 24.dp),
                color = colors.onSurfaceVariant, fontSize = 10.sp)
        }
    }

    val close = { dialog = null }
    when (dialog) {
        SettingsDialog.Appearance -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.appearance)) },
            text = {
                Column {
                    for (value in Appearance.entries) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = appearance == value,
                                onClick = { onAppearance(value); close() },
                            )
                            TextButton(onClick = { onAppearance(value); close() }) {
                                Text(stringResource(value.label))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } },
        )
        SettingsDialog.About -> AboutUpdates(close)
        null -> Unit
    }
}

@Composable
private fun Section(label: Int) {
    Text(
        stringResource(label),
        Modifier.padding(start = 6.dp, bottom = 6.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Setting(title: Int, glyph: Glyph, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(start = 5.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                .background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            LocationIcon(glyph, size = 18.dp, color = colors.primary)
        }
        Spacer(Modifier.width(13.dp))
        Text(stringResource(title), Modifier.weight(1f).padding(end = 8.dp),
            color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        LocationIcon(Glyph.Next, size = 16.dp, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(Modifier.padding(start = 6.dp, end = 6.dp, top = 7.dp, bottom = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant)
}

private val Appearance.label: Int
    get() = when (this) {
        Appearance.System -> R.string.theme_system
        Appearance.Light -> R.string.theme_light
        Appearance.Dark -> R.string.theme_dark
    }

