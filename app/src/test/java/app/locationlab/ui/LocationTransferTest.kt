package app.locationlab.ui

import app.locationlab.data.HistoryEntry
import app.locationlab.simulation.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationTransferTest {
    @Test
    fun automaticClipboardImportRequiresARecognizedShareHeader() {
        assertNull(parseSharedLocation("22.543096, 114.057865", requireHeader = true))
        val location = parseSharedLocation(
            "位置模拟 · 位置分享\n名称：福田中心\n坐标：22.543096, 114.057865", requireHeader = true,
        )
        assertEquals(SharedLocation(Coordinate(22.543096, 114.057865), "福田中心"), location)
    }

    @Test
    fun sharedPositionRoundTripsAtFullPrecision() {
        val entry = HistoryEntry(Coordinate(22.5430967, 114.0578654), 1, placeName = "福田中心")
        assertEquals(SharedLocation(entry.coordinate, entry.placeName), parseSharedLocation(entry.shareText()))
    }

    @Test
    fun recognizesPlainCoordinatesAndRejectsUnrelatedText() {
        assertEquals(SharedLocation(Coordinate(22.54, 114.05), null), parseSharedLocation("22。54，114。05"))
        assertNull(parseSharedLocation("名称：福田中心\n坐标：22.54, 114.05"))
        assertNull(parseSharedLocation("91, 114"))
    }
}
