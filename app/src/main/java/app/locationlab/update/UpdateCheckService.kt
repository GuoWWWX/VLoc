package app.locationlab.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.edit
import app.locationlab.MainActivity
import app.locationlab.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class UpdateCheckService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            try {
                UpdateRepository(applicationContext).check()?.let(::notifyIfNew)
                jobFinished(params, false)
            } catch (_: IOException) {
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyIfNew(update: AppUpdate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.update_channel),
            NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return
        val preferences = getSharedPreferences(CHANNEL, MODE_PRIVATE)
        if (preferences.getLong("notified_version", 0) >= update.code) return
        val action = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).setAction(OPEN_UPDATES)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        manager.notify(JOB_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_vloc_notification)
            .setLargeIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setContentTitle(getString(R.string.update_available, update.version))
            .setContentText(getString(R.string.update_notification_hint))
            .setContentIntent(action)
            .setAutoCancel(true)
            .build())
        preferences.edit { putLong("notified_version", update.code) }
    }

    companion object {
        internal const val OPEN_UPDATES = "app.locationlab.OPEN_UPDATES"
        private const val CHANNEL = "app_updates"
        private const val JOB_ID = 21

        internal fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .build())
        }
    }
}
