package app.locationlab.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import app.locationlab.R

internal fun Context.openSetupSettings(step: Int, developerEnabled: Boolean) {
    val actions = when (step) {
        0 -> listOf(if (developerEnabled) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS)
        1 -> listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_DEVICE_INFO_SETTINGS)
        else -> buildList {
            // AOSP app-specific battery page; OEMs may omit this entry point.
            add("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL")
            if (Build.MANUFACTURER.equals("vivo", true) || Build.BRAND.equals("iqoo", true)) {
                add(Intent.ACTION_POWER_USAGE_SUMMARY)
            }
            add(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            add(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }
    for (action in actions + Settings.ACTION_SETTINGS) {
        val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS ||
            action == "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL") {
            intent.data = "package:$packageName".toUri()
        }
        val preference = when {
            step == 1 && action == Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS -> "mock_location_app"
            action == Settings.ACTION_DEVICE_INFO_SETTINGS -> "build_number"
            else -> null
        }
        if (preference != null) {
            // Settings uses this search-result argument to scroll to and highlight a preference.
            // It is optional: OEM settings that do not implement it still open the requested page.
            intent.putExtra(":settings:fragment_args_key", preference)
            intent.putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", preference)
            })
        }
        try {
            startActivity(intent)
            if (action == Settings.ACTION_SETTINGS) Toast.makeText(this, R.string.setup_fallback, Toast.LENGTH_LONG).show()
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next supported system entry point.
        } catch (_: SecurityException) {
            // Some manufacturers restrict their private settings activities.
        }
    }
    Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
}
