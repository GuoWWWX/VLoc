package app.locationlab.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.locationlab.BuildConfig
import app.locationlab.R
import app.locationlab.update.AppUpdate
import app.locationlab.update.UpdateRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AboutUpdates(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { UpdateRepository(context) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf<Int?>(null) }
    var download by remember { mutableStateOf<Job?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val attribution = stringResource(R.string.osm_copyright)

    val action = when {
        checking -> R.string.update_checking
        download != null -> R.string.update_pause
        apk != null -> R.string.update_install
        update != null -> if (status == R.string.update_failed || status == R.string.update_paused) {
            R.string.update_resume
        } else R.string.update_download
        else -> R.string.update_check
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.about)) },
        text = {
            Column {
                Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME))
                update?.let { Text(stringResource(R.string.update_available, it.version)) }
                status?.let { Text(stringResource(it)) }
                if (download != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_progress, (progress * 100).toInt()))
                }
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, attribution.toUri()))
                    } catch (_: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(context, attribution, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.map_attribution)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !checking, onClick = {
                val ready = apk
                val release = update
                when {
                    download != null -> {
                        download?.cancel()
                        status = R.string.update_paused
                    }
                    ready != null -> {
                        if (!ready.isFile) {
                            apk = null
                            status = R.string.update_failed
                        } else {
                            try {
                                repository.install(ready)
                            } catch (_: android.content.ActivityNotFoundException) {
                                status = R.string.update_install_failed
                            } catch (_: SecurityException) {
                                status = R.string.update_install_failed
                            }
                        }
                    }
                    release != null -> {
                        status = null
                        progress = 0f
                        download = scope.launch {
                            try {
                                apk = repository.prepare(release) { progress = it }
                                status = R.string.update_ready
                            } catch (_: IOException) {
                                status = R.string.update_failed
                            } finally {
                                download = null
                            }
                        }
                    }
                    else -> {
                        status = null
                        checking = true
                        scope.launch {
                            try {
                                update = repository.check()
                                if (update == null) status = R.string.update_latest
                            } catch (_: IOException) {
                                status = R.string.update_check_failed
                            } finally {
                                checking = false
                            }
                        }
                    }
                }
            }) { Text(stringResource(action)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}
