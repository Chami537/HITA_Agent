package cn.limpu.hita.data.analytics

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UsageQueueInstrumentedTest {
    @Test fun persistsDeduplicatesLeasesAndClearsOldConsent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "analytics-test-${UUID.randomUUID()}.db"
        var store = UsageAnalyticsQueueStore(context, name)
        val id = UUID.randomUUID().toString()
        val event = QueuedUsageEvent(id, "{\"app_version\":\"old-version\"}", 2, System.currentTimeMillis(), 1, 4)
        try {
            store.enqueue(event); store.enqueue(event)
            assertEquals(1, store.size())
            assertTrue(store.acquire("sender-a"))
            val second = UsageAnalyticsQueueStore(context, name)
            assertFalse(second.acquire("sender-b")); second.close()
            store.release("sender-a"); store.close()
            store = UsageAnalyticsQueueStore(context, name)
            assertEquals("{\"app_version\":\"old-version\"}", store.batch(4, 50).single().payload)
            assertTrue(store.acquire("sender-b")); store.release("sender-b")
            store.enqueue(event.copy(eventId = "new-consent", generation = 5))
            store.clearBefore(5)
            assertEquals(listOf("new-consent"), store.batch(5, 50).map { it.eventId })
            store.enqueue(event.copy(eventId = "expired", createdAt = System.currentTimeMillis() - UsageAnalyticsQueueStore.MAX_AGE_MS - 1000, generation = 5))
            assertEquals(1, store.size()); assertEquals(1L, store.quality()["expired"])
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun capacityPreservesActiveAndTerminalEvents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "analytics-test-${UUID.randomUUID()}.db"
        val store = UsageAnalyticsQueueStore(context, name)
        try {
            val now = System.currentTimeMillis()
            store.enqueue(QueuedUsageEvent("important", "x".repeat(800_000), 2, now, 1, 1))
            repeat(3) { store.enqueue(QueuedUsageEvent("ordinary-$it", "x".repeat(800_000), 2, now + it, 0, 1)) }
            val ids = store.batch(1, 50).map { it.eventId }
            assertTrue("important" in ids); assertEquals(2, ids.size); assertEquals(2L, store.quality()["capacity"])
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun disablingTelemetryClearsTheRealQueueAndStopsRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UsageAnalyticsClient.setEnabled(context, true)
        val store = UsageAnalyticsQueueStore(context)
        try {
            val generation = context.getSharedPreferences("usage_analytics_settings", 0).getLong("generation", 0)
            store.enqueue(QueuedUsageEvent("consent-fixture", "{}", 2, System.currentTimeMillis(), 0, generation))
            UsageAnalyticsClient.setEnabled(context, false)
            val deadline = System.currentTimeMillis() + 5000
            while (store.size() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertFalse(UsageAnalyticsClient.isEnabled(context)); assertEquals(0, store.size())
            UsageAnalyticsClient.record(UsageAnalyticsEvent.SCORES_VIEWED)
            Thread.sleep(200)
            assertEquals(0, store.size())
        } finally { store.close() }
    }

    @Test fun capacityUsesUtf8BytesRatherThanCharacterCount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "analytics-test-${UUID.randomUUID()}.db"
        val store = UsageAnalyticsQueueStore(context, name)
        try {
            repeat(3) { store.enqueue(QueuedUsageEvent("unicode-$it", "汉".repeat(400_000), 1, System.currentTimeMillis(), 0, 1)) }
            assertEquals(1, store.size())
            assertEquals(2L, store.quality()["capacity"])
        } finally { store.close(); context.deleteDatabase(name) }
    }

}
