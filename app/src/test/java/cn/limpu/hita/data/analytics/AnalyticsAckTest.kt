package cn.limpu.hita.data.analytics

import org.junit.Assert.*
import org.junit.Test

class AnalyticsAckTest {
    private val body = """{"ok":true,"accepted_event_ids":["a"],"duplicate_event_ids":["b"],"rejected":[{"event_id":"c","code":"invalid_dimensions"},{"event_id":"d","code":"temporary_overload"}]}"""
    @Test fun removesOnlyExplicitPermanentAcknowledgements() {
        assertEquals(setOf("a", "b", "c"), AnalyticsAck.confirmed(200, 200, body, setOf("a", "b", "c", "d", "missing")))
    }
    @Test fun retryableAndUnknownResponsesNeverClearQueue() {
        for (status in listOf(201, 202, 204, 400, 401, 403, 404, 408, 413, 429, 500, 503)) assertTrue(AnalyticsAck.confirmed(status, 200, body, setOf("a", "b", "c", "d")).isEmpty())
        for (response in listOf("<html>ok</html>", "{}", """{"ok":true,"accepted_event_ids":["a"]}""", body.replace("\"a\"", "\"foreign-id\""))) assertTrue(AnalyticsAck.confirmed(200, 200, response, setOf("a", "b", "c", "d")).isEmpty())
    }
    @Test fun legacyUses202AndExactIds() {
        val ack = """{"ok":true,"accepted_event_ids":["a"],"duplicate_event_ids":[],"rejected":[]}"""
        assertEquals(setOf("a"), AnalyticsAck.confirmed(202, 202, ack, setOf("a", "b"), true))
        assertTrue(AnalyticsAck.confirmed(200, 202, ack, setOf("a", "b"), true).isEmpty())
    }
}
