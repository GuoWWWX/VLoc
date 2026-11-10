package app.locationlab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.locationlab.R
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton

@Composable
internal fun WelcomeScreen(authorized: Boolean, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val artSize = minOf(266.dp, (maxWidth - 60.dp).coerceAtLeast(120.dp))
        Column(Modifier.widthIn(max = 450.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp, vertical = 14.dp)) {
            LocationIconButton(Glyph.Close, stringResource(R.string.close), onClose,
                modifier = Modifier.align(Alignment.End), color = colors.onSurfaceVariant)
            Spacer(Modifier.height(19.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).size(artSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val unit = size.minDimension / 266f
                    drawCircle(colors.secondaryContainer.copy(alpha = .72f), 133 * unit)
                    drawCircle(colors.primary.copy(alpha = .05f), 92 * unit)
                    drawCircle(colors.primary, 92 * unit, style = Stroke(1.dp.toPx()))
                    drawCircle(colors.surface, 41 * unit)
                    drawCircle(colors.primary, 25 * unit)
                }
                LocationIcon(Glyph.Locate, size = 22.dp, color = colors.onPrimary)
            }
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.welcome_title), color = colors.onSurface,
                fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_description), color = colors.onSurfaceVariant,
                fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(24.dp))
            WelcomeStep(1, stringResource(R.string.welcome_step_one))
            Spacer(Modifier.height(10.dp))
            WelcomeStep(2, stringResource(R.string.welcome_step_two))
            Spacer(Modifier.height(24.dp))
            if (authorized) {
                LocationButton(stringResource(R.string.welcome_enter), onClose, Modifier.fillMaxWidth(), glyph = Glyph.Check)
            } else {
                LocationButton(stringResource(R.string.open_settings), onOpenSettings, Modifier.fillMaxWidth())
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.welcome_skip))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(number: Int, label: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 55.dp).clip(RoundedCornerShape(12.dp))
            .background(colors.background).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(25.dp).clip(CircleShape).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Text(number.toString(), color = colors.onSecondaryContainer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(11.dp))
        Text(label, color = colors.onSurface, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun MockPermissionDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 390.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(colors.surface).padding(24.dp),
            ) {
                Row(Modifier.heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFCF0E2)),
                        contentAlignment = Alignment.Center) {
                        LocationIcon(Glyph.Alert, size = 23.dp, color = Color(0xFFC47D39))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.mock_dialog_title), color = colors.onSurface,
                        fontSize = 20.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                LocationButton(stringResource(R.string.go_to_settings), onOpenSettings, Modifier.fillMaxWidth())
            }
        }
    }
}
