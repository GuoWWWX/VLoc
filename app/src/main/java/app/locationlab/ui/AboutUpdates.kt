package app.locationlab.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val projectUrl = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}"
    val colors = MaterialTheme.colorScheme
    fun openProject(path: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, (projectUrl + path).toUri()))
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, R.string.project_link_unavailable, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VLoc", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.about_text), color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { openProject("") }) { Text("GitHub") }
                    TextButton(onClick = { openProject("/releases") }) { Text(stringResource(R.string.about_releases)) }
                    TextButton(onClick = { openProject("/blob/main/LICENSE") }) { Text("MIT") }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.about_updates), fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface)
                        update?.let { Text(stringResource(R.string.update_available, it.version), color = colors.primary) }
                        status?.let { Text(stringResource(it), fontSize = 12.sp, lineHeight = 18.sp) }
                        if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (download != null || progress > 0f || status == R.string.update_paused) {
                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(if (apk != null) R.string.update_download_complete else
                                if (progress >= 1f && download != null) R.string.update_verifying else R.string.update_progress,
                                (progress.coerceIn(0f, 1f) * 100).toInt()), fontSize = 12.sp)
                        }
                        if (status == null && update == null && !checking) {
                            Text(stringResource(R.string.about_update_hint), fontSize = 12.sp, lineHeight = 18.sp,
                                color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !checking, onClick = {
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
                                progress = 1f
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
