package app.locationlab.simulation

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

enum class DriftIntervalUnit(internal val milliseconds: Long) {
    Seconds(1_000), Minutes(60_000), Hours(3_600_000), Days(86_400_000),
}

sealed interface DriftTiming {
    data object OnStart : DriftTiming

    data class Interval(val amount: Int, val unit: DriftIntervalUnit) : DriftTiming {
        init { require(amount in 1..9999) }
        internal val milliseconds: Long get() = amount * unit.milliseconds
    }

    data class Daily(val hour: Int, val minute: Int) : DriftTiming {
        init { require(hour in 0..23 && minute in 0..59) }
    }
}

internal fun DriftTiming.encode(): String = when (this) {
    DriftTiming.OnStart -> "start"
    is DriftTiming.Interval -> "interval:$amount:${unit.name}"
    is DriftTiming.Daily -> "daily:$hour:$minute"
}

internal fun decodeDriftTiming(encoded: String?, legacySeconds: Int? = null): DriftTiming {
    if (encoded == null) {
        return legacySeconds?.takeIf { it in 1..60 }?.let { DriftTiming.Interval(it, DriftIntervalUnit.Seconds) }
            ?: DriftTiming.OnStart
    }
    val parts = encoded.split(':')
    if (parts.size != 3) return DriftTiming.OnStart
    val amount = parts[1].toIntOrNull() ?: return DriftTiming.OnStart
    return when (parts[0]) {
        "interval" -> {
            val unit = DriftIntervalUnit.entries.firstOrNull { it.name == parts[2] }
            if (unit != null && amount in 1..9999) DriftTiming.Interval(amount, unit) else DriftTiming.OnStart
        }
        "daily" -> {
            val minute = parts[2].toIntOrNull()
            if (amount in 0..23 && minute != null && minute in 0..59) DriftTiming.Daily(amount, minute)
            else DriftTiming.OnStart
        }
        else -> DriftTiming.OnStart
    }
}

internal class DriftTimer(private val timing: DriftTiming, startedElapsed: Long, startedWall: ZonedDateTime) {
    private var nextElapsed = startedElapsed + ((timing as? DriftTiming.Interval)?.milliseconds ?: 0)
    private var nextWall = (timing as? DriftTiming.Daily)?.nextAfter(startedWall, null)
    private var lastWall = startedWall
    private var lastDailyDate: LocalDate? = null

    fun millisecondsUntil(elapsed: Long, wall: ZonedDateTime): Long = when (timing) {
        DriftTiming.OnStart -> Long.MAX_VALUE
        is DriftTiming.Interval -> (nextElapsed - elapsed).coerceAtLeast(0)
        is DriftTiming.Daily -> {
            if (wall.zone != lastWall.zone || wall.isBefore(lastWall)) {
                nextWall = timing.nextAfter(wall, lastDailyDate)
            }
            lastWall = wall
            Duration.between(wall, requireNotNull(nextWall)).toMillis().coerceAtLeast(0)
        }
    }

    fun advanceIfDue(elapsed: Long, wall: ZonedDateTime): Boolean {
        if (millisecondsUntil(elapsed, wall) > 0) return false
        when (timing) {
            DriftTiming.OnStart -> return false
            is DriftTiming.Interval -> {
                val period = timing.milliseconds
                nextElapsed += ((elapsed - nextElapsed) / period + 1) * period
            }
            is DriftTiming.Daily -> {
                lastDailyDate = requireNotNull(nextWall).toLocalDate()
                nextWall = timing.nextAfter(wall, lastDailyDate)
            }
        }
        return true
    }
}

private fun DriftTiming.Daily.nextAfter(now: ZonedDateTime, alreadyRanOn: LocalDate?): ZonedDateTime {
    val date = now.toLocalDate()
    val today = date.atTime(hour, minute).atZone(now.zone)
    return if (today.isAfter(now) && date != alreadyRanOn) today
    else date.plusDays(1).atTime(hour, minute).atZone(now.zone)
}
