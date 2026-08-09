package cn.limpu.hita.data.analytics

import android.content.Context
import cn.limpu.hita.BuildConfig
import cn.limpu.hita.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 统一使用统计客户端（Android 端）。
 *
 * 协议（docs/unified-protocol.md §2）：
 * - 本地队列持久化，批量上报 POST {AGENT_BACKEND_BASE_URL}/api/usage
 * - 4xx 永久丢弃（协议不匹配），网络错误/5xx 保留重试
 * - 设置开关默认开启，关闭后不再记录与上报
 */
object UsageAnalyticsClient {

    private const val TAG = "UsageAnalytics"
    private const val BATCH_SIZE = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var contextRef: Context? = null
    @Volatile
    private var queueStore: UsageAnalyticsQueueStore? = null

    /** 必须在 Application.onCreate 中初始化一次。 */
    fun initialize(context: Context) {
        if (contextRef == null) {
            contextRef = context.applicationContext
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            queueStore()?.clear()
        }
    }

    /** 记录一个事件（白名单枚举，维度由调用方按协议提供）。 */
    fun record(event: UsageAnalyticsEvent, dimensions: Map<String, String> = emptyMap()) {
        val appContext = contextRef ?: return
        if (!isEnabled(appContext)) return
        val store = queueStore() ?: return
        val queued = UsageAnalyticsQueueStore.UsageAnalyticsQueuedEvent(
            eventId = UUID.randomUUID().toString(),
            eventName = event.eventName,
            dimensions = dimensions,
            occurredAt = ISO_FORMAT.format(Date()),
        )
        store.enqueue(queued)
        // 前台事件立即触发一次批量发送
        if (event == UsageAnalyticsEvent.APP_FOREGROUND) {
            flush()
        }
    }

    /** 批量发送队列中的事件。 */
    fun flush() {
        val appContext = contextRef ?: return
        val store = queueStore() ?: return
        if (!isEnabled(appContext)) return
        if (store.size() == 0) return
        scope.launch {
            val batch = store.dequeue(BATCH_SIZE)
            if (batch.isEmpty()) return@launch
            val accepted = sendBatch(appContext, batch)
            if (accepted) {
                store.remove(batch.map { it.eventId })
            } else {
                LogUtils.d("$TAG: batch retained for retry (${batch.size})")
            }
        }
    }

    private fun queueStore(): UsageAnalyticsQueueStore? {
        val appContext = contextRef ?: return null
        var store = queueStore
        if (store == null) {
            synchronized(this) {
                store = queueStore
                if (store == null) {
                    store = UsageAnalyticsQueueStore(appContext)
                    queueStore = store
                }
            }
        }
        return store
    }

    /** @return true 表示已确认接收（200），其余情况保留队列重试。 */
    private fun sendBatch(context: Context, batch: List<UsageAnalyticsQueueStore.UsageAnalyticsQueuedEvent>): Boolean {
        val payload = buildPayload(context, batch) ?: return false
        val endpoint = BuildConfig.AGENT_BACKEND_BASE_URL.trimEnd('/') + "/api/usage"
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "HITA-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code in 200..299 -> true
                    response.code in 400..499 -> {
                        // 协议不匹配（事件名/维度违规），永久丢弃，避免死循环
                        LogUtils.w("$TAG: batch permanently rejected HTTP ${response.code}")
                        true
                    }
                    else -> {
                        LogUtils.d("$TAG: retryable HTTP ${response.code}")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.d("$TAG: send failed ${e.message}")
            false
        }
    }

    private fun buildPayload(
        context: Context,
        batch: List<UsageAnalyticsQueueStore.UsageAnalyticsQueuedEvent>,
    ): String? {
        val store = queueStore() ?: return null
        return try {
            val events = JSONArray()
            for (event in batch) {
                events.put(
                    JSONObject()
                        .put("event_id", event.eventId)
                        .put("event_name", event.eventName)
                        .put("occurred_at", event.occurredAt)
                        .put("dimensions", JSONObject(event.dimensions))
                )
            }
            JSONObject()
                .put("app_id", "hita-android")
                .put("installation_id", store.installationId)
                .put("platform", "android")
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("build_number", BuildConfig.VERSION_CODE.toString())
                .put("events", events)
                .toString()
        } catch (e: Exception) {
            LogUtils.e("$TAG: build payload failed", e)
            null
        }
    }

    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    private const val PREFS_NAME = "usage_analytics_settings"
    private const val KEY_ENABLED = "enabled"
}
