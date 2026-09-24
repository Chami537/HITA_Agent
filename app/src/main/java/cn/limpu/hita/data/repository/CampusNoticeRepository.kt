package cn.limpu.hita.data.repository

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cn.limpu.hita.BuildConfig
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.notice.CampusNotice
import cn.limpu.hita.data.source.preference.CampusNoticePreferenceSource
import cn.limpu.hita.data.source.web.notice.CampusNoticeParser
import cn.limpu.hita.utils.LogUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class CampusNoticeSyncError {
    NEED_LOGIN,
    NEED_CAMPUS_NET,
    FAILED,
}

@Singleton
class CampusNoticeRepository @Inject constructor(application: Application) {
    private val dao = AppDatabase.getDatabase(application).campusNoticeDao()
    private val prefs = CampusNoticePreferenceSource(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val _refreshing = MutableLiveData(false)
    val refreshing: LiveData<Boolean> = _refreshing

    private val _syncError = MutableLiveData<CampusNoticeSyncError?>(null)
    val syncError: LiveData<CampusNoticeSyncError?> = _syncError

    fun observeNotices(): LiveData<List<CampusNotice>> = dao.observeLatest()

    fun syncOnPageOpen() {
        refresh(force = false)
    }

    fun ingestWebExtract(json: String) {
        executor.execute {
            val notices = noticesFromExtract(json)
            if (notices.isEmpty()) return@execute
            dao.replaceAll(notices)
            prefs.markFetchedToday()
            _syncError.postValue(null)
        }
    }

    fun refresh(force: Boolean) {
        executor.execute {
            _refreshing.postValue(true)
            var cached = emptyList<CampusNotice>()
            try {
                cached = dao.getLatest()
                val skipNetwork = !force &&
                    !cacheIsJunk(cached) &&
                    prefs.isFetchedToday() &&
                    prefs.parserVersion >= CampusNoticeParser.PARSER_VERSION
                if (skipNetwork) {
                    _syncError.postValue(null)
                    return@execute
                }
                val notices = fetchLatestNotices()
                if (notices.isNotEmpty()) {
                    dao.replaceAll(notices)
                    prefs.markFetchedToday()
                    _syncError.postValue(null)
                    return@execute
                }
                if (cacheIsJunk(cached)) {
                    dao.replaceAll(emptyList())
                }
                if (dao.getLatest().isNotEmpty()) {
                    _syncError.postValue(null)
                    return@execute
                }
                _syncError.postValue(
                    if (hasPortalCookie()) CampusNoticeSyncError.FAILED
                    else CampusNoticeSyncError.NEED_LOGIN
                )
            } catch (e: CampusNoticeLoginRequired) {
                if (cacheIsJunk(cached)) dao.replaceAll(emptyList())
                _syncError.postValue(CampusNoticeSyncError.NEED_LOGIN)
            } catch (e: Exception) {
                LogUtils.e("campus notice sync failed: ${e.message}", e)
                if (cacheIsJunk(cached)) dao.replaceAll(emptyList())
                _syncError.postValue(classify(e))
            } finally {
                _refreshing.postValue(false)
            }
        }
    }

    private fun cacheIsJunk(cached: List<CampusNotice>): Boolean {
        if (prefs.parserVersion < CampusNoticeParser.PARSER_VERSION) return true
        if (cached.isEmpty()) return true
        return cached.none { CampusNoticeParser.isPersistedNotice(it) }
    }

    private fun fetchLatestNotices(): List<CampusNotice> {
        val byId = LinkedHashMap<String, CampusNotice>()
        for (page in 1..CampusNoticeParser.PAGES_FOR_CAP) {
            val pageNotices = downloadAndParse(CampusNoticeParser.listPageUrl(page))
            if (pageNotices.isEmpty()) break
            for (notice in pageNotices) {
                if (notice.id !in byId) byId[notice.id] = notice
            }
            if (byId.size >= CampusNoticeParser.MAX_NOTICES) break
        }
        return byId.values
            .sortedWith(compareByDescending<CampusNotice> { it.pubDateMillis }.thenBy { it.title })
            .take(CampusNoticeParser.MAX_NOTICES)
    }

    private fun classify(error: Exception): CampusNoticeSyncError {
        return when (error) {
            is UnknownHostException, is SocketTimeoutException -> CampusNoticeSyncError.NEED_CAMPUS_NET
            is IOException -> CampusNoticeSyncError.FAILED
            else -> CampusNoticeSyncError.FAILED
        }
    }

    private fun hasPortalCookie(): Boolean {
        val cookie = portalCookieHeader()
        return cookie.contains("JSESSIONID", ignoreCase = true) ||
            cookie.contains("iPlanetDirectoryPro", ignoreCase = true)
    }

    private fun portalCookieHeader(): String {
        return runCatching {
            val manager = CookieManager.getInstance()
            listOf(
                "https://info.hitsz.edu.cn/",
                "http://info.hitsz.edu.cn/",
                "https://ids.hit.edu.cn/",
            ).mapNotNull { url ->
                runCatching { manager.getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }
            }.joinToString("; ")
        }.getOrDefault("")
    }

    private fun downloadAndParse(url: String): List<CampusNotice> {
        val html = download(url)
        return CampusNoticeParser.parse(html, url)
    }

    private fun download(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 HITA/${BuildConfig.VERSION_NAME}",
            )
            .header("Accept", "text/html,application/xhtml+xml")
            .apply {
                val cookie = portalCookieHeader()
                if (cookie.isNotBlank() && url.contains("info.hitsz.edu.cn")) {
                    header("Cookie", cookie)
                }
            }
            .build()
        httpClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            if (CampusNoticeParser.isCasLoginUrl(finalUrl)) {
                throw CampusNoticeLoginRequired()
            }
            if (response.code in 401..403) {
                throw CampusNoticeLoginRequired()
            }
            if (response.code !in 200..299) {
                throw IOException("HTTP ${response.code}")
            }
            return response.body?.string() ?: throw IOException("empty body")
        }
    }

    private fun noticesFromExtract(json: String): List<CampusNotice> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val notices = ArrayList<CampusNotice>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
            val rawUrl = obj.optString("url").substringBefore('#').trim()
            if (title.length < 4 || !CampusNoticeParser.isNoticeUrl(rawUrl)) continue
            val url = CampusNoticeParser.canonicalContentUrl(rawUrl)
            val near = obj.optString("near")
            val millis = CampusNoticeParser.parseDateMillis("$title $near")
            val id = CampusNoticeParser.newsIdFromUrl(url) ?: continue
            notices += CampusNotice(id = id, title = title, url = url, pubDateMillis = millis)
        }
        return notices.distinctBy { it.id }.take(CampusNoticeParser.MAX_NOTICES)
    }

    private class CampusNoticeLoginRequired : Exception("login required")
}
