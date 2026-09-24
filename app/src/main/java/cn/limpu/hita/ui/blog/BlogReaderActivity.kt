package cn.limpu.hita.ui.blog

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import cn.limpu.hita.R
import cn.limpu.hita.data.model.blog.BlogArticle
import cn.limpu.hita.data.repository.BlogRepository
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import com.limpu.style.ThemeTools
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BlogReaderActivity : AppCompatActivity() {

    @Inject lateinit var blogRepository: BlogRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)

        val guid = intent.getStringExtra(EXTRA_GUID).orEmpty()
        if (guid.isBlank()) {
            finish()
            return
        }

        setContent {
            HitaComposeTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        blogRepository.loadArticle(guid) { article ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (article == null) {
                    finish()
                    return@runOnUiThread
                }
                blogRepository.markArticleRead(article.guid)
                setContent {
                    HitaComposeTheme {
                        BlogReaderScreen(
                            article = article,
                            onBack = { onBackPressedDispatcher.onBackPressed() },
                            onOpenInternal = { next ->
                                startActivity(
                                    Intent(this, BlogReaderActivity::class.java)
                                        .putExtra(EXTRA_GUID, next.guid)
                                )
                            },
                            onOpenExternal = { url ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            findInternal = { url -> blogRepository.findByLink(url) },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_GUID = "guid"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlogReaderScreen(
    article: BlogArticle,
    onBack: () -> Unit,
    onOpenInternal: (BlogArticle) -> Unit,
    onOpenExternal: (String) -> Unit,
    findInternal: (String) -> BlogArticle?,
) {
    val isDark = HitaTheme.isDark
    val background = MaterialTheme.colorScheme.background.toArgb()
    val onBackground = MaterialTheme.colorScheme.onBackground.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val tableBorder = MaterialTheme.colorScheme.outline.toArgb()
    val html = remember(article.guid, isDark, background, onBackground, link, muted, codeBackground, tableBorder) {
        BlogHtmlRenderer.wrap(
            article = article,
            isDark = isDark,
            background = background,
            onBackground = onBackground,
            link = link,
            muted = muted,
            codeBackground = codeBackground,
            tableBorder = tableBorder,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = article.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = rememberLxgwFontFamily(),
                    maxLines = 1,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.rotate(180f),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            BlogContentWebView(
                html = html,
                findInternal = findInternal,
                onOpenInternal = onOpenInternal,
                onOpenExternal = onOpenExternal,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BlogContentWebView(
    html: String,
    findInternal: (String) -> BlogArticle?,
    onOpenInternal: (BlogArticle) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    allowFileAccess = true
                    allowContentAccess = true
                    defaultTextEncodingName = "utf-8"
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        return handleUrl(url)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return handleUrl(url.orEmpty())
                    }

                    private fun handleUrl(url: String): Boolean {
                        if (url.isBlank() || url.startsWith("file://")) return false
                        val internal = findInternal(url)
                        if (internal != null) {
                            onOpenInternal(internal)
                            return true
                        }
                        onOpenExternal(url)
                        return true
                    }
                }
                tag = html
                loadDataWithBaseURL(
                    "file:///android_asset/blog/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        update = { webView ->
            val tagged = webView.tag as? String
            if (tagged != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(
                    "file:///android_asset/blog/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

@Composable
private fun rememberLxgwFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        FontFamily(Typeface.createFromAsset(context.assets, "blog/lxgw-wenkai.ttf"))
    }
}
