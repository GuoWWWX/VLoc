package app.locationlab.simulation

import kotlin.math.min
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftEngineTest {
    @Test
    fun fixedModeAlwaysReturnsCenter() {
        val center = Coordinate(22.543096, 114.057865)
        val engine = DriftEngine(SimulationConfig(center))
        repeat(10) { assertEquals(center, engine.next()) }
        assertEquals(center, engine.refresh())
    }

    @Test
    fun manualRefreshStaysInsideRadiusAndMovesAwayFromPreviousPoint() {
        for (center in listOf(Coordinate(22.54, 114.06), Coordinate(89.9999, 179.9999))) {
            for (radius in listOf(1, 50, 5000)) {
                val engine = DriftEngine(SimulationConfig(center, drift = true, radiusMeters = radius), Random(24))
                var previous = engine.initialPoint()
                repeat(200) {
                    val next = engine.refresh()
                    assertTrue(center.distanceTo(next) <= radius + 0.01)
                    assertTrue(previous.distanceTo(next) >= min(radius * 0.2, 20.0) - 0.01)
                    previous = next
                }
            }
        }
    }

    @Test
    fun manualRefreshMovesEvenWhenRandomSamplesRepeat() {
        val center = Coordinate(22.54, 114.06)
        val repeatingRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val engine = DriftEngine(SimulationConfig(center, drift = true), repeatingRandom)
        val first = engine.initialPoint()
        val refreshed = engine.refresh()
        assertTrue(first.distanceTo(refreshed) in 10.0..50.0)
    }

    @Test
    fun driftRespectsRadiusAndStepNearDateLineAndPoles() {
        for (center in listOf(Coordinate(0.0, 179.999999), Coordinate(89.9999, -179.9999))) {
            for (radius in listOf(1, 100, 5000)) {
                val engine = DriftEngine(SimulationConfig(center, drift = true, radiusMeters = radius), Random(24))
                var previous = center
                var moved = false
                repeat(1000) {
                    val next = engine.next()
                    assertTrue(center.distanceTo(next) <= radius + 0.1)
                    assertTrue(previous.distanceTo(next) <= min(radius * 0.1, 10.0) + 0.1)
                    moved = moved || next != previous
                    previous = next
                }
                assertTrue(moved)
            }
        }
    }
}
