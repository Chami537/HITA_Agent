package cn.limpu.hita.data.notice

import android.content.Context
import cn.limpu.hita.BuildConfig
import cn.limpu.hita.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 公告中心（Android 端）。
 *
 * 协议见 docs/unified-protocol.md §4：
 * GET {AGENT_BACKEND_BASE_URL}/api/notices?app_id=...&app_version=...&build=...
 * kind: version(版本提醒) / service(服务公告) / incident(故障通知)
 * severity: critical(全屏阻断) / info(列表展示)
 */
data class AppNotice(
    val id: String,
    val kind: String,
    val severity: String,
    val title: String,
    val body: String,
    val minAppVersion: Long?,
    val affectedMinVersion: Long?,
    val startsAt: Long?,
    val endsAt: Long?,
) {
    val isCritical: Boolean get() = severity == "critical"
    val isVersionKind: Boolean get() = kind == "version"
}

object AppNoticeCenter {

    private const val TAG = "AppNoticeCenter"
    private const val CACHE_KEY = "cached_notices_v1"
    private const val PREFS_NAME = "app_notice"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 异步拉取公告并缓存；回调在主线程执行（UI 操作安全）。 */
    fun fetch(context: Context, onResult: (List<AppNotice>) -> Unit = {}) {
        val appContext = context.applicationContext
        scope.launch {
            val fetched = try {
                requestNotices(appContext)
            } catch (e: Exception) {
                LogUtils.d("$TAG: fetch failed ${e.message}")
                null
            }
            val notices = fetched ?: cachedNotices(appContext)
            if (fetched != null) {
                cacheNotices(appContext, fetched)
            }
            withContext(Dispatchers.Main) {
                onResult(notices)
            }
        }
    }

    fun cachedNotices(context: Context): List<AppNotice> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                parseNotice(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 命中当前时间窗且版本范围的公告。 */
    fun activeNotices(notices: List<AppNotice>, now: Long = System.currentTimeMillis()): List<AppNotice> {
        val currentVersion = BuildConfig.VERSION_CODE.toLong()
        return notices.filter { notice ->
            val started = notice.startsAt?.let { now >= it } ?: true
            val ended = notice.endsAt?.let { now <= it } ?: true
            val affected = notice.affectedMinVersion?.let { currentVersion >= it } ?: true
            started && ended && affected
        }.sortedBy { if (it.isCritical) 0 else 1 }
    }

    private fun requestNotices(context: Context): List<AppNotice>? {
        val endpoint = BuildConfig.AGENT_BACKEND_BASE_URL.trimEnd('/') +
            "/api/notices?app_id=hita-android" +
            "&app_version=${BuildConfig.VERSION_NAME}" +
            "&build=${BuildConfig.VERSION_CODE}"
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("User-Agent", "HITA-Android/${BuildConfig.VERSION_NAME}")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val array = root.optJSONArray("notices") ?: JSONArray()
            return (0 until array.length()).mapNotNull { i ->
                parseNotice(array.getJSONObject(i))
            }
        }
    }

    private fun parseNotice(obj: JSONObject): AppNotice? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        return AppNotice(
            id = id,
            kind = obj.optString("kind", "service"),
            severity = obj.optString("severity", "info"),
            title = obj.optString("title", "公告"),
            body = obj.optString("body", ""),
            minAppVersion = obj.optLong("min_app_version", -1L).takeIf { it >= 0 },
            affectedMinVersion = obj.optLong("affected_min_version", -1L).takeIf { it >= 0 },
            startsAt = obj.optLong("starts_at", -1L).takeIf { it >= 0 },
            endsAt = obj.optLong("ends_at", -1L).takeIf { it >= 0 },
        )
    }

    private fun cacheNotices(context: Context, notices: List<AppNotice>) {
        val array = JSONArray()
        notices.forEach { notice ->
            array.put(
                JSONObject()
                    .put("id", notice.id)
                    .put("kind", notice.kind)
                    .put("severity", notice.severity)
                    .put("title", notice.title)
                    .put("body", notice.body)
                    .put("min_app_version", notice.minAppVersion ?: JSONObject.NULL)
                    .put("affected_min_version", notice.affectedMinVersion ?: JSONObject.NULL)
                    .put("starts_at", notice.startsAt ?: JSONObject.NULL)
                    .put("ends_at", notice.endsAt ?: JSONObject.NULL)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(CACHE_KEY, array.toString()).apply()
    }
}
