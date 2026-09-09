package cn.limpu.hita.data.analytics

import com.google.gson.JsonParser

/** Unknown responses or omitted IDs always remain queued. */
object AnalyticsAck {
    private val permanent = setOf("invalid_event_id", "unknown_event", "invalid_producer", "event_too_large", "invalid_environment", "invalid_platform", "invalid_visibility", "invalid_version", "invalid_correlation_id", "missing_session_id", "missing_operation_id", "missing_attempt_id", "invalid_measurement", "tokens_not_allowed", "invalid_dimensions", "invalid_occurred_at", "event_expired", "clock_too_far_ahead", "event_id_conflict", "invalid_source_context", "missing_outcome", "missing_service_dimensions")
    fun confirmed(status: Int, expectedStatus: Int, body: String, submitted: Set<String>, legacy: Boolean = false): Set<String> = runCatching {
        if (status != expectedStatus) return emptySet()
        val value = JsonParser().parse(body).asJsonObject
        if (!value.get("ok").asBoolean) return emptySet()
        val acknowledged = mutableSetOf<String>()
        for (key in listOf("accepted_event_ids", "duplicate_event_ids")) {
            for (item in value.getAsJsonArray(key)) {
                val id = item.asString
                if (id !in submitted || !acknowledged.add(id)) return emptySet()
            }
        }
        for (item in value.getAsJsonArray("rejected")) {
            val rejection = item.asJsonObject
            val id = rejection.get("event_id").asString
            if (id !in submitted || id in acknowledged) return emptySet()
            val reason = rejection.get(if (legacy) "reason" else "code").asString
            if (reason in permanent || (legacy && (reason in setOf("unknown event_name", "invalid occurred_at", "missing event_id", "invalid_legacy_record") || reason.startsWith("dimension not allowed:")))) acknowledged.add(id)
        }
        acknowledged
    }.getOrDefault(emptySet())
}
