package app.locationlab.platform

import app.locationlab.simulation.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceLookupTest {
    @Test
    fun acceptsDecimalPunctuationFromChineseKeyboard() {
        val point = Coordinate(22.543096, 114.057865)
        assertEquals(point, parseCoordinateSearch("22。543096，114．057865"))
        assertNull(parseCoordinateSearch("91。5，114．057865"))
    }
}
