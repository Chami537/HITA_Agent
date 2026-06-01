package com.limpu.hitax.ui.eas.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.limpu.hitax.data.model.eas.EASToken
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.limpu.hitax.R
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.style.ThemeTools
import com.limpu.hitax.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.json.JSONObject
import java.net.URL

@AndroidEntryPoint
class WebViewLoginActivity : AppCompatActivity() {

    protected val viewModel: WebViewLoginViewModel by viewModels()

    companion object {
        const val EXTRA_SILENT_MODE = "silent_mode"
        const val EXTRA_CAMPUS = "campus"

        // 校园网络URL常量
        private object CampusUrls {
            // 本部校区URL
            private const val BENBU_BASE = "http://i-hit-edu-cn.ivpn.hit.edu.cn:1080"
            private const val JWTS_BASE = "http://jwts-hit-edu-cn.ivpn.hit.edu.cn:1080"

            // 威海校区URL
            private const val WEIHAI_BASE = "https://webvpn.hitwh.edu.cn"
            private const val WEIHAI_EAS_PREFIX = "$WEIHAI_BASE/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667"

            val BENBU_LOGIN = "$BENBU_BASE/portal/home/"
            val BENBU_JWTS = "$JWTS_BASE/loginCAS"
            val BENBU_PROBE_URLS = listOf(
                "$JWTS_BASE/loginCAS",
                "$BENBU_BASE/",
                "$BENBU_BASE/portal/home/"
            )

            val WEIHAI_LOGIN = "$WEIHAI_BASE/"
            val WEIHAI_JWTS = "$WEIHAI_EAS_PREFIX/loginCAS"
            val WEIHAI_PROBE_URLS = listOf(
                WEIHAI_JWTS,
                "$WEIHAI_EAS_PREFIX/kjscx/queryJxlListBySjid",
                "$WEIHAI_EAS_PREFIX/cjcx/queryQmcj",
                "$WEIHAI_BASE/"
            )

            const val EELABINFO_URL = "http://eelabinfo-hit-edu-cn.ivpn.hit.edu.cn:1080"
        }

        private const val COOKIE_RETRY_COUNT = 30
        private const val COOKIE_RETRY_DELAY_MS = 500L
        private const val SILENT_TIMEOUT_MS = 18000L
        private val BENBU_REQUIRED_COOKIES = setOf("JSESSIONID", "HIT")
        private const val WEIHAI_TICKET_COOKIE_PREFIX = "wengine_vpn_ticket"
        private val WEIHAI_EAS_SESSION_COOKIE_HINTS = listOf("JSESSIONID", "HIT", "TWFID")
    }

    private data class CampusWebConfig(
        val campus: EASToken.Campus,
        val loginUrl: String,
        val jwtsUrl: String,
        val cookieProbeUrls: List<String>
    )

    private var finished = false
    private var cookieRetryCount = 0
    private var autoOpeningJwts = false
    private var silentMode = false
    private lateinit var config: CampusWebConfig
    private var navigatingToEelab = false
    private var collectedEasCookies: Map<String, String>? = null
    private var eelabTokenFetching = false
    private var lastPageHadError = false
    private lateinit var webView: WebView
    private var progressVisible by mutableStateOf(false)
    private var progressValue by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val campus = runCatching {
            EASToken.Campus.valueOf(
                intent?.getStringExtra(EXTRA_CAMPUS) ?: EASToken.Campus.BENBU.name
            )
        }.getOrDefault(EASToken.Campus.BENBU)
        config = configFor(campus)
        silentMode = intent?.getBooleanExtra(EXTRA_SILENT_MODE, false) == true

        val nightMode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        setTheme(
            if (silentMode) {
                R.style.WebViewLoginSilentTheme
            } else {
                R.style.Theme_HITA_WebViewLogin
            }
        )
        super.onCreate(savedInstanceState)

        if (silentMode) {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setDimAmount(0f)
        } else {
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
        }

