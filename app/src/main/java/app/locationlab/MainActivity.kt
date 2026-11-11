package app.locationlab

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.locationlab.data.Appearance
import app.locationlab.map.MapSource
import app.locationlab.platform.SimulationService
import app.locationlab.platform.SimulationState
import app.locationlab.platform.canMockLocation
import app.locationlab.simulation.SimulationConfig
import app.locationlab.ui.AboutUpdates
import app.locationlab.ui.ClipboardLocation
import app.locationlab.ui.HistoryManagementScreen
import app.locationlab.ui.HomeScreen
import app.locationlab.ui.LocationEntryDialog
import app.locationlab.ui.LocationModel
import app.locationlab.ui.MockPermissionDialog
import app.locationlab.ui.PickerScreen
import app.locationlab.ui.SettingsDrawer
import app.locationlab.ui.UpdateReminderDialog
import app.locationlab.ui.WelcomeScreen
import app.locationlab.ui.readSharedClipboard
import app.locationlab.update.UpdateAvailability
import app.locationlab.update.UpdateCheckService
import app.locationlab.update.UpdateRepository
import java.io.IOException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var clipboardCheckPending = false
    private var clipboardLocation by mutableStateOf<ClipboardLocation?>(null)
    private var updateRequested by mutableStateOf(false)
    private var mockAllowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateRequested = intent.action == UpdateCheckService.OPEN_UPDATES
        mockAllowed = canMockLocation()
        UpdateCheckService.schedule(this)
        setContent { LocationApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UpdateCheckService.OPEN_UPDATES) updateRequested = true
    }

    override fun onResume() {
        super.onResume()
        mockAllowed = canMockLocation()
        clipboardCheckPending = true
        checkClipboard()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    private fun checkClipboard() {
        if (!clipboardCheckPending || !hasWindowFocus()) return
        clipboardCheckPending = false
        clipboardLocation = readSharedClipboard()
    }

    @Composable
    private fun LocationApp(model: LocationModel = viewModel()) {
        val data by model.data.collectAsStateWithLifecycle()
        val storageError by model.storageError.collectAsStateWithLifecycle()
        val readError by model.readError.collectAsStateWithLifecycle()
        val simulation by SimulationService.state.collectAsStateWithLifecycle()
        var picker by rememberSaveable { mutableStateOf<PickerMode?>(null) }
        var manageHistory by rememberSaveable { mutableStateOf(false) }
        var showGuide by rememberSaveable { mutableStateOf(false) }
        var requestedNotifications by rememberSaveable { mutableStateOf(false) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val updateRepository = remember { UpdateRepository(this@MainActivity) }
        var updateAvailability by remember {
            mutableStateOf(UpdateAvailability(cachedUpdateAvailable = updateRepository.hasKnownUpdate()))
        }
        var handledUpdateCode by rememberSaveable { mutableLongStateOf(0) }
        var showAbout by rememberSaveable { mutableStateOf(false) }
        val openAbout = {
            updateAvailability.update?.let { handledUpdateCode = it.code }
            showAbout = true
        }
        LaunchedEffect(updateRequested) {
            if (updateRequested) {
                openAbout()
                updateRequested = false
            }
        }
        LifecycleStartEffect(updateRepository) {
            if (!showAbout) {
                updateAvailability = UpdateAvailability(cachedUpdateAvailable = updateRepository.hasKnownUpdate())
            }
            val check = scope.launch {
                try {
                    val update = updateRepository.check()
                    updateAvailability = UpdateAvailability(update = update)
                    if ((showAbout || updateRequested) && update != null) handledUpdateCode = update.code
                } catch (_: IOException) {
                    // The last known availability stays visible while offline.
                }
            }
            onStopOrDispose { check.cancel() }
        }
        val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            data?.let { current -> current.config?.let { SimulationService.start(this, it, current.placeName) } }
        }

        val busyStatus = when (simulation) {
            SimulationState.Starting -> R.string.simulation_starting
            SimulationState.Stopping -> R.string.simulation_stopping
            else -> null
        }
        val active = simulation as? SimulationState.Running
        val canChangeSelection = active == null && busyStatus == null
        val dark = when (data?.appearance) {
            Appearance.Light -> false
            Appearance.Dark -> true
            else -> isSystemInDarkTheme()
        }
        val theme = if (dark) {
            darkColorScheme(
                primary = Color(0xFF7DDBCF),
                onPrimary = Color(0xFF103735),
                secondaryContainer = Color(0xFF174743),
                onSecondaryContainer = Color(0xFFA3F0E5),
                background = Color(0xFF101C1C),
                surface = Color(0xFF172626),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF087D76),
                onPrimary = Color.White,
                secondaryContainer = Color(0xFFD6F1E8),
                onSecondaryContainer = Color(0xFF0D6965),
                background = Color(0xFFF4F8F6),
                surface = Color.White,
                onSurface = Color(0xFF122D31),
                onSurfaceVariant = Color(0xFF637D7B),
                outlineVariant = Color(0xFFD8E4E0),
            )
        }

        MaterialTheme(colorScheme = theme) {
            Surface(Modifier.fillMaxSize(), color = theme.background) {
                val loaded = data
                if (loaded == null || readError) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (readError) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.storage_read_error))
                                TextButton(onClick = model::retryLoading) { Text(stringResource(R.string.retry_storage)) }
                                if (active != null) {
                                    TextButton(onClick = { SimulationService.stop(this@MainActivity) }) {
                                        Text(stringResource(R.string.simulation_stop))
                                    }
                                }
                            }
                        } else CircularProgressIndicator()
                    }
                } else {
                    val selectMapSource: (MapSource) -> Unit = { source ->
                        model.edit { it.copy(mapSource = source) }
                    }
                    val welcome = !loaded.setupGuideSeen
                    val finishWelcome = { model.edit { it.copy(onboardingSeen = true, setupGuideSeen = true) } }
                    BackHandler(drawer.isOpen || picker != null || manageHistory) {
                        when {
                            drawer.isOpen -> scope.launch { drawer.close() }
                            picker != null -> picker = null
                            else -> manageHistory = false
                        }
                    }
                    ModalNavigationDrawer(
                        drawerState = drawer,
                        gesturesEnabled = picker == null && !welcome && !manageHistory,
                        scrimColor = theme.onSurface.copy(alpha = 0.38f),
                        drawerContent = {
                            SettingsDrawer(
                                appearance = loaded.appearance,
                                mapSource = loaded.mapSource,
                                onMapSource = selectMapSource,
                                onClose = { scope.launch { drawer.close() } },
                                onAppearance = { value -> model.edit { it.copy(appearance = value) } },
                                onMockSettings = { showGuide = true },
                                onNotifications = { openSystem(Settings.ACTION_APP_NOTIFICATION_SETTINGS) },
                                onHistory = { scope.launch { drawer.close(); manageHistory = true } },
                                showUpdateBadge = updateAvailability.showBadge,
                                onAbout = openAbout,
                            )
                        },
                    ) {
                        when {
                            welcome -> WelcomeScreen(
                                authorized = mockAllowed,
                                onClose = finishWelcome,
                            )
                            picker != null -> PickerScreen(
                                initial = if (picker == PickerMode.Add) loaded.config?.let { SimulationConfig(it.center) }
                                    else loaded.config,
                                initialName = loaded.placeName.takeIf { picker == PickerMode.Edit },
                                adding = picker == PickerMode.Add,
                                history = loaded.history,
                                source = loaded.mapSource,
                                onMapSource = selectMapSource,
                                onCancel = { picker = null },
                                onConfirm = { config, name, onFailure ->
                                    if (picker == PickerMode.Add) {
                                        model.add(config, name, onFailure, makeCurrent = canChangeSelection) { picker = null }
                                    }
                                    else model.edit({ picker = null }, onFailure) {
                                        it.saveCurrent(loaded.selectedHistoryKey, config, name)
                                    }
                                },
                            )
                            manageHistory -> HistoryManagementScreen(
                                entries = loaded.history,
                                canApply = canChangeSelection,
                                onBack = { manageHistory = false },
                                onApply = { entry ->
                                    val now = System.currentTimeMillis()
                                    model.edit({ manageHistory = false }) { it.use(entry.key, now) }
                                },
                                onPin = { key -> model.edit { it.update(key) { row -> row.copy(pinned = !row.pinned) } } },
                                onEdit = { entry, point, name, onSaved, onFailure ->
                                    model.edit(onSaved, onFailure) { it.editHistory(entry.key, point, name) }
                                },
                                onDelete = { keys -> model.edit { it.removeHistory(keys) } },
                                onFavorite = { keys -> model.edit { it.favoriteHistory(keys) } },
                            )
                            else -> HomeScreen(
                                data = loaded,
                                active = active,
                                busyStatus = busyStatus,
                                drawerOpen = drawer.isOpen,
                                onSettings = { scope.launch { drawer.open() } },
                                onPick = { picker = PickerMode.Edit },
                                onAdd = { picker = PickerMode.Add },
                                onStart = {
                                    loaded.config?.let { config ->
                                        if (!canMockLocation()) {
                                            showGuide = true
                                        } else if (Build.VERSION.SDK_INT >= 33 &&
                                            !requestedNotifications &&
                                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            requestedNotifications = true
                                            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            SimulationService.start(this, config, loaded.placeName)
                                        }
                                    }
                                },
                                onSelect = { entry ->
                                    val now = System.currentTimeMillis()
                                    model.edit { it.use(entry.key, now) }
                                },
                                onEdit = { entry, point, name, onSaved, onFailure ->
                                    model.edit(onSaved, onFailure) { it.editHistory(entry.key, point, name) }
                                },
                                onPin = { key -> model.edit { data -> data.update(key) { it.copy(pinned = !it.pinned) } } },
                                onFavorite = { key -> model.edit { data -> data.update(key) { it.copy(favorite = !it.favorite) } } },
                                onDelete = { key -> model.edit { it.removeHistory(key) } },
                                onStop = { SimulationService.stop(this) },
                                onRefresh = { SimulationService.refresh(this) },
                                onToggleDrift = { model.edit { it.toggleDrift() } },
                            )
                        }
                    }
                    val pendingShare = clipboardLocation?.takeUnless { it.fingerprint == loaded.lastHandledShare }
                    pendingShare?.let { shared ->
                        key(shared.fingerprint) {
                            LocationEntryDialog(shared.location.coordinate, shared.location.name, importing = true,
                                onClose = {
                                    clipboardLocation = null
                                    model.edit { it.copy(lastHandledShare = shared.fingerprint) }
                                },
                                onSave = { point, name, onFailure ->
                                    model.add(SimulationConfig(point), name, onFailure,
                                        makeCurrent = canChangeSelection, shareFingerprint = shared.fingerprint) {
                                        clipboardLocation = null
                                    }
                                },
                            )
                        }
                    }
                    if (showAbout) {
                        val update = updateAvailability.update
                        key(update) {
                            AboutUpdates(
                                update = update,
                                onUpdateChecked = { checked ->
                                    updateAvailability = UpdateAvailability(update = checked)
                                    checked?.let { handledUpdateCode = it.code }
                                },
                                onClose = { showAbout = false },
                            )
                        }
                    } else if (!updateRequested && !welcome && !showGuide && drawer.isClosed &&
                        picker == null && !manageHistory && pendingShare == null &&
                        !storageError && simulation !is SimulationState.Error
                    ) {
                        updateAvailability.reminder(handledUpdateCode)?.let { update ->
                            UpdateReminderDialog(
                                version = update.version,
                                onLater = { handledUpdateCode = update.code },
                                onUpdate = openAbout,
                            )
                        }
                    }
                }
                if (showGuide) {
                    MockPermissionDialog(
                        authorized = mockAllowed,
                        onDismiss = { showGuide = false },
                    )
                }
                val error = simulation as? SimulationState.Error
                if (error != null || (storageError && loaded != null)) {
                    val dismiss = {
                        model.storageError.value = false
                        SimulationService.dismissError()
                    }
                    AlertDialog(
                        onDismissRequest = dismiss,
                        text = { Text(stringResource(error?.message ?: R.string.storage_error)) },
                        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } },
                    )
                }
            }
        }
    }

    private fun openSystem(action: String) {
        val fallback = if (action == Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) {
            R.string.mock_guide
        } else {
            R.string.settings_unavailable
        }
        try {
            startActivity(Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, fallback, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, fallback, Toast.LENGTH_LONG).show()
        }
    }
}

private enum class PickerMode { Add, Edit }
