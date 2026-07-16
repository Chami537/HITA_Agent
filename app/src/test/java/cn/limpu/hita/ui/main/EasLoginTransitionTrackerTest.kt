package cn.limpu.hita.ui.main

import cn.limpu.hita.data.model.eas.EASToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasLoginTransitionTrackerTest {
    @Test
    fun `routine token refresh does not retrigger login work`() {
        val tracker = EasLoginTransitionTracker()
        val initial = loggedInToken("20240001", "session-1")
        val refreshed = loggedInToken("20240001", "session-2")

        assertTrue(tracker.shouldTriggerLoginWork(initial))
        assertFalse(tracker.shouldTriggerLoginWork(refreshed))
        assertFalse(tracker.shouldTriggerLoginWork(refreshed))
    }

    @Test
    fun `logout then login triggers work again`() {
        val tracker = EasLoginTransitionTracker()

        assertTrue(tracker.shouldTriggerLoginWork(loggedInToken("20240001", "session-1")))
        assertFalse(tracker.shouldTriggerLoginWork(EASToken()))
        assertTrue(tracker.shouldTriggerLoginWork(loggedInToken("20240001", "session-2")))
    }

    @Test
    fun `switching account is treated as a new login`() {
        val tracker = EasLoginTransitionTracker()

        assertTrue(tracker.shouldTriggerLoginWork(loggedInToken("20240001", "session-1")))
        assertTrue(tracker.shouldTriggerLoginWork(loggedInToken("20240002", "session-2")))
    }

    private fun loggedInToken(username: String, session: String) = EASToken().apply {
        campus = EASToken.Campus.SHENZHEN
        this.username = username
        webCookies["JSESSIONID"] = session
        webCookies["route"] = "route"
    }
}
