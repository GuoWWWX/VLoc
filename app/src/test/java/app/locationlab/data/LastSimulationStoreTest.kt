package app.locationlab.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.locationlab.simulation.Coordinate
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LastSimulationStoreTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun newSessionGetsTheLastDeliveredPointAfterStorageReopens() = runBlocking {
        val file = File(directory.root, "last-simulation.preferences_pb")
        val last = Coordinate(22.543204, 114.058001)

        withStore(file) { store ->
            assertNull(store.lastSimulation.first())
            assertNull(store.recordSimulation(last))
        }
        withStore(file) { store ->
            assertEquals(last, store.lastSimulation.first())
        }
    }

    @Test
    fun savingNewOutputReturnsThePreviouslySavedCoordinate() = runBlocking {
        val storage = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val first = Coordinate(22.543096, 114.057865)
        val next = Coordinate(31.2304, 121.4737)
        assertNull(LocationStore(storage).recordSimulation(first))
        assertEquals(first, LocationStore(storage).recordSimulation(next))
        assertEquals(next, LocationStore(storage).lastSimulation.first())
    }

    private suspend fun withStore(file: File, action: suspend (LocationStore) -> Unit) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            action(LocationStore(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            job.cancelAndJoin()
        }
    }
}
