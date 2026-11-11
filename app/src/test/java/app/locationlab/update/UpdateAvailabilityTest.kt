package app.locationlab.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAvailabilityTest {
    private val release = AppUpdate(
        version = "1.0.0",
        code = 25,
        apk = UpdateAsset("VLoc-v1.0.0-arm64-v8a.apk", "0".repeat(64), 1024),
        deltas = emptyList(),
    )

    @Test
    fun confirmedUpdateShowsPromptAndBadge() {
        val availability = UpdateAvailability(update = release)
        assertEquals(release, availability.reminder(handledCode = 0))
        assertTrue(availability.showBadge)
    }

    @Test
    fun deferringAnUpdateKeepsBadgeAndAllowsTheNextReleaseToPrompt() {
        val availability = UpdateAvailability(update = release)
        assertNull(availability.reminder(handledCode = release.code))
        assertTrue(availability.showBadge)
        val nextRelease = release.copy(version = "1.0.1", code = 26)
        assertEquals(nextRelease, UpdateAvailability(update = nextRelease).reminder(handledCode = release.code))
    }

    @Test
    fun cachedAvailabilityShowsBadgeWithoutPrompt() {
        val availability = UpdateAvailability(cachedUpdateAvailable = true)
        assertTrue(availability.showBadge)
        assertNull(availability.reminder(handledCode = 0))
    }

    @Test
    fun noAvailableUpdateClearsPromptAndBadge() {
        val availability = UpdateAvailability(update = null)
        assertFalse(availability.showBadge)
        assertNull(availability.reminder(handledCode = 0))
    }
}
