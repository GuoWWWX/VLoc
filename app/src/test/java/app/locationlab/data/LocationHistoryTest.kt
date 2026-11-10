package app.locationlab.data

import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DriftTiming
import app.locationlab.simulation.SimulationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationHistoryTest {
    @Test
    fun eachSavedPlaceRestoresItsOwnDriftConfiguration() {
        val first = SimulationConfig(Coordinate(22.54, 114.05), true, 240, DriftTiming.Daily(8, 30))
        val second = SimulationConfig(Coordinate(31.23, 121.47))
        val active = AppData().add(first, 1, "福田", "a").add(second, 2, "上海", "b", makeCurrent = false)

        assertEquals(first, active.config)
        assertEquals(second, active.use("b", 3).config)
        val switched = active.use("b", 3).toggleDrift().use("a", 4)
        assertEquals(first, switched.config)
        assertEquals(second.copy(drift = true), switched.use("b", 5).config)
    }

    @Test
    fun reselectPreservesFlagsAndPinnedPointsSortFirst() {
        val a = SimulationConfig(Coordinate(22.54, 114.05))
        val b = SimulationConfig(Coordinate(22.55, 114.06))
        val data = AppData().add(a, 1, null, "a").add(b, 2, null, "b")
        val key = data.history.first().key
        val pinned = data.update(key) { it.copy(pinned = true, favorite = true) }
        assertEquals(listOf(a.center, b.center), pinned.visibleHistory(false).map { it.coordinate })
        val reselected = pinned.use("a", 3).use("b", 4)
        assertEquals(2, reselected.history.size)
        val favorite = reselected.visibleHistory(true).single()
        assertEquals(3L, favorite.selectedAt)
        assertTrue(favorite.pinned)
        assertEquals(b, reselected.clearOrdinaryHistory().config)
        assertEquals(listOf(favorite), reselected.clearOrdinaryHistory().history)
    }

    @Test
    fun deletingSelectedFavoriteRemovesOnlyTheHistoryEntry() {
        val other = SimulationConfig(Coordinate(22.55, 114.06))
        val selected = SimulationConfig(Coordinate(22.54, 114.05), drift = true)
        val data = AppData().add(other, 1, null, "other").add(selected, 2, null, "selected")
        val key = data.history.last().key
        val marked = data.update(key) { it.copy(pinned = true, favorite = true) }

        val removed = marked.removeHistory(key)

        assertEquals(selected, removed.config)
        assertEquals(listOf(other.center), removed.visibleHistory(false).map { it.coordinate })
        assertTrue(removed.visibleHistory(true).isEmpty())
    }

    @Test
    fun namedPlaceSurvivesReselectingAndRemovingItsHistoryEntry() {
        val config = SimulationConfig(Coordinate(22.54, 114.05))
        val named = AppData().add(config, 1, "福田中心", "a").use("a", 2)

        assertEquals("福田中心", named.history.single().placeName)
        assertEquals("福田中心", named.removeHistory(named.history.single().key).placeName)
        val other = SimulationConfig(Coordinate(31.23, 121.47))
        assertEquals(null, named.add(other, 3, null, "b").placeName)
    }

    @Test
    fun movingTheCurrentPinKeepsTheLegacyKeyAndFlags() {
        val center = Coordinate(22.54, 114.05)
        val other = Coordinate(31.23, 121.47)
        val original = HistoryEntry(center, 1, pinned = true, favorite = true, placeName = "福田")
        val initial = AppData(config = SimulationConfig(center), history = listOf(original), placeName = original.placeName)

        val edited = initial.saveCurrent(initial.selectedHistoryKey, SimulationConfig(other), "新位置")

        assertEquals(original.copy(coordinate = other, placeName = "新位置"), edited.history.single())
        assertEquals(original.key, edited.selectedHistoryKey)
        assertEquals(other, edited.config?.center)
        assertEquals("新位置", edited.placeName)
    }

    @Test
    fun explicitAddCreatesSeparateRowsAndEditingOneDoesNotMergeThem() {
        val coordinate = Coordinate(22.54, 114.05)
        val config = SimulationConfig(coordinate, drift = true)
        val initial = AppData().add(config, 1, "原地点", "a").add(config, 2, "另一个地点", "b")
        val moved = Coordinate(22.55, 114.06)
        val edited = initial.editHistory("b", moved, "手动改名")

        assertEquals(listOf("a", "b"), edited.history.map { it.key })
        assertEquals(coordinate, edited.history.first().coordinate)
        assertEquals(config.copy(center = moved), edited.config)
        assertEquals("手动改名", edited.placeName)
    }
}
