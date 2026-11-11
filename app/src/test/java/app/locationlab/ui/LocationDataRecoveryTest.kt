package app.locationlab.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.locationlab.data.AppData
import app.locationlab.data.LocationStore
import app.locationlab.map.MapSource
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.SimulationConfig
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationDataRecoveryTest {
    @Test
    fun retryAfterReadFailureRestoresDataAndContinuesObservingEdits() = runBlocking {
        val storage = TestStore()
        val store = LocationStore(storage)
        val config = SimulationConfig(Coordinate(22.54, 114.05), drift = true)
        store.edit { it.add(config, 1, "公共样例", "sample") }
        storage.failReads = true
        val reloads = MutableStateFlow(0)
        val errors = Channel<Boolean>(Channel.UNLIMITED)
        val snapshots = Channel<AppData>(Channel.UNLIMITED)
        val observation = launch {
            observeLocationData(store, reloads) { errors.trySend(it) }.collect { snapshots.send(it) }
        }
        try {
            assertTrue(withTimeout(5_000) { errors.receive() })
            storage.failReads = false
            reloads.value++
            val recovered = withTimeout(5_000) { snapshots.receive() }
            assertEquals(config, recovered.config)
            assertEquals("sample", recovered.history.single().key)

            store.edit { it.update("sample") { entry -> entry.copy(favorite = true) } }
            assertTrue(withTimeout(5_000) { snapshots.receive() }.history.single().favorite)
        } finally {
            observation.cancel()
        }
    }

    @Test
    fun retryingMalformedDataNeverOverwritesItAndCanReadARepairedSnapshot() = runBlocking {
        val storage = TestStore()
        val key = stringPreferencesKey("app_data")
        storage.values.value = mutablePreferencesOf(key to "{broken")
        val reloads = MutableStateFlow(0)
        val errors = Channel<Boolean>(Channel.UNLIMITED)
        val snapshots = Channel<AppData>(Channel.UNLIMITED)
        val observation = launch {
            observeLocationData(LocationStore(storage), reloads) { errors.trySend(it) }
                .collect { snapshots.send(it) }
        }
        try {
            assertTrue(withTimeout(5_000) { errors.receive() })
            reloads.value++
            assertTrue(withTimeout(5_000) { errors.receive() })
            assertEquals("{broken", storage.values.value[key])

            storage.values.value = mutablePreferencesOf(key to "{}")
            reloads.value++
            assertEquals(AppData(mapSource = MapSource.OpenFreeMap), withTimeout(5_000) { snapshots.receive() })
        } finally {
            observation.cancel()
        }
    }

    private class TestStore : DataStore<Preferences> {
        val values = MutableStateFlow<Preferences>(mutablePreferencesOf())
        var failReads = false
        override val data = flow {
            if (failReads) throw IOException("Temporary read failure")
            emitAll(values)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(values.value).also { values.value = it }
    }
}