        setContent {
            HitaComposeTheme() {
                WebViewLoginScreen(
                    silentMode = silentMode,
                    progressVisible = progressVisible,
                    progressValue = progressValue,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onWebViewReady = { createdWebView ->
                        webView = createdWebView
                        initViews()
                    }
                )
            }
        }
        LogUtils.d( "onCreate silentMode=$silentMode campus=${config.campus}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initViews() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        setupWebView()
        if (silentMode) {
            webView.postDelayed({
                if (!finished) {
                    LogUtils.w( "silent web login timeout campus=${config.campus}")
                    finishWithCancelledResult()
                }
            }, SILENT_TIMEOUT_MS)
        }
        LogUtils.d( "load login url=${config.loginUrl} campus=${config.campus}")
        webView.loadUrl(config.loginUrl)
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun configFor(campus: EASToken.Campus): CampusWebConfig {
        return when (campus) {
            EASToken.Campus.BENBU -> CampusWebConfig(
                campus = campus,
                loginUrl = CampusUrls.BENBU_LOGIN,
                jwtsUrl = CampusUrls.BENBU_JWTS,
                cookieProbeUrls = CampusUrls.BENBU_PROBE_URLS
            )
            EASToken.Campus.WEIHAI -> CampusWebConfig(
                campus = campus,
                loginUrl = CampusUrls.WEIHAI_LOGIN,
                jwtsUrl = CampusUrls.WEIHAI_JWTS,
                cookieProbeUrls = CampusUrls.WEIHAI_PROBE_URLS
            )
            EASToken.Campus.SHENZHEN -> configFor(EASToken.Campus.BENBU)
        }
    }

    private fun setupWebView() {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        progressVisible = false
                    } else {
                        progressVisible = true
                        progressValue = newProgress
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    if (finished) {
                        return
                    }

                    // Handle eelabinfo navigation for JWT token
                    if (navigatingToEelab) {
                        if (url.contains("eelabinfo") && !url.contains("ids.hit.edu.cn") && !eelabTokenFetching) {
                            eelabTokenFetching = true
                            LogUtils.d("eelabinfo page loaded, fetching JWT token...")
                            webView.postDelayed({ fetchEelabTokenViaHttp() }, 1000)
                        } else if (!url.contains("eelabinfo") && !eelabTokenFetching) {
                            LogUtils.w("navigated away from eelabinfo, finishing without token, url=$url")
                            navigatingToEelab = false
                            finishWithCookies(collectedEasCookies ?: collectCookies())
                        }
                        return
                    }

                    val uri = Uri.parse(url)
                    LogUtils.d("onPageFinished: host=${uri.host} path=${uri.path} autoOpeningJwts=$autoOpeningJwts")

                    when {
                        isPortalHomePage(url) -> {
                            LogUtils.d("portal home detected, auto open jwts campus=${config.campus}")
                            autoOpenJwts(view)
                        }
                        isIvpnRedirectPage(url) -> {
                            autoOpeningJwts = false
                            if (silentMode) {
                                LogUtils.d("ivpn redirect page in silent mode, need user interaction")
                                finishWithCancelledResult()
                            } else {
                                LogUtils.d("ivpn redirect page, waiting for CAS login: path=${uri.path}")
                            }
                        }
                        isSuccessPage(url) -> {
                            autoOpeningJwts = false
                            LogUtils.success("login success page detected campus=${config.campus}")
                            handleSuccessPage()
                        }
                        autoOpeningJwts && uri.host?.contains("jwts") == true -> {
                            autoOpeningJwts = false
                            LogUtils.d("jwts domain (auto-open), starting cookie polling: path=${uri.path}")
                            startCookiePolling()
                        }
                        isJwtsPage(url) -> {
                            LogUtils.d("jwts login page detected, waiting for CAS redirect")
                        }
                        else -> {
                            LogUtils.d("unhandled page: host=${uri.host} path=${uri.path}")
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    LogUtils.w( "onReceivedError url=${request?.url} code=${error?.errorCode} desc=${error?.description} campus=${config.campus}")
                }
            }
        }
    }

