package app.locationlab.simulation

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Coordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
    }

    fun distanceTo(other: Coordinate): Double {
        val phi = Math.toRadians(latitude)
        val otherPhi = Math.toRadians(other.latitude)
        val dPhi = otherPhi - phi
        val dLambda = Math.toRadians(other.longitude - longitude)
        val a = sin(dPhi / 2).pow(2) + cos(phi) * cos(otherPhi) * sin(dLambda / 2).pow(2)
        return 2 * EARTH_RADIUS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun moved(meters: Double, bearing: Double): Coordinate {
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val angle = meters / EARTH_RADIUS
        val otherPhi = asin(
            (sin(phi) * cos(angle) + cos(phi) * sin(angle) * cos(bearing)).coerceIn(-1.0, 1.0),
        )
        val otherLambda = lambda + atan2(
            sin(bearing) * sin(angle) * cos(phi),
            cos(angle) - sin(phi) * sin(otherPhi),
        )
        return Coordinate(Math.toDegrees(otherPhi), (Math.toDegrees(otherLambda) + 540) % 360 - 180)
    }

    private companion object {
        const val EARTH_RADIUS = 6_371_008.8
    }
}

const val DEFAULT_DRIFT_RADIUS = 50

data class SimulationConfig(
    val center: Coordinate,
    val drift: Boolean = false,
    val radiusMeters: Int = DEFAULT_DRIFT_RADIUS,
    val timing: DriftTiming = DriftTiming.OnStart,
) {
    init {
        require(radiusMeters in 1..5000)
    }
}

class DriftEngine(private val config: SimulationConfig, private val random: Random = Random.Default) {
    private var last = config.center

    fun initialPoint(): Coordinate {
        if (config.drift) last = samplePoint()
        return last
    }

    fun refresh(): Coordinate {
        if (!config.drift) return config.center
        val minDistance = min(config.radiusMeters * 0.2, 20.0)
        repeat(8) {
            val candidate = samplePoint()
            if (last.distanceTo(candidate) >= minDistance) {
                last = candidate
                return candidate
            }
        }
        last = if (last.distanceTo(config.center) >= minDistance) config.center
        else config.center.moved(config.radiusMeters * 0.9, random.nextDouble(2 * PI))
        return last
    }

    private fun samplePoint(): Coordinate =
        config.center.moved(config.radiusMeters * sqrt(random.nextDouble()), random.nextDouble(2 * PI))

    fun next(): Coordinate {
        if (!config.drift) return config.center
        val maxStep = min(config.radiusMeters * 0.1, 10.0)
        repeat(12) {
            val candidate = last.moved(maxStep * sqrt(random.nextDouble()), random.nextDouble(2 * PI))
            if (config.center.distanceTo(candidate) <= config.radiusMeters) {
                last = candidate
                return candidate
            }
        }
        return last
    }
}
