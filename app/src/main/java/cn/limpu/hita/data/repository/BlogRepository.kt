package cn.limpu.hita.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cn.limpu.hita.BuildConfig
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.blog.BlogArticle
import cn.limpu.hita.data.source.preference.BlogPreferenceSource
import cn.limpu.hita.data.source.web.blog.BlogFeedParser
import cn.limpu.hita.utils.LogUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlogRepository @Inject constructor(application: Application) {
    private val appContext = application.applicationContext
    private val dao = AppDatabase.getDatabase(application).blogArticleDao()
    private val prefs = BlogPreferenceSource(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _tabUnseenLiveData = MutableLiveData(false)
    val tabUnseenLiveData: LiveData<Boolean> = _tabUnseenLiveData

    private val _unreadGuidsLiveData = MutableLiveData<Set<String>>(emptySet())
    val unreadGuidsLiveData: LiveData<Set<String>> = _unreadGuidsLiveData

    private val _refreshingLiveData = MutableLiveData(false)
    val refreshingLiveData: LiveData<Boolean> = _refreshingLiveData

    private val _syncErrorLiveData = MutableLiveData<String?>(null)
    val syncErrorLiveData: LiveData<String?> = _syncErrorLiveData

    @Volatile
    private var articleCache: List<BlogArticle> = emptyList()

    init {
        executor.execute {
            articleCache = dao.getAll()
            publishUnseenLocked()
        }
    }

    fun observeArticles(): LiveData<List<BlogArticle>> = dao.observeAll()

    fun getArticle(guid: String): BlogArticle? {
        articleCache.firstOrNull { it.guid == guid }?.let { return it }
        return null
    }

    fun loadArticle(guid: String, callback: (BlogArticle?) -> Unit) {
        executor.execute {
            val article = articleCache.firstOrNull { it.guid == guid } ?: dao.getByGuid(guid)
            callback(article)
        }
    }

    fun findByLink(link: String): BlogArticle? {
        val trimmed = link.trim()
        if (trimmed.isBlank()) return null
        val normalized = normalizeBlogLink(trimmed)
        val path = BlogFeedParser.pathFromLink(normalized ?: trimmed)
        return articleCache.firstOrNull { article ->
            article.guid == trimmed ||
                article.link == trimmed ||
                (normalized != null && (article.guid == normalized || article.link == normalized)) ||
                (path.isNotBlank() && article.path == path)
        }
    }

    fun syncOnAppOpen() {
        refresh(force = false)
    }

    fun refresh(force: Boolean) {
        executor.execute {
            _refreshingLiveData.postValue(true)
            try {
                val xml = downloadFeed()
                val feed = BlogFeedParser.parse(xml)
                if (feed.articles.isEmpty()) {
                    throw IllegalStateException("empty feed")
                }
                val unchanged = !force &&
                    prefs.hasBaseline &&
                    prefs.lastBuildDate.isNotBlank() &&
                    prefs.lastBuildDate == feed.lastBuildDate &&
                    dao.getAll().isNotEmpty()
                if (!unchanged) {
                    dao.replaceAll(feed.articles)
                    prefs.lastBuildDate = feed.lastBuildDate
                    val guids = feed.articles.map { it.guid }.toSet()
                    if (!prefs.hasBaseline) {
                        prefs.replaceReadGuids(guids)
                        prefs.replaceTabClearedGuids(guids)
                        prefs.hasBaseline = true
                    }
                }
                articleCache = dao.getAll()
                _syncErrorLiveData.postValue(null)
                publishUnseenLocked()
            } catch (e: Exception) {
                LogUtils.e("blog sync failed: ${e.message}", e)
                _syncErrorLiveData.postValue(e.message ?: "sync failed")
                publishUnseenLocked()
            } finally {
                _refreshingLiveData.postValue(false)
            }
        }
    }

    fun markTabOpened() {
        executor.execute {
            val guids = dao.getAll().map { it.guid }.toSet()
            prefs.replaceTabClearedGuids(guids)
            publishUnseenLocked()
        }
    }

    fun markArticleRead(guid: String) {
        if (guid.isBlank()) return
        executor.execute {
            prefs.addReadGuid(guid)
            publishUnseenLocked()
        }
    }

    private fun publishUnseenLocked() {
        val current = dao.getAll().map { it.guid }.toSet()
        val read = prefs.readGuids()
        val tabCleared = prefs.tabClearedGuids()
        _unreadGuidsLiveData.postValue(current - read)
        _tabUnseenLiveData.postValue(current.any { it !in tabCleared })
    }

    private fun downloadFeed(): String {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return fetchFeedOnce()
            } catch (error: Exception) {
                lastError = error
                LogUtils.e("blog download attempt ${attempt + 1} failed: ${error.message}", error)
                if (attempt < 2) {
                    Thread.sleep(1500L * (attempt + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("rss download failed")
    }

    private fun fetchFeedOnce(): String {
        val request = Request.Builder()
            .url(BlogFeedParser.FEED_URL)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 HITA/${BuildConfig.VERSION_NAME}",
            )
            .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("empty body")
            if (!body.contains("<item")) {
                throw IllegalStateException("rss has no items")
            }
            return body
        }
    }
    companion object {
        fun normalizeBlogLink(raw: String): String? {
            val trimmed = raw.trim().substringBefore('#')
            if (trimmed.isBlank()) return null
            val relativePath = when {
                trimmed.startsWith("/blog/") -> trimmed
                trimmed.startsWith("blog/") -> "/$trimmed"
                else -> null
            }
            if (relativePath != null) {
                return "${BlogFeedParser.SITE_ORIGIN}${relativePath.trimEnd('/')}"
            }
            val uri = try {
                java.net.URI(trimmed)
            } catch (_: Exception) {
                return null
            }
            val host = uri.host?.removePrefix("www.") ?: return null
            if (host != "hoa.moe") return null
            val path = uri.path.orEmpty().trimEnd('/')
            if (!path.startsWith("/blog/")) return null
            return "${BlogFeedParser.SITE_ORIGIN}$path"
        }
    }
}
