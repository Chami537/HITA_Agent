package cn.limpu.hita.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasSessionGenerationGuardTest {
    @Test
    fun `current logged in session accepts routine refresh`() {
        assertTrue(
            EasSessionGenerationGuard.acceptsServiceRefresh(
                refreshEnabled = true,
                tokenGeneration = 7,
                currentGeneration = 7,
                storedSessionLoggedIn = true
            )
        )
    }

    @Test
    fun `response started before logout is rejected after relogin`() {
        assertFalse(
            EasSessionGenerationGuard.acceptsServiceRefresh(
                refreshEnabled = true,
                tokenGeneration = 7,
                currentGeneration = 8,
                storedSessionLoggedIn = true
            )
        )
    }

    @Test
    fun `refresh is rejected while logged out`() {
        assertFalse(
            EasSessionGenerationGuard.acceptsServiceRefresh(
                refreshEnabled = false,
                tokenGeneration = 8,
                currentGeneration = 8,
                storedSessionLoggedIn = false
            )
        )
    }
}
