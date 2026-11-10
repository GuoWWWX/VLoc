package app.locationlab.simulation

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftTimingTest {
    private val morning = ZonedDateTime.of(2026, 11, 10, 7, 0, 0, 0, ZoneId.of("Asia/Singapore"))

    @Test
    fun legacySecondsAndNewTimingsRoundTrip() {
        assertEquals(DriftTiming.Interval(5, DriftIntervalUnit.Seconds), decodeDriftTiming(null, 5))
        for (timing in listOf(DriftTiming.OnStart, DriftTiming.Interval(2, DriftIntervalUnit.Hours), DriftTiming.Daily(8, 30))) {
            assertEquals(timing, decodeDriftTiming(timing.encode()))
        }
    }

    @Test
    fun startupOnlyDriftsWithinRadiusAndNeverReschedules() {
        val center = Coordinate(22.543096, 114.057865)
        val config = SimulationConfig(center, drift = true, radiusMeters = 100)
        val point = DriftEngine(config, Random(24)).initialPoint()
        val timer = DriftTimer(config.timing, 0, morning)

        assertTrue(center.distanceTo(point) in 0.01..100.0)
        assertEquals(Long.MAX_VALUE, timer.millisecondsUntil(86_400_000, morning.plusDays(1)))
        assertFalse(timer.advanceIfDue(86_400_000, morning.plusDays(1)))
    }

    @Test
    fun intervalsUseElapsedTimeAndSkipMissedOccurrences() {
        val timer = DriftTimer(DriftTiming.Interval(1, DriftIntervalUnit.Days), 0, morning)

        assertFalse(timer.advanceIfDue(86_399_999, morning.plusDays(2)))
        assertTrue(timer.advanceIfDue(3 * 86_400_000L + 100, morning.plusDays(2)))
        assertFalse(timer.advanceIfDue(3 * 86_400_000L + 100, morning.plusDays(2)))
        assertEquals(86_399_900L, timer.millisecondsUntil(3 * 86_400_000L + 100, morning.plusDays(2)))
    }

    @Test
    fun dailyTimingUsesLocalTimeAndDoesNotRepeatAfterTheClockMovesBack() {
        val timer = DriftTimer(DriftTiming.Daily(8, 30), 0, morning)
        val due = morning.plusMinutes(90)

        assertEquals(5_400_000L, timer.millisecondsUntil(0, morning))
        assertFalse(timer.advanceIfDue(0, due.minusSeconds(1)))
        assertTrue(timer.advanceIfDue(0, due))
        assertFalse(timer.advanceIfDue(0, due.minusMinutes(10)))
        assertFalse(timer.advanceIfDue(0, due))
        assertTrue(timer.advanceIfDue(0, due.plusDays(1)))
    }

    @Test
    fun countdownUsesTheSameIntervalDeadlineAndSurvivesWallClockChanges() {
        val timer = DriftTimer(DriftTiming.Interval(1, DriftIntervalUnit.Minutes), 1000, morning)
        val deadline = requireNotNull(timer.nextDeadline)
        assertEquals(60_000L, deadline.remainingMilliseconds(1000, morning))
        assertEquals(30_000L, deadline.remainingMilliseconds(31_000, morning.plusHours(2)))
        // A manual refresh before the deadline must leave the existing schedule intact.
        assertFalse(timer.advanceIfDue(31_000, morning.plusHours(2)))
        assertEquals(deadline, timer.nextDeadline)
        assertTrue(timer.advanceIfDue(61_000, morning.plusMinutes(1)))
        assertEquals(60_000L, requireNotNull(timer.nextDeadline).remainingMilliseconds(61_000, morning))
    }

    @Test
    fun dailyCountdownFollowsTomorrowAndTimezoneChanges() {
        val timer = DriftTimer(DriftTiming.Daily(8, 30), 0, morning)
        assertEquals(5_400_000L, requireNotNull(timer.nextDeadline).remainingMilliseconds(0, morning))
        val due = morning.plusMinutes(90)
        assertTrue(timer.advanceIfDue(0, due))
        assertEquals(86_400_000L, requireNotNull(timer.nextDeadline).remainingMilliseconds(0, due))
        val shifted = due.withZoneSameInstant(ZoneId.of("Europe/London"))
        val wait = timer.millisecondsUntil(0, shifted)
        assertEquals(wait, requireNotNull(timer.nextDeadline).remainingMilliseconds(0, shifted))
        assertEquals(null, DriftTimer(DriftTiming.OnStart, 0, morning).nextDeadline)
    }
}