    private fun isPortalHomePage(url: String): Boolean {
        val uri = Uri.parse(url)
        val normalizedPath = uri.path?.trimEnd('/') ?: ""
        return when (config.campus) {
            EASToken.Campus.BENBU -> uri.host == "i-hit-edu-cn.ivpn.hit.edu.cn" &&
                (normalizedPath == "/portal/home" || normalizedPath == "/portal")
            EASToken.Campus.WEIHAI -> uri.host == "webvpn.hitwh.edu.cn" && (normalizedPath.isBlank() || normalizedPath == "/portal/home")
            EASToken.Campus.SHENZHEN -> false
        }
    }

    private fun isIvpnRedirectPage(url: String): Boolean {
        if (config.campus != EASToken.Campus.BENBU) return false
        val uri = Uri.parse(url)
        return uri.host == "ivpn.hit.edu.cn"
    }

    private fun isJwtsPage(url: String): Boolean {
        if (config.campus == EASToken.Campus.WEIHAI) {
            return false
        }
        val uri = Uri.parse(url)
        return uri.host?.contains("jwts") == true &&
            (url.lowercase().contains("logincas") || url.lowercase().contains("login"))
    }

    private fun isSuccessPage(url: String): Boolean {
        val uri = Uri.parse(url) ?: return false
        val path = uri.path?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()

        return when (config.campus) {
            EASToken.Campus.BENBU -> {
                val isLoginPage = path.contains("login")
                val isOnJwtsDomain = host.contains("jwts")

                // Only check cookies on JWTS domain — IVPN portal pages can have
                // IVPN session cookies that don't represent an authenticated JWTS session
                if (isOnJwtsDomain && !isLoginPage) {
                    val cookies = collectCookies()
                    val hasRequiredCookies = cookies.containsKey("JSESSIONID") &&
                                             cookies.containsKey("HIT")

                    if (hasRequiredCookies) {
                        return true
                    }
                }

                (host.contains("jwts") || host.contains("hit.edu.cn")) &&
                (path.contains("kbcx") || path.contains("cjcx") || path.contains("kjscx") ||
                 path.contains("xswh") || path.contains("query") || path.contains("index"))
            }
            EASToken.Campus.WEIHAI -> {
                val urlLower = url.lowercase()
                val isLoginCasPage = urlLower.contains("logincas")
                val isFunctionPage = path.contains("kbcx") || path.contains("cjcx") ||
                                   path.contains("kjscx") || path.contains("query") ||
                                   path.contains("index")
                isLoginCasPage || isFunctionPage
            }
            EASToken.Campus.SHENZHEN -> false
        }
    }

    private fun autoOpenJwts(webView: WebView) {
        if (autoOpeningJwts) return
        autoOpeningJwts = true
        LogUtils.d("auto opening JWTS campus=${config.campus}")
        webView.loadUrl(config.jwtsUrl)
    }

    private fun startCookiePolling() {
        cookieRetryCount = 0
        webView.postDelayed({
            checkCookiesAndFinish()
        }, COOKIE_RETRY_DELAY_MS)
    }

    private fun checkCookiesAndFinish() {
        if (finished) return

        val cookies = collectCookies()
        val currentUrl = webView.url ?: ""
        val hasVpnTicket = hasWeihaiVpnTicket(cookies)
        val hasJsessionid = cookies.containsKey("JSESSIONID")

        if (cookieRetryCount == 0 || cookieRetryCount % 10 == 0) {
            LogUtils.d("checkCookies: retry=$cookieRetryCount keys=${cookies.keys.sorted()} host=${Uri.parse(currentUrl).host}")
        }

        if (config.campus == EASToken.Campus.WEIHAI && hasVpnTicket && hasJsessionid) {
            fetchVpnEasCookies { vpnCookies ->
                val mergedCookies = LinkedHashMap(cookies)
                vpnCookies.forEach { (key, value) -> mergedCookies.putIfAbsent(key, value) }
                finishWithCookies(mergedCookies)
            }
            return
        }

        if (config.campus == EASToken.Campus.BENBU
            && hasRequiredCookies(cookies, currentUrl)) {
            handleSuccessPage()
            return
        }

        if (config.campus == EASToken.Campus.SHENZHEN
            && hasRequiredCookies(cookies, currentUrl)) {
            finishWithCookies(cookies)
            return
        }

        if (cookieRetryCount >= COOKIE_RETRY_COUNT) {
            LogUtils.w("cookie polling timeout campus=${config.campus}")
            return
        }

        cookieRetryCount++
        webView.postDelayed({
            checkCookiesAndFinish()
        }, COOKIE_RETRY_DELAY_MS)
    }

