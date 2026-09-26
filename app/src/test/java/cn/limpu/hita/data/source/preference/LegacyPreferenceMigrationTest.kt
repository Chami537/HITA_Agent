package cn.limpu.hita.data.source.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPreferenceMigrationTest {
    @Test
    fun `failed persistent copy leaves legacy data untouched`() {
        var deleted = false
        val copied = LegacyPreferenceMigration.copyThenDelete(
            mapOf("username" to "student", "password" to "secret"),
            persist = { false },
            deleteLegacy = { deleted = true; true }
        )
        assertFalse(copied)
        assertFalse(deleted)
    }

    @Test
    fun `legacy data is deleted only after its persistent copy`() {
        val events = mutableListOf<String>()
        val copied = LegacyPreferenceMigration.copyThenDelete(
            mapOf("username" to "student", "password" to "secret"),
            persist = { values ->
                assertEquals("student", values["username"])
                assertEquals("secret", values["password"])
                events += "persist"
                true
            },
            deleteLegacy = { events += "delete"; true }
        )
        assertTrue(copied)
        assertEquals(listOf("persist", "delete"), events)
    }

    @Test
    fun `encrypted legacy session overrides stale plaintext even when logged out`() {
        val plaintext = mapOf("accessToken" to "old-session", "username" to "student")
        assertEquals(
            emptyMap<String, String>(),
            LegacyPreferenceMigration.preferredSource(plaintext, emptyMap<String, String>())
        )
        assertEquals(
            mapOf("accessToken" to "new-session"),
            LegacyPreferenceMigration.preferredSource(
                plaintext,
                mapOf("accessToken" to "new-session")
            )
        )
    }
}
