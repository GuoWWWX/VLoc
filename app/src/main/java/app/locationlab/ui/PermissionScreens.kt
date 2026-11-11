package app.locationlab.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.locationlab.R
import app.locationlab.platform.openSetupSettings
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationButton
import app.locationlab.ui.components.LocationIcon
import app.locationlab.ui.components.LocationIconButton
import kotlinx.coroutines.launch

@Composable
internal fun WelcomeScreen(authorized: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun developerEnabled() = Settings.Global.getInt(context.contentResolver,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    var developer by remember { mutableStateOf(developerEnabled()) }
    var backgroundConfirmed by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) developer = developerEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val titles = listOf(R.string.setup_developer, R.string.setup_mock, R.string.setup_background)
    val vivo = Build.MANUFACTURER.equals("vivo", true) || Build.BRAND.equals("iqoo", true)
    Box(Modifier.fillMaxSize().background(colors.surface).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 540.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_title), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                LocationIconButton(Glyph.Close, stringResource(R.string.close), onClose)
            }
            Text(stringResource(R.string.setup_intro), color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (step in 0..2) {
                    val selected = pager.currentPage == step
                    val fill by animateColorAsState(if (selected) colors.secondaryContainer else colors.background,
                        label = "Setup step")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(fill)
                        .selectable(selected, role = Role.Tab, onClick = { scope.launch { pager.animateScrollToPage(step) } })
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0${step + 1}", color = colors.primary, fontWeight = FontWeight.Bold)
                        Text(stringResource(titles[step]), fontSize = 12.sp, color = colors.onSurface)
                    }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top, pageSpacing = 16.dp) { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.secondaryContainer),
                        contentAlignment = Alignment.Center) {
                        LocationIcon(when (step) { 0 -> Glyph.Info; 1 -> Glyph.Locate; else -> Glyph.History },
                            size = 30.dp, color = colors.primary)
                    }
                    val done = when (step) { 0 -> developer; 1 -> authorized; else -> backgroundConfirmed }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(titles[step]), Modifier.weight(1f), fontSize = 24.sp,
                            lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(if (step == 2) {
                            if (done) R.string.setup_manual_confirmed else R.string.setup_manual_required
                        } else if (done) R.string.setup_complete else R.string.setup_pending),
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (done) colors.secondaryContainer else colors.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (done) colors.onSecondaryContainer else colors.onSurfaceVariant,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(stringResource(when (step) {
                        0 -> R.string.setup_developer_reason
                        1 -> R.string.setup_mock_reason
                        else -> R.string.setup_background_reason
                    }), fontSize = 15.sp, lineHeight = 24.sp)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.background)
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.setup_how), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(when (step) {
                            0 -> R.string.setup_developer_path
                            1 -> R.string.setup_mock_path
                            else -> if (vivo) R.string.setup_background_vivo else R.string.setup_background_path
                        }), fontSize = 13.sp, lineHeight = 22.sp, color = colors.onSurfaceVariant)
                    }
                    if (step == 2) {
                        Text(stringResource(R.string.setup_background_limits), fontSize = 12.sp,
                            lineHeight = 19.sp, color = colors.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(backgroundConfirmed, { backgroundConfirmed = it })
                            Text(stringResource(R.string.setup_confirm_background), fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LocationButton(stringResource(when (pager.currentPage) {
                    0 -> if (developer) R.string.setup_open_developer else R.string.setup_open_device
                    1 -> R.string.setup_open_mock
                    else -> R.string.setup_open_background
                }), { context.openSetupSettings(pager.currentPage, developer) }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (pager.currentPage == 0) onClose()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text(stringResource(if (pager.currentPage == 0) R.string.welcome_skip else R.string.setup_previous))
                    }
                    TextButton(onClick = { if (pager.currentPage == 2) onClose()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(if (pager.currentPage == 2) R.string.welcome_enter else R.string.setup_next))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MockPermissionDialog(authorized: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WelcomeScreen(authorized, onDismiss)
    }
}
