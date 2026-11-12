package ua.echecker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun `parses tags with or without v prefix`() {
        assertEquals(listOf(1, 0), AppVersion.parse("v1.0"))
        assertEquals(listOf(1, 10, 2), AppVersion.parse("1.10.2"))
        assertEquals(listOf(2), AppVersion.parse(" V2 "))
    }

    @Test fun `rejects non-numeric versions`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.1-beta"))
        assertNull(AppVersion.parse("1..2"))
    }

    @Test fun `newer when a component is higher`() {
        assertTrue(AppVersion.isNewer("v1.1", "1.0"))
        assertTrue(AppVersion.isNewer("v2.0", "1.9"))
        assertTrue(AppVersion.isNewer("1.0.1", "1.0"))
    }

    @Test fun `compares numerically, not as text`() {
        assertTrue(AppVersion.isNewer("1.10", "1.9"))
        assertFalse(AppVersion.isNewer("1.9", "1.10"))
    }

    @Test fun `same or older is not newer`() {
        assertFalse(AppVersion.isNewer("v1.0", "1.0"))
        assertFalse(AppVersion.isNewer("1.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("0.9", "1.0"))
    }

    @Test fun `unparseable versions never count as newer`() {
        assertFalse(AppVersion.isNewer("nightly", "1.0"))
        assertFalse(AppVersion.isNewer("1.1", "dev"))
    }
}
