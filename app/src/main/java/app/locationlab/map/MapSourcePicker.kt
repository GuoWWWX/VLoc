package app.locationlab.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locationlab.R
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationSheet

@Composable
internal fun MapSourcePicker(
    source: MapSource,
    onSelect: (MapSource) -> Unit,
    trigger: @Composable (open: () -> Unit) -> Unit,
) {
    var showChoices by rememberSaveable { mutableStateOf(false) }
    trigger { showChoices = true }
    if (showChoices) {
        val colors = MaterialTheme.colorScheme
        LocationSheet(stringResource(R.string.map_source), onClose = { showChoices = false }) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                for (option in mapDisplayOrder) {
                    val selected = option == source
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 49.dp).clip(RoundedCornerShape(12.dp))
                            .then(if (selected) Modifier.background(colors.secondaryContainer) else Modifier)
                            .selectable(selected, role = Role.RadioButton) {
                                showChoices = false
                                onSelect(option)
                            }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LocationIcon(Glyph.Layers, color = colors.primary)
                        Text(stringResource(option.label), Modifier.weight(1f).padding(start = 12.dp),
                            color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (selected) LocationIcon(Glyph.Check, color = colors.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

private val mapDisplayOrder = listOf(
    MapSource.AMapPreview, MapSource.BaiduPreview, MapSource.GooglePreview,
    MapSource.OpenFreeMap, MapSource.OpenStreetMap, MapSource.VersaTiles,
)
