package app.locationlab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.locationlab.data.AppData
import app.locationlab.data.LocationStore
import app.locationlab.simulation.SimulationConfig
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONException

class LocationModel(application: Application) : AndroidViewModel(application) {
    private val store = LocationStore(application)
    val storageError = MutableStateFlow(false)
    val data = combine(store.data, store.lastSimulation) { data, point -> data.copy(lastSimulation = point) }.catch { error ->
        if (error is IOException || error is JSONException || error is IllegalArgumentException) {
            storageError.value = true
        } else {
            throw error
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun add(
        config: SimulationConfig,
        name: String?,
        onFailure: () -> Unit,
        makeCurrent: Boolean = true,
        shareFingerprint: String? = null,
        onSaved: () -> Unit,
    ) {
        val key = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        edit(onSaved, onFailure) {
            val added = it.add(config, now, name, key, makeCurrent)
            if (shareFingerprint == null) added else added.copy(lastHandledShare = shareFingerprint)
        }
    }

    fun edit(onSaved: () -> Unit = {}, onFailure: () -> Unit = {}, transform: (AppData) -> AppData) {
        viewModelScope.launch {
            try {
                store.edit(transform)
                onSaved()
            } catch (_: IOException) {
                storageError.value = true
                onFailure()
            } catch (_: JSONException) {
                storageError.value = true
                onFailure()
            } catch (_: IllegalArgumentException) {
                storageError.value = true
                onFailure()
            }
        }
    }
}
