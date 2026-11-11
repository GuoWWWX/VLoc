package app.locationlab.platform

import android.app.Notification
import android.content.Context
import app.locationlab.R

internal fun vlocNotificationBuilder(context: Context, channel: String) =
    Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_vloc_notification)
