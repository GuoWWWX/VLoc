package app.locationlab.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import app.locationlab.data.LocationStore
import app.locationlab.MainActivity
import app.locationlab.R
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DEFAULT_DRIFT_RADIUS
import app.locationlab.simulation.DriftDeadline
import app.locationlab.simulation.DriftEngine
import app.locationlab.simulation.DriftTimer
import app.locationlab.simulation.DriftTiming
import app.locationlab.simulation.SimulationConfig
import app.locationlab.simulation.decodeDriftTiming
import app.locationlab.simulation.encode
import java.time.ZonedDateTime
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SimulationState {
    data object Idle : SimulationState
    data object Starting : SimulationState
    data object Stopping : SimulationState
    data class Running(
        val config: SimulationConfig,
        val point: Coordinate,
        val previousPoint: Coordinate? = null,
        val name: String? = null,
        val refreshing: Boolean = false,
        val nextDrift: DriftDeadline? = null,
    ) : SimulationState
    data class Error(@StringRes val message: Int) : SimulationState
}

class SimulationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mock: MockLocation
    private lateinit var store: LocationStore
    private var updates: Job? = null
    private var finishing = false
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        mock = MockLocation(this)
        store = LocationStore(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            val running = mutableState.value as? SimulationState.Running
            if (running != null && running.config.drift && !running.refreshing) {
                mutableState.value = running.copy(refreshing = true)
                if (refreshRequests.trySend(Unit).isFailure) mutableState.value = running
            }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            finish()
            return START_NOT_STICKY
        }
        if (updates != null || finishing) return START_NOT_STICKY

        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            val config = SimulationConfig(
                Coordinate(intent.getDoubleExtra("lat", Double.NaN), intent.getDoubleExtra("lon", Double.NaN)),
                intent.getBooleanExtra("drift", false),
                intent.getIntExtra("radius", DEFAULT_DRIFT_RADIUS),
                decodeDriftTiming(intent.getStringExtra("timing"), intent.getIntExtra("interval", 0)),
            )
            val name = intent.getStringExtra("name")
            updates = scope.launch { simulate(config, name) }
        } catch (error: RuntimeException) {
            finish(error.messageResource())
        }
        return START_NOT_STICKY
    }

    private suspend fun simulate(config: SimulationConfig, name: String?) {
        try {
            val engine = DriftEngine(config)
            var point = engine.initialPoint()
            mock.open()
            var nextLocationRefresh = SystemClock.elapsedRealtime() + LOCATION_REFRESH_MS
            val previous = deliver(point)
            val timer = DriftTimer(if (config.drift) config.timing else DriftTiming.OnStart,
                SystemClock.elapsedRealtime(), ZonedDateTime.now())
            mutableState.value = SimulationState.Running(config, point, previousPoint = previous,
                name = name, nextDrift = timer.nextDeadline)
            while (currentCoroutineContext().isActive) {
                val now = SystemClock.elapsedRealtime()
                val wait = minOf(timer.millisecondsUntil(now, ZonedDateTime.now()), nextLocationRefresh - now)
                val scheduled = mutableState.value as? SimulationState.Running
                if (scheduled != null && scheduled.nextDrift != timer.nextDeadline) {
                    mutableState.value = scheduled.copy(nextDrift = timer.nextDeadline)
                }
                val refresh = withTimeoutOrNull(wait.coerceAtLeast(1)) {
                    refreshRequests.receive()
                } != null
                val refreshStarted = SystemClock.elapsedRealtime()
                nextLocationRefresh = refreshStarted + LOCATION_REFRESH_MS
                val due = timer.advanceIfDue(SystemClock.elapsedRealtime(), ZonedDateTime.now())
                val next = when {
                    refresh -> engine.refresh()
                    due -> engine.next()
                    else -> point
                }
                if (point == next) {
                    mock.update(point)
                } else {
                    val last = deliver(next)
                    point = next
                    val running = mutableState.value as? SimulationState.Running
                    if (running != null) mutableState.value = running.copy(point = point,
                        previousPoint = last, nextDrift = timer.nextDeadline)
                }
                if (refresh) {
                    delay(REFRESH_FEEDBACK_MS - (SystemClock.elapsedRealtime() - refreshStarted))
                    val running = mutableState.value as? SimulationState.Running
                    if (running != null) mutableState.value = running.copy(refreshing = false)
                }
            }
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            finish(R.string.storage_error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            finish(error.messageResource())
        }
    }

    private suspend fun deliver(point: Coordinate): Coordinate? {
        mock.update(point)
        // Once delivered to Android, finish recording the point even if Stop is pressed.
        val previous = withContext(NonCancellable) { store.recordSimulation(point) }
        currentCoroutineContext().ensureActive()
        return previous
    }

    private fun finish(@StringRes error: Int? = null) {
        if (finishing) return
        finishing = true
        updates?.cancel()
        updates = null
        val cleanup = mock.close()
        fun complete(cleaned: Boolean) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            mutableState.value = when {
                !cleaned -> SimulationState.Error(R.string.simulation_cleanup_error)
                error != null -> SimulationState.Error(error)
                else -> SimulationState.Idle
            }
            finishing = false
            stopSelf()
        }
        if (cleanup.isComplete) {
            complete(cleanup.isSuccessful && cleanup.result == true)
            return
        }
        mutableState.value = SimulationState.Stopping
        scope.launch {
            val cleaned = withTimeoutOrNull(CLEANUP_WAIT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    cleanup.addOnCompleteListener {
                        continuation.resume(it.isSuccessful && it.result == true)
                    }
                }
            } == true
            complete(cleaned)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        val cleanup = mock.close()
        val cleaned = cleanup.isSuccessful && cleanup.result == true
        if (mutableState.value !is SimulationState.Error) {
            mutableState.value = if (cleaned) SimulationState.Idle
            else SimulationState.Error(R.string.simulation_cleanup_error)
        }
        super.onDestroy()
    }

    private fun RuntimeException.messageResource(): Int =
        if (this is SecurityException && !canMockLocation()) R.string.simulation_permission_error
        else R.string.simulation_error

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.simulation_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val content = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, SimulationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return vlocNotificationBuilder(this, CHANNEL)
            .setContentTitle(getString(R.string.simulation_running))
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.simulation_stop), stop).build())
            .build()
    }

    companion object {
        private const val ACTION_START = "app.locationlab.START"
        private const val ACTION_STOP = "app.locationlab.STOP"
        private const val ACTION_REFRESH = "app.locationlab.REFRESH"
        private const val CHANNEL = "simulation"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_REFRESH_MS = 125L
        private const val REFRESH_FEEDBACK_MS = 400L
        private const val CLEANUP_WAIT_MS = 3_000L
        private val mutableState = MutableStateFlow<SimulationState>(SimulationState.Idle)
        val state = mutableState.asStateFlow()

        fun start(context: Context, config: SimulationConfig, name: String? = null) {
            if (state.value is SimulationState.Running || state.value == SimulationState.Starting ||
                state.value == SimulationState.Stopping
            ) return
            if (!context.canMockLocation()) {
                mutableState.value = SimulationState.Error(R.string.simulation_permission_error)
                return
            }
            mutableState.value = SimulationState.Starting
            try {
                context.startForegroundService(Intent(context, SimulationService::class.java).apply {
                    action = ACTION_START
                    putExtra("lat", config.center.latitude)
                    putExtra("lon", config.center.longitude)
                    putExtra("drift", config.drift)
                    putExtra("radius", config.radiusMeters)
                    putExtra("timing", config.timing.encode())
                    putExtra("name", name)
                })
            } catch (_: RuntimeException) {
                mutableState.value = SimulationState.Error(R.string.simulation_start_error)
            }
        }

        fun stop(context: Context) {
            val running = state.value as? SimulationState.Running ?: return
            mutableState.value = SimulationState.Stopping
            try {
                context.startService(Intent(context, SimulationService::class.java).setAction(ACTION_STOP))
            } catch (_: RuntimeException) {
                mutableState.value = running
            }
        }

        fun refresh(context: Context) {
            val running = state.value as? SimulationState.Running ?: return
            if (running.config.drift && !running.refreshing) {
                try {
                    context.startService(Intent(context, SimulationService::class.java).setAction(ACTION_REFRESH))
                } catch (_: RuntimeException) {
                    Toast.makeText(context, R.string.refresh_location_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun dismissError() {
            if (state.value is SimulationState.Error) mutableState.value = SimulationState.Idle
        }
    }
}
