package cn.limpu.hita.ui.notice

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cn.limpu.hita.data.repository.CampusNoticeRepository
import cn.limpu.hita.data.source.web.notice.CampusNoticeParser
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONTokener
import javax.inject.Inject

@AndroidEntryPoint
class InfoPortalLoginActivity : AppCompatActivity() {
    @Inject lateinit var noticeRepository: CampusNoticeRepository

    private lateinit var webView: WebView
    private var finished = false
    private var extractScheduled = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(Color.WHITE)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (isPortalHome(url)) {
                    scheduleExtract()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        })
        webView.loadUrl(CampusNoticeParser.LIST_URL)
    }

    private fun isPortalHome(url: String): Boolean {
        if (CampusNoticeParser.isCasLoginUrl(url)) return false
        return url.contains("info.hitsz.edu.cn", ignoreCase = true)
    }

    private fun scheduleExtract() {
        if (extractScheduled || finished) return
        extractScheduled = true
        webView.postDelayed({ finishSuccess() }, 800)
    }

    private fun extractNotices() {
        finishSuccess()
    }

    private fun decodeJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching {
            JSONTokener(raw).nextValue()?.toString().orEmpty()
        }.getOrDefault(raw.trim().removeSurrounding("\""))
    }

    private fun finishSuccess() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        setResult(RESULT_OK, Intent())
        finish()
    }

    companion object {
        private const val EXTRACT_JS = """
            (function(){
              function textOf(el){
                return ((el.getAttribute('title')||'') || (el.textContent||'')).replace(/\s+/g,' ').trim();
              }
              var items = [];
              var seen = {};
              var anchors = document.querySelectorAll('a');
              for (var i = 0; i < anchors.length; i++) {
                var a = anchors[i];
                var href = a.href || '';
                var title = textOf(a);
                if (title.length < 6 || title.length > 120) continue;
                if (/登录|首页|导航|邮箱|下载|English/.test(title)) continue;
                if (!/info\.hitsz|\/info\/|\.htm/.test(href)) continue;
                if (seen[href]) continue;
                seen[href] = true;
                var near = a.parentElement ? (a.parentElement.innerText||'').slice(0,80) : '';
                items.push({title:title,url:href,near:near});
                if (items.length >= 40) break;
              }
              return JSON.stringify(items);
            })()
        """
    }
}
