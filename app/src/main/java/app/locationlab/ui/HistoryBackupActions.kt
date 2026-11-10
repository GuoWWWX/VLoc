package app.locationlab.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.locationlab.R
import app.locationlab.data.HistoryEntry
import app.locationlab.ui.components.Glyph
import app.locationlab.ui.components.LocationIconButton
import java.time.LocalDate

@Composable
internal fun HistoryBackupActions(entries: List<HistoryEntry>, selected: Set<String>?) {
    val model: HistoryBackupModel = viewModel()
    val busy by model.busy.collectAsStateWithLifecycle()
    val pending by model.pendingImport.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), model::export)
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), model::read)
    fun exportEntries(rows: List<HistoryEntry>) {
        menu = false
        model.prepareExport(rows)
        try {
            export.launch("VLoc-positions-${LocalDate.now()}.json")
        } catch (_: ActivityNotFoundException) {
            model.export(null)
            model.notice.value = R.string.backup_picker_unavailable
        }
    }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else LocationIconButton(Glyph.More, stringResource(R.string.backup_actions), { menu = true }, iconSize = 19.dp)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_all)) },
                enabled = entries.isNotEmpty(), onClick = { exportEntries(entries) })
            if (selected != null) DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_selected, selected.size)) },
                enabled = selected.isNotEmpty(), onClick = { exportEntries(entries.filter { it.key in selected }) })
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_restore)) }, onClick = {
                menu = false
                try {
                    restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                } catch (_: ActivityNotFoundException) {
                    model.notice.value = R.string.backup_picker_unavailable
                }
            })
        }
    }
    pending?.let { imported ->
        val existing = remember(entries) { entries.mapTo(mutableSetOf()) { it.key } }
        val updates = imported.count { it.key in existing }
        AlertDialog(onDismissRequest = { if (!busy) model.pendingImport.value = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_message, imported.size - updates, updates)) },
            dismissButton = { TextButton(enabled = !busy, onClick = { model.pendingImport.value = null }) {
                Text(stringResource(R.string.cancel))
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = model::restore) {
                Text(stringResource(if (busy) R.string.backup_restoring else R.string.backup_confirm))
            } })
    }
    notice?.let { message ->
        AlertDialog(onDismissRequest = { model.notice.value = null },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = { model.notice.value = null }) {
                Text(stringResource(R.string.confirm))
            } })
    }
}
