package app.locationlab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.locationlab.map.MapSource
import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DEFAULT_DRIFT_RADIUS
import app.locationlab.simulation.SimulationConfig
import app.locationlab.simulation.decodeDriftTiming
import app.locationlab.simulation.encode
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

private val Context.locationData by preferencesDataStore(name = "location_lab")
private val dataKey = stringPreferencesKey("app_data")
private val lastLatitude = doublePreferencesKey("last_simulation_latitude")
private val lastLongitude = doublePreferencesKey("last_simulation_longitude")

class LocationStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.locationData)
    val data = store.data.map { it[dataKey] }.distinctUntilChanged().map(::decode)
    val lastSimulation = store.data.map { it.lastSimulationPoint() }.distinctUntilChanged()

    suspend fun recordSimulation(point: Coordinate): Coordinate? {
        var previous: Coordinate? = null
        store.edit {
            previous = it.lastSimulationPoint()
            it[lastLatitude] = point.latitude
            it[lastLongitude] = point.longitude
        }
        return previous
    }

    suspend fun edit(transform: (AppData) -> AppData) {
        store.edit { it[dataKey] = encode(transform(decode(it[dataKey]))) }
    }

    private fun decode(raw: String?): AppData {
        if (raw == null) return AppData()
        val json = JSONObject(raw)
        val configData = json.optJSONObject("config")
        val config = configData?.let {
            SimulationConfig(it.coordinate(), it.getBoolean("drift"), it.getInt("radius"),
                decodeDriftTiming(it.optString("timing").takeIf(String::isNotEmpty), it.optInt("interval", 0)))
        }
        val history = json.optJSONArray("history") ?: JSONArray()
        val selectedKey = if (json.has("selectedHistoryKey")) {
            json.optString("selectedHistoryKey").ifEmpty { null }
        } else config?.center?.historyKey()
        return AppData(
            config = config,
            history = List(history.length()) { i ->
                val entry = history.getJSONObject(i)
                val coordinate = entry.coordinate()
                val key = entry.optString("key", coordinate.historyKey())
                val options = if (entry.has("drift")) entry.configuration() else
                    config?.copy(center = coordinate)?.takeIf { key == selectedKey } ?: SimulationConfig(coordinate)
                HistoryEntry(coordinate, entry.getLong("at"), entry.getBoolean("pin"),
                    entry.getBoolean("favorite"), entry.nameOrNull(), key).withConfiguration(options)
            },
            appearance = Appearance.valueOf(json.optString("appearance", Appearance.System.name)),
            mapSource = MapSource.entries.firstOrNull { it.name == json.optString("mapSource") }
                ?: MapSource.OpenFreeMap,
            onboardingSeen = json.optBoolean("onboardingSeen", config != null),
            placeName = configData?.nameOrNull(),
            selectedHistoryKey = selectedKey,
            lastHandledShare = json.optString("lastHandledShare").takeIf { it.isNotBlank() },
        )
    }

    private fun encode(data: AppData): String = JSONObject().apply {
        put("appearance", data.appearance.name)
        put("mapSource", data.mapSource.name)
        put("onboardingSeen", data.onboardingSeen)
        put("selectedHistoryKey", data.selectedHistoryKey.orEmpty())
        put("lastHandledShare", data.lastHandledShare)
        data.config?.let {
            put("config", it.center.json().put("drift", it.drift).put("radius", it.radiusMeters)
                .put("timing", it.timing.encode()).put("name", data.placeName))
        }
        put("history", JSONArray().apply {
            for (entry in data.history) {
                put(entry.coordinate.json().put("at", entry.selectedAt).put("pin", entry.pinned)
                    .put("favorite", entry.favorite).put("name", entry.placeName).put("key", entry.key)
                    .put("drift", entry.drift).put("radius", entry.radiusMeters).put("timing", entry.timing.encode()))
            }
        })
    }.toString()

    private fun Coordinate.json() = JSONObject().put("lat", latitude).put("lon", longitude)
    private fun JSONObject.coordinate() = Coordinate(getDouble("lat"), getDouble("lon"))
    private fun JSONObject.configuration() = SimulationConfig(coordinate(), optBoolean("drift"),
        optInt("radius", DEFAULT_DRIFT_RADIUS), decodeDriftTiming(optString("timing"), optInt("interval")))
    private fun JSONObject.nameOrNull() = optString("name").takeIf { it.isNotBlank() }
}

private fun Preferences.lastSimulationPoint(): Coordinate? {
    val latitude = this[lastLatitude]?.takeIf { it in -90.0..90.0 } ?: return null
    val longitude = this[lastLongitude]?.takeIf { it in -180.0..180.0 } ?: return null
    return Coordinate(latitude, longitude)
}
