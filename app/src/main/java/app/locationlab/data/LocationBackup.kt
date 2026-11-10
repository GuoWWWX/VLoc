package app.locationlab.data

import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.decodeDriftTiming
import app.locationlab.simulation.encode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object LocationBackup {
    const val MAX_BYTES = 5 * 1024 * 1024

    fun encode(entries: List<HistoryEntry>, exportedAt: Long): String {
        require(entries.isNotEmpty())
        val locations = JSONArray()
        for (entry in entries) {
            locations.put(JSONObject().apply {
                put("id", entry.key)
                put("latitude", entry.coordinate.latitude)
                put("longitude", entry.coordinate.longitude)
                put("selectedAt", entry.selectedAt)
                put("name", entry.placeName ?: JSONObject.NULL)
                put("favorite", entry.favorite)
                put("pinned", entry.pinned)
                put("drift", entry.drift)
                put("radiusMeters", entry.radiusMeters)
                put("timing", entry.timing.encode())
            })
        }
        val text = JSONObject().apply {
            put("format", "vloc-history")
            put("version", 1)
            put("exportedAt", exportedAt)
            put("locations", locations)
        }.toString(2)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return text
    }

    fun read(input: InputStream): List<HistoryEntry> {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES)
            bytes.write(buffer, 0, count)
        }
        val tokens = JSONTokener(bytes.toString(Charsets.UTF_8.name()))
        val document = tokens.nextValue() as? JSONObject ?: throw IllegalArgumentException("Invalid backup")
        require(tokens.nextClean() == '\u0000')
        require(document.get("format") == "vloc-history" && document.integer("version") == 1L)
        val rows = document.getJSONArray("locations")
        require(rows.length() > 0)
        val keys = mutableSetOf<String>()
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val key = row.get("id") as? String ?: throw IllegalArgumentException("Invalid identifier")
            require(key.isNotBlank() && key.length <= 200 && keys.add(key))
            val name = row.get("name").let {
                if (it == JSONObject.NULL) null
                else (it as? String ?: throw IllegalArgumentException("Invalid name")).also { value ->
                    require(value.isNotBlank() && value.length <= 80)
                }
            }
            val timingText = row.get("timing") as? String ?: throw IllegalArgumentException("Invalid timing")
            val timing = decodeDriftTiming(timingText)
            require(timing.encode() == timingText)
            val radius = row.integer("radiusMeters")
            require(radius in 1..5000)
            val selectedAt = row.integer("selectedAt")
            require(selectedAt in 0..253_402_300_799_999L)
            HistoryEntry(
                coordinate = Coordinate(row.number("latitude"), row.number("longitude")),
                selectedAt = selectedAt,
                pinned = row.boolean("pinned"), favorite = row.boolean("favorite"),
                placeName = name, key = key, drift = row.boolean("drift"),
                radiusMeters = radius.toInt(), timing = timing,
            )
        }
    }

    private fun JSONObject.integer(key: String): Long =
        (get(key) as? Number)?.toString()?.toLongOrNull() ?: throw IllegalArgumentException("Invalid integer")

    private fun JSONObject.number(key: String): Double =
        (get(key) as? Number)?.toDouble() ?: throw IllegalArgumentException("Invalid number")

    private fun JSONObject.boolean(key: String): Boolean =
        get(key) as? Boolean ?: throw IllegalArgumentException("Invalid boolean")
}
