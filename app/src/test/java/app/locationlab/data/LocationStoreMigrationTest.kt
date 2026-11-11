package app.locationlab.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.locationlab.map.MapSource
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DriftIntervalUnit
import app.locationlab.simulation.DriftTiming
import app.locationlab.simulation.SimulationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStoreMigrationTest {
    @Test
    fun freshInstallDefaultsToAmapAndKeepsTheUsersMapChoice() = runBlocking {
        val storage = TestStore()
        val store = LocationStore(storage)
        assertEquals(MapSource.AMapPreview, store.data.first().mapSource)

        store.edit { it.copy(mapSource = MapSource.TencentPreview) }

        assertEquals(MapSource.TencentPreview, LocationStore(storage).data.first().mapSource)
    }

    @Test
    fun legacySnapshotKeepsSelectedOptionsNamesAndFlagsWhenSavedAgain() = runBlocking {
        val storage = TestStore("""
            {"config":{"lat":22.54,"lon":114.05,"drift":true,"radius":240,"interval":30,"name":"公共样例"},
             "history":[{"lat":22.54,"lon":114.05,"at":123,"pin":true,"favorite":true,"name":"公共样例"},
                        {"lat":31.23,"lon":121.47,"at":100,"pin":false,"favorite":false}],
             "appearance":"Dark","mapSource":"OpenStreetMap"}
        """.trimIndent())
        val store = LocationStore(storage)
        val migrated = store.data.first()
        val selected = migrated.history.first()
        assertEquals(SimulationConfig(Coordinate(22.54, 114.05), true, 240,
            DriftTiming.Interval(30, DriftIntervalUnit.Seconds)), selected.configuration())
        assertTrue(selected.pinned && selected.favorite)
        assertEquals("公共样例", selected.placeName)
        assertEquals(selected.key, migrated.selectedHistoryKey)
        assertEquals(SimulationConfig(Coordinate(31.23, 121.47)), migrated.history.last().configuration())

        store.edit { it.copy(mapSource = MapSource.GooglePreview) }
        assertEquals(migrated.copy(mapSource = MapSource.GooglePreview), LocationStore(storage).data.first())
    }

    @Test
    fun currentSnapshotRoundTripsPerLocationOptionsAndLastSimulation() = runBlocking {
        val storage = TestStore("{}")
        val store = LocationStore(storage)
        val first = SimulationConfig(Coordinate(22.54, 114.05), true, 50, DriftTiming.Daily(8, 30))
        val second = SimulationConfig(Coordinate(31.23, 121.47), true, 500,
            DriftTiming.Interval(2, DriftIntervalUnit.Hours))
        val expected = AppData().add(first, 1, "样例一", "one").add(second, 2, "样例二", "two")
            .update("one") { it.copy(pinned = true, favorite = true) }
            .copy(appearance = Appearance.Dark, mapSource = MapSource.GooglePreview, lastHandledShare = "sample")
        store.edit { expected }
        val previous = Coordinate(22.5401, 114.0501)
        store.recordSimulation(previous)
        val reopened = LocationStore(storage)
        assertEquals(expected, reopened.data.first())
        assertEquals(previous, reopened.lastSimulation.first())
        assertEquals(first, reopened.data.first().use("one", 3).config)
    }

    @Test
    fun restoredBackupPersistsWithoutReplacingOtherHistoryOrSettings() = runBlocking {
        val storage = TestStore("{}")
        val store = LocationStore(storage)
        val config = SimulationConfig(Coordinate(22.54, 114.05))
        val original = AppData().add(config, 1, "公共样例", "selected")
            .add(config, 2, "保留样例", "keep", makeCurrent = false)
            .copy(appearance = Appearance.Dark, mapSource = MapSource.GooglePreview)
        store.edit { original }
        val lastSimulation = Coordinate(22.5401, 114.0501)
        store.recordSimulation(lastSimulation)
        val imported = listOf(original.history.first().copy(favorite = true, pinned = true,
            drift = true, radiusMeters = 800, timing = DriftTiming.Daily(9, 45)),
            original.history.first().copy(key = "new", placeName = "新增样例"))
        val backup = LocationBackup.encode(imported, 3)
        val restored = LocationBackup.read(backup.byteInputStream(Charsets.UTF_8))

        store.edit { it.importHistory(restored) }

        val reopened = LocationStore(storage)
        assertEquals(original.importHistory(imported), reopened.data.first())
        assertEquals(lastSimulation, reopened.lastSimulation.first())
        assertEquals(imported.first().configuration(), reopened.data.first().config)
    }

    @Test
    fun existingUsersSeeNewSetupGuideOnceWithoutLosingTheirConfiguration() = runBlocking {
        val storage = TestStore("""{"onboardingSeen":true,"config":{"lat":22.54,"lon":114.05,"drift":false,"radius":50}}""")
        val store = LocationStore(storage)
        val original = store.data.first()
        assertTrue(original.onboardingSeen)
        assertEquals(false, original.setupGuideSeen)
        store.edit { it.copy(setupGuideSeen = true) }
        val reopened = LocationStore(storage).data.first()
        assertTrue(reopened.setupGuideSeen)
        assertEquals(original.config, reopened.config)
        assertEquals(original.history, reopened.history)
    }

    private class TestStore(raw: String? = null) : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(mutablePreferencesOf().apply {
            if (raw != null) this[stringPreferencesKey("app_data")] = raw
        })
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
