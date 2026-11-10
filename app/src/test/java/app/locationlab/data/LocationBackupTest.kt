package app.locationlab.data

import app.locationlab.simulation.Coordinate
import app.locationlab.simulation.DriftIntervalUnit
import app.locationlab.simulation.DriftTiming
import java.io.ByteArrayInputStream
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationBackupTest {
    private val entries = listOf(
        HistoryEntry(Coordinate(22.543096, 114.057865), 1_700_000_000_000, pinned = true,
            favorite = true, placeName = "深圳市民中心", key = "first", drift = true,
            radiusMeters = 5000, timing = DriftTiming.Daily(8, 30)),
        HistoryEntry(Coordinate(-90.0, -180.0), 1, key = "second",
            radiusMeters = 1, timing = DriftTiming.Interval(9999, DriftIntervalUnit.Days)),
        HistoryEntry(Coordinate(90.0, 180.0), 2, key = "third"),
    )

    private fun read(text: String) = LocationBackup.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    private fun document() = JSONObject(LocationBackup.encode(entries, 1_800_000_000_000))

    @Test
    fun backupRoundTripPreservesEveryLocationFieldAndSelectedSubset() {
        assertEquals(entries, read(LocationBackup.encode(entries, 1_800_000_000_000)))
        assertEquals(entries.take(1), read(LocationBackup.encode(entries.take(1), 1_800_000_000_000)))
    }

    @Test
    fun invalidEntryRejectsTheWholeBackup() {
        for ((field, value) in listOf(
            "latitude" to 91, "longitude" to -181, "radiusMeters" to 0,
            "radiusMeters" to 1.5, "selectedAt" to -1, "selectedAt" to Long.MAX_VALUE,
            "timing" to "daily:25:00", "timing" to "interval:0:Seconds",
            "favorite" to "true", "id" to "", "name" to "a".repeat(81),
        )) {
            val document = document()
            document.getJSONArray("locations").getJSONObject(1).put(field, value)
            assertThrows("$field=$value", IllegalArgumentException::class.java) { read(document.toString()) }
        }
    }

    @Test
    fun duplicateIdentifiersAndUnsupportedVersionsAreRejected() {
        val duplicate = document()
        duplicate.getJSONArray("locations").getJSONObject(1).put("id", "first")
        assertThrows(IllegalArgumentException::class.java) { read(duplicate.toString()) }
        assertThrows(IllegalArgumentException::class.java) { read(document().put("version", 2).toString()) }
        assertThrows(IllegalArgumentException::class.java) { read(document().put("format", "other").toString()) }
        assertThrows(IllegalArgumentException::class.java) { read(document().toString() + "trailing") }
    }

    @Test
    fun oversizedInputIsRejectedBeforeParsing() {
        val bytes = ByteArray(LocationBackup.MAX_BYTES + 1) { ' '.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) { LocationBackup.read(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun truncatedFilesAndMissingFieldsAreRejected() {
        val text = document().toString()
        assertThrows(JSONException::class.java) { read(text.dropLast(2)) }
        val missing = document()
        missing.getJSONArray("locations").getJSONObject(0).remove("latitude")
        assertThrows(JSONException::class.java) { read(missing.toString()) }
    }

    @Test
    fun restoreMergesByIdAndRepeatedImportsDoNotCreateDuplicates() {
        val existing = entries[0].copy(placeName = "旧名称", favorite = false)
        val untouched = entries[1].copy(key = "keep")
        val data = AppData(config = existing.configuration(), selectedHistoryKey = existing.key,
            placeName = existing.placeName, history = listOf(existing, untouched),
            appearance = Appearance.Dark, lastSimulation = Coordinate(22.54, 114.05))

        val restored = data.importHistory(entries)

        assertEquals(listOf(entries[0], untouched, entries[1], entries[2]), restored.history)
        assertEquals(entries[0].configuration(), restored.config)
        assertEquals(entries[0].placeName, restored.placeName)
        assertEquals(data.selectedHistoryKey, restored.selectedHistoryKey)
        assertEquals(data.appearance, restored.appearance)
        assertEquals(data.lastSimulation, restored.lastSimulation)
        assertEquals(restored, restored.importHistory(entries))
        assertEquals(null, AppData().importHistory(entries).config)
    }
}
