package app.locationlab.data

import app.locationlab.map.MapSource
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DEFAULT_DRIFT_RADIUS
import app.locationlab.simulation.DriftTiming
import app.locationlab.simulation.SimulationConfig
import kotlin.math.roundToInt

enum class Appearance { System, Light, Dark }

data class HistoryEntry(
    val coordinate: Coordinate,
    val selectedAt: Long,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val placeName: String? = null,
    val key: String = coordinate.historyKey(),
    val drift: Boolean = false,
    val radiusMeters: Int = DEFAULT_DRIFT_RADIUS,
    val timing: DriftTiming = DriftTiming.OnStart,
) {
    fun configuration() = SimulationConfig(coordinate, drift, radiusMeters, timing)

    fun withConfiguration(config: SimulationConfig) =
        copy(coordinate = config.center, drift = config.drift, radiusMeters = config.radiusMeters, timing = config.timing)
}

data class AppData(
    val config: SimulationConfig? = null,
    val history: List<HistoryEntry> = emptyList(),
    val appearance: Appearance = Appearance.System,
    val mapSource: MapSource = MapSource.AMapPreview,
    val onboardingSeen: Boolean = false,
    val placeName: String? = null,
    val selectedHistoryKey: String? = config?.center?.historyKey(),
    val lastHandledShare: String? = null,
    val lastSimulation: Coordinate? = null,
    val setupGuideSeen: Boolean = false,
) {
    fun add(config: SimulationConfig, now: Long, name: String?, key: String, makeCurrent: Boolean = true): AppData {
        val entry = HistoryEntry(config.center, now, placeName = name.normalizedName(), key = key)
            .withConfiguration(config)
        val added = copy(history = history + entry)
        return if (makeCurrent) added.use(entry) else added
    }

    fun use(key: String, now: Long): AppData {
        val entry = history.firstOrNull { it.key == key } ?: return this
        return update(key) { it.copy(selectedAt = now) }
            .use(entry)
    }

    fun saveCurrent(key: String?, config: SimulationConfig, name: String?): AppData {
        val entry = history.firstOrNull { it.key == key }
        val selectedName = name.normalizedName()
        if (entry == null) {
            return copy(config = config, placeName = selectedName, selectedHistoryKey = key)
        }
        val updated = entry.withConfiguration(config).copy(placeName = selectedName)
        return update(entry.key) { updated }.use(updated)
    }

    fun editHistory(key: String, coordinate: Coordinate, name: String?): AppData {
        val updated = update(key) { it.copy(coordinate = coordinate, placeName = name.normalizedName()) }
        if (key != selectedHistoryKey) return updated
        val entry = updated.history.firstOrNull { it.key == key } ?: return updated
        return updated.use(entry)
    }

    private fun use(entry: HistoryEntry): AppData =
        copy(config = entry.configuration(), placeName = entry.placeName, selectedHistoryKey = entry.key)

    fun toggleDrift(): AppData {
        val selected = config ?: return this
        return saveCurrent(selectedHistoryKey, selected.copy(drift = !selected.drift), placeName)
    }

    fun update(key: String, change: (HistoryEntry) -> HistoryEntry): AppData =
        copy(history = history.map { if (it.key == key) change(it) else it })

    fun removeHistory(key: String): AppData = removeHistory(setOf(key))

    fun removeHistory(keys: Set<String>): AppData = copy(history = history.filterNot { it.key in keys })

    fun favoriteHistory(keys: Set<String>): AppData =
        copy(history = history.map { if (it.key in keys) it.copy(favorite = true) else it })

    fun importHistory(entries: List<HistoryEntry>): AppData {
        val imported = entries.associateBy { it.key }
        val existingKeys = history.mapTo(mutableSetOf()) { it.key }
        val merged = copy(history = history.map { imported[it.key] ?: it } +
            entries.filterNot { it.key in existingKeys })
        val selected = imported[selectedHistoryKey]
        return if (selected == null) merged else merged.use(selected)
    }

    fun clearOrdinaryHistory(): AppData = copy(history = history.filter { it.pinned || it.favorite })

    fun visibleHistory(favoritesOnly: Boolean): List<HistoryEntry> =
        history.filter { !favoritesOnly || it.favorite }
            .sortedWith(compareByDescending<HistoryEntry> { it.pinned }.thenByDescending { it.selectedAt })
}

internal fun Coordinate.historyKey(): String {
    val lat = (latitude * 1_000_000).roundToInt()
    val lon = (longitude * 1_000_000).roundToInt()
    return "$lat:${if (lon == 180_000_000) -lon else lon}"
}

internal fun String?.normalizedName(): String? = this?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
