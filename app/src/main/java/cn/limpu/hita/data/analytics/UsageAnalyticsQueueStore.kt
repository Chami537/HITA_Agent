package cn.limpu.hita.data.analytics

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 使用统计本地持久化队列。
 *
 * 语义与 iOS UsageAnalyticsQueueStore 对齐：事件先落盘，批量发送，
 * 失败保留、成功移除；队列上限防膨胀。
 */
class UsageAnalyticsQueueStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** 匿名安装标识，首次生成后持久化；与账号/学号无关。 */
    val installationId: String = prefs.getString(KEY_INSTALLATION_ID, null)
        ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALLATION_ID, it).apply()
        }

    @Synchronized
    fun enqueue(event: UsageAnalyticsQueuedEvent) {
        val events = allEvents().toMutableList()
        events.add(event)
        // 上限 500 条，超出丢弃最旧（防长期离线膨胀）
        while (events.size > MAX_QUEUED_EVENTS) {
            events.removeAt(0)
        }
        save(events)
    }

    @Synchronized
    fun dequeue(limit: Int): List<UsageAnalyticsQueuedEvent> {
        val events = allEvents()
        return events.take(limit)
    }

    @Synchronized
    fun remove(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        val ids = eventIds.toHashSet()
        val remaining = allEvents().filterNot { it.eventId in ids }
        save(remaining)
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_PENDING_EVENTS).apply()
    }

    @Synchronized
    fun size(): Int = allEvents().size

    private fun allEvents(): List<UsageAnalyticsQueuedEvent> {
        val raw = prefs.getString(KEY_PENDING_EVENTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<UsageAnalyticsQueuedEvent>>() {}.type
            gson.fromJson<List<UsageAnalyticsQueuedEvent>>(raw, type).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(events: List<UsageAnalyticsQueuedEvent>) {
        if (events.isEmpty()) {
            prefs.edit().remove(KEY_PENDING_EVENTS).apply()
        } else {
            prefs.edit().putString(KEY_PENDING_EVENTS, gson.toJson(events)).apply()
        }
    }

    data class UsageAnalyticsQueuedEvent(
        val eventId: String,
        val eventName: String,
        val dimensions: Map<String, String>,
        val occurredAt: String, // ISO-8601
    )

    private companion object {
        const val PREFS_NAME = "usage_analytics"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_PENDING_EVENTS = "pending_events_v1"
        const val MAX_QUEUED_EVENTS = 500
    }
}