    private fun handleSuccessPage() {
        if (finished) return

        val cookies = collectCookies()

        if (config.campus == EASToken.Campus.WEIHAI && hasWeihaiVpnTicket(cookies)) {
            fetchVpnEasCookies { vpnCookies ->
                val mergedCookies = LinkedHashMap(cookies)
                vpnCookies.forEach { (key, value) -> mergedCookies.putIfAbsent(key, value) }
                finishWithCookies(mergedCookies)
            }
        } else if (config.campus == EASToken.Campus.BENBU) {
            navigatingToEelab = true
            eelabTokenFetching = false
            collectedEasCookies = cookies
            webView.postDelayed({
                if (navigatingToEelab && !finished) {
                    LogUtils.w("eelabinfo timeout, finishing without token")
                    navigatingToEelab = false
                    finishWithCookies(cookies)
                }
            }, 10000)
            webView.loadUrl(CampusUrls.EELABINFO_URL + "/api/cas/loginSuccess")
        } else {
            finishWithCookies(cookies)
        }
    }

    private fun fetchEelabTokenViaHttp() {
        if (finished || !navigatingToEelab) return

        Thread {
            try {
                val cookieManager = CookieManager.getInstance()
                val eelabCookies = cookieManager.getCookie(CampusUrls.EELABINFO_URL)

                if (eelabCookies.isNullOrBlank() || !eelabCookies.contains("JSESSIONID")) {
                    LogUtils.w("fetchEelabToken: JSESSIONID not found, student likely has no eelab access")
                    webView.post {
                        if (!finished && navigatingToEelab) {
                            navigatingToEelab = false
                            finishWithCookies(collectedEasCookies ?: collectCookies())
                        }
                    }
                    return@Thread
                }

                val allCookies = mutableMapOf<String, String>()
                eelabCookies.split(";").forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.contains("=")) {
                        val idx = trimmed.indexOf('=')
                        allCookies[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
                    }
                }

                val url = CampusUrls.EELABINFO_URL + "/api/cas/login?sf_request_type=ajax"
                val response = org.jsoup.Jsoup.connect(url)
                    .cookies(allCookies)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; sdk_gphone64_arm64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.135 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Origin", CampusUrls.EELABINFO_URL)
                    .header("Referer", CampusUrls.EELABINFO_URL + "/login.html?t=suc")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                    .timeout(5000)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .method(org.jsoup.Connection.Method.POST)
                    .execute()

                if (response.statusCode() == 200) {
                    try {
                        val json = JSONObject(response.body())
                        val code = json.optInt("code", -1)
                        if (code == 0) {
                            val data = json.optJSONObject("data")
                            val token = data?.optString("token", "") ?: ""
                            if (token.length >= 50) {
                                LogUtils.success("fetchEelabToken: got JWT token, length=${token.length}")
                                webView.post {
                                    if (!finished && navigatingToEelab) {
                                        navigatingToEelab = false
                                        finishWithCookies(collectedEasCookies ?: collectCookies(), token)
                                    }
                                }
                                return@Thread
                            }
                        }
                        LogUtils.w("fetchEelabToken: unexpected response code=$code")
                    } catch (e: Exception) {
                        LogUtils.e("fetchEelabToken: parse response failed", e)
                    }
                } else {
                    LogUtils.w("fetchEelabToken: HTTP ${response.statusCode()}")
                }

                webView.post {
                    if (!finished && navigatingToEelab) {
                        navigatingToEelab = false
                        finishWithCookies(collectedEasCookies ?: collectCookies())
                    }
                }
            } catch (e: Exception) {
                LogUtils.e("fetchEelabToken: HTTP request failed", e)
                webView.post {
                    if (!finished && navigatingToEelab) {
                        navigatingToEelab = false
                        finishWithCookies(collectedEasCookies ?: collectCookies())
                    }
                }
            }
        }.start()
    }

    private fun hasRequiredCookies(cookies: Map<String, String>, currentUrl: String): Boolean {
        return when (config.campus) {
            EASToken.Campus.BENBU, EASToken.Campus.SHENZHEN -> {
                val hasJsession = cookies.containsKey("JSESSIONID") || hasUrlJsession(currentUrl)
                val hasHit = cookies.containsKey("HIT")
                hasJsession && hasHit
            }
            EASToken.Campus.WEIHAI -> {
                // 威海校区：需要 VPN ticket + JSESSIONID
                val hasVpnTicket = hasWeihaiVpnTicket(cookies)
                val hasJsession = cookies.containsKey("JSESSIONID")
                hasVpnTicket && hasJsession
            }
        }
    }

    private fun hasWeihaiVpnTicket(cookies: Map<String, String>): Boolean {
        return cookies.keys.any { key ->
            key.startsWith(WEIHAI_TICKET_COOKIE_PREFIX, ignoreCase = true) ||
                key.contains(WEIHAI_TICKET_COOKIE_PREFIX, ignoreCase = true)
        }
    }

    private fun hasUrlJsession(url: String): Boolean {
        return url.contains(";jsessionid=", ignoreCase = true) ||
            url.contains("jsessionid=", ignoreCase = true)
    }

    private fun collectCookies(): LinkedHashMap<String, String> {
        val cookieManager = CookieManager.getInstance()
        val cookies = LinkedHashMap<String, String>()

        config.cookieProbeUrls.forEach { url ->
            val parsed = parseCookies(cookieManager.getCookie(url))
            parsed.forEach { (key, value) ->
                cookies.putIfAbsent(key, value)
            }
        }

        val currentUrl = webView.url
        if (!currentUrl.isNullOrBlank() && currentUrl.startsWith("http")) {
            val parsed = parseCookies(cookieManager.getCookie(currentUrl))
            parsed.forEach { (key, value) ->
                cookies.putIfAbsent(key, value)
            }

            if (!cookies.containsKey("JSESSIONID")) {
                val jsessionid = extractJsessionidFromUrl(currentUrl)
                if (jsessionid != null) {
                    cookies["JSESSIONID"] = jsessionid
                }
            }
        }

        return cookies
    }

    private fun fetchVpnEasCookies(callback: (Map<String, String>) -> Unit) {
        // 在主线程上捕获 WebView 数据
        val currentUrl = webView.url ?: ""
        val cookieHeader = buildCookieHeader(currentUrl)

        Thread {
            val result = mutableMapOf<String, String>()
            try {
                // 威海校区的 EAS 系统域名和登录页面路径
                val easHost = "jwts.hitwh.edu.cn"
                val easPath = "/loginCAS"  // 使用登录页面路径

                // 调用 VPN cookie API
                val cookieApiUrl = "https://webvpn.hitwh.edu.cn/wengine-vpn/cookie?method=get&host=$easHost&scheme=http&path=$easPath&vpn_timestamp=${System.currentTimeMillis()}"

                // 使用同步 HTTP 请求
                val connection = URL(cookieApiUrl).openConnection() as javax.net.ssl.HttpsURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Cookie", cookieHeader)
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.setRequestProperty("Accept", "*/*")

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    LogUtils.d("VPN cookie API response: $response")

                    // 解析返回的 cookies（格式：name=value; JSESSIONID=xxx; HIT=yyy）
                    val parts = response.split(";")
                    for (part in parts) {
                        val trimmed = part.trim()
                        if (trimmed.contains("=")) {
                            val idx = trimmed.indexOf('=')
                            val key = trimmed.substring(0, idx).trim()
                            val value = trimmed.substring(idx + 1).trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                // 只关心 EAS 相关的 cookies
                                if (key == "JSESSIONID" || key == "HIT" || key == "TWFID") {
                                    result[key] = value
                                    LogUtils.d("parsed VPN cookie: $key=$value")
                                }
                            }
                        }
                    }
                } else {
                    LogUtils.w("VPN cookie API failed with code: $responseCode")
                }
            } catch (e: Exception) {
                LogUtils.e("fetchVpnEasCookies: failed", e)
            }

            webView.post {
                callback(result)
            }
        }.start()
    }

    private fun buildCookieHeader(currentUrl: String): String {
        val cookieManager = CookieManager.getInstance()

        // 从当前 URL 和 VPN 主域获取 cookies
        val cookies = mutableSetOf<String>()

        // 添加当前页面的 cookies
        if (currentUrl.isNotEmpty()) {
            val currentCookies = cookieManager.getCookie(currentUrl)
            if (!currentCookies.isNullOrBlank()) {
                cookies.add(currentCookies)
            }
        }

        // 添加 VPN 主域的 cookies
        val vpnCookies = cookieManager.getCookie("https://webvpn.hitwh.edu.cn/")
        if (!vpnCookies.isNullOrBlank()) {
            cookies.add(vpnCookies)
        }

        return cookies.joinToString("; ")
    }

    private fun extractJsessionidFromUrl(url: String): String? {
        // 匹配 URL 中的 ;jsessionid=XXX 或 jsessionid=XXX 参数
        val patterns = listOf(
            ";jsessionid=([^;&?]*)",
            "[?&]jsessionid=([^;&]*)"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    private fun fingerprintSummary(cookies: Map<String, String>): String {
        return when (config.campus) {
            EASToken.Campus.BENBU, EASToken.Campus.SHENZHEN -> {
                BENBU_REQUIRED_COOKIES.sorted().joinToString(prefix = "[", postfix = "]") { key ->
                    val value = cookies[key]
                    "$key=${value?.take(8) ?: "-"}"
                }
            }
            EASToken.Campus.WEIHAI -> {
                val ticketKey = cookies.keys.firstOrNull { it.startsWith(WEIHAI_TICKET_COOKIE_PREFIX) }
                val ticketValue = ticketKey?.let { cookies[it] }
                val easSummary = WEIHAI_EAS_SESSION_COOKIE_HINTS.joinToString(prefix = "[", postfix = "]") { key ->
                    "$key=${cookies[key]?.take(8) ?: "-"}"
                }
                "[$WEIHAI_TICKET_COOKIE_PREFIX=${ticketValue?.take(8) ?: "-"}]$easSummary"
            }
        }
    }

    private fun finishWithCookies(cookies: Map<String, String>, eelabToken: String? = null) {
        if (finished) return
        finished = true

        val cookiesJson = JSONObject(cookies as Map<*, *>).toString()
        val intent = Intent().apply {
            putExtra("cookies", cookiesJson)
            if (!eelabToken.isNullOrBlank()) {
                putExtra("electronic_exp_token", eelabToken)
            }
        }
        LogUtils.success("login complete campus=${config.campus} cookies=${cookies.size} eelabToken=${eelabToken != null}")
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun finishWithCancelledResult() {
        if (finished) return
        finished = true
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun parseCookies(cookieString: String?): Map<String, String> {
        if (cookieString.isNullOrBlank()) return emptyMap()
        return cookieString.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isBlank() || !trimmed.contains("=")) return@mapNotNull null
                val idx = trimmed.indexOf('=')
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }
}

@HiltViewModel
class WebViewLoginViewModel @Inject constructor() : androidx.lifecycle.ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewLoginScreen(
    silentMode: Boolean,
    progressVisible: Boolean,
    progressValue: Int,
    onBack: () -> Unit,
    onWebViewReady: (WebView) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (silentMode) ComposeColor.Transparent else MaterialTheme.colorScheme.background)
    ) {
        if (!silentMode) {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.webview_login_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.rotate(180f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        if (progressVisible && !silentMode) {
            LinearProgressIndicator(
                progress = { (progressValue.coerceIn(0, 100)) / 100f },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).also(onWebViewReady)
            }
        )
    }
}
