package cn.limpu.hita.data.analytics

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

@Entity(tableName = "events")
data class QueuedUsageEvent(
    @PrimaryKey val eventId: String,
    val payload: String,
    val schema: Int,
    val createdAt: Long,
    val priority: Int,
    val generation: Long,
)
@Entity(tableName = "lease")
data class UsageLease(@PrimaryKey val id: Int = 1, val owner: String = "", val expires: Long = 0)
@Entity(tableName = "quality")
data class UsageQuality(@PrimaryKey val reason: String, val count: Long = 0)

@Dao
interface UsageQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(event: QueuedUsageEvent)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun initLease(lease: UsageLease)
    @Query("UPDATE lease SET owner=:owner, expires=:expires WHERE id=1 AND (expires<=:now OR owner=:owner)")
    fun acquire(owner: String, now: Long, expires: Long): Int
    @Query("UPDATE lease SET owner='',expires=0 WHERE id=1 AND owner=:owner") fun release(owner: String)
    @Query("SELECT * FROM events WHERE generation=:generation ORDER BY createdAt,eventId LIMIT :limit")
    fun batch(generation: Long, limit: Int): List<QueuedUsageEvent>
    @Query("DELETE FROM events WHERE eventId IN (:ids)") fun remove(ids: Collection<String>): Int
    @Query("DELETE FROM events WHERE generation<:generation") fun clearBefore(generation: Long): Int
    @Query("DELETE FROM events WHERE createdAt<:cutoff") fun expire(cutoff: Long): Int
    @Query("SELECT count(*) FROM events") fun size(): Int
    @Query("SELECT coalesce(sum(length(CAST(payload AS BLOB))),0) FROM events") fun bytes(): Long
    @Query("SELECT eventId FROM events ORDER BY priority,createdAt,eventId LIMIT 1") fun victim(): String?
    @Query("INSERT OR IGNORE INTO quality(reason,count) VALUES(:reason,0)") fun initCounter(reason: String)
    @Query("UPDATE quality SET count=count+:amount WHERE reason=:reason") fun increment(reason: String, amount: Long)
    @Query("SELECT * FROM quality") fun quality(): List<UsageQuality>
}
@Database(entities = [QueuedUsageEvent::class, UsageLease::class, UsageQuality::class], version = 1, exportSchema = true)
abstract class UsageQueueDatabase : RoomDatabase() { abstract fun queue(): UsageQueueDao }

/** Independent transactional queue; all calls run on an IO dispatcher. */
class UsageAnalyticsQueueStore(context: Context, databaseName: String = "usage_analytics_v2.db") {
    private val prefs = context.getSharedPreferences("usage_analytics", Context.MODE_PRIVATE)
    private val database = Room.databaseBuilder(context.applicationContext, UsageQueueDatabase::class.java, databaseName).build()
    private val dao = database.queue()
    val installationId: String = prefs.getString("installation_id", null)?.takeIf(::validId)
        ?: context.getSharedPreferences("stats", Context.MODE_PRIVATE).getString("device_id", null)?.takeIf(::validId)
        ?: UUID.randomUUID().toString()

    init {
        check(prefs.edit().putString("installation_id", installationId).commit())
        dao.initLease(UsageLease())
    }
    @Synchronized fun migrateLegacy(generation: Long) {
        val raw = prefs.getString("pending_events_v1", null) ?: return
        val type = object : TypeToken<List<LegacyEvent>>() {}.type
        val events = runCatching { Gson().fromJson<List<LegacyEvent>>(raw, type) }.getOrNull()
        if (events == null) { count("legacy_decode_failed", 1); return }
        database.runInTransaction {
            events.forEach { event ->
                if (event.eventId.isNullOrBlank() || event.eventName.isNullOrBlank() || event.occurredAt.isNullOrBlank()) { count("legacy_invalid", 1); return@forEach }
                // Version at creation is unknowable for the old queue. Never relabel it as the new version.
                val payload = Gson().toJson(mapOf("event_id" to event.eventId, "event_name" to event.eventName, "occurred_at" to event.occurredAt, "dimensions" to event.dimensions.orEmpty()))
                dao.insert(QueuedUsageEvent(event.eventId, payload, 1, System.currentTimeMillis(), 0, generation))
            }
            trim()
        }
        check(prefs.edit().remove("pending_events_v1").commit())
    }
    @Synchronized fun enqueue(event: QueuedUsageEvent) = database.runInTransaction { dao.insert(event); trim() }
    @Synchronized fun batch(generation: Long, limit: Int): List<QueuedUsageEvent> {
        val expired = dao.expire(System.currentTimeMillis() - MAX_AGE_MS); count("expired", expired.toLong())
        return dao.batch(generation, limit).let { rows -> rows.filter { it.schema == rows.firstOrNull()?.schema } }
    }
    fun remove(ids: Collection<String>) { if (ids.isNotEmpty()) dao.remove(ids) }
    fun clearBefore(generation: Long) { dao.clearBefore(generation); prefs.edit().remove("pending_events_v1").commit() }
    fun acquire(owner: String): Boolean = dao.acquire(owner, System.currentTimeMillis(), System.currentTimeMillis() + 120_000) == 1
    fun release(owner: String) { dao.release(owner) }
    fun size(): Int = dao.size()
    fun close() = database.close()
    fun count(reason: String, amount: Long = 1) { if (amount > 0) { dao.initCounter(reason); dao.increment(reason, amount) } }
    fun quality(): Map<String, Long> = dao.quality().associate { it.reason to it.count }
    private fun trim() {
        count("expired", dao.expire(System.currentTimeMillis() - MAX_AGE_MS).toLong())
        while (dao.size() > 2000 || dao.bytes() > 2 * 1024 * 1024) {
            val id = dao.victim() ?: break; dao.remove(listOf(id)); count("capacity")
        }
    }
    private data class LegacyEvent(val eventId: String?, val eventName: String?, val dimensions: Map<String, String>?, val occurredAt: String?)
    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private fun validId(value: String) = runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }.getOrDefault(false)
    }
}
