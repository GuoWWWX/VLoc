package app.locationlab.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.locationlab.R
import app.locationlab.data.HistoryEntry
import app.locationlab.data.LocationBackup
import app.locationlab.data.LocationStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

internal class HistoryBackupModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val store = LocationStore(application)
    private var exporting: List<HistoryEntry>? = null
    val pendingImport = MutableStateFlow<List<HistoryEntry>?>(null)
    val busy = MutableStateFlow(false)
    val notice = MutableStateFlow<Int?>(null)

    fun prepareExport(entries: List<HistoryEntry>) {
        exporting = entries.toList()
    }

    fun export(uri: Uri?) {
        val entries = exporting
        exporting = null
        if (uri == null) return
        if (entries == null) {
            notice.value = R.string.backup_export_retry
            return
        }
        perform(R.string.backup_export_failed) {
            withContext(Dispatchers.IO) {
                val text = LocationBackup.encode(entries, System.currentTimeMillis())
                val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open backup")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
            notice.value = R.string.backup_exported
        }
    }

    fun read(uri: Uri?) {
        if (uri == null) return
        perform(R.string.backup_import_failed) {
            pendingImport.value = withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open backup")
                input.use(LocationBackup::read)
            }
        }
    }

    fun restore() {
        val entries = pendingImport.value ?: return
        perform(R.string.backup_restore_failed) {
            store.edit { it.importHistory(entries) }
            pendingImport.value = null
            notice.value = R.string.backup_restored
        }
    }

    private fun perform(@StringRes errorMessage: Int, operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is IOException || error is JSONException || error is IllegalArgumentException ||
                    error is SecurityException
                ) notice.value = errorMessage
                else throw error
            } finally {
                busy.value = false
            }
        }
    }
}
