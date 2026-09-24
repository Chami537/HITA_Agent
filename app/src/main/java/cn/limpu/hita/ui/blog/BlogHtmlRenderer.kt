package cn.limpu.hita.ui.blog

import android.graphics.Color
import cn.limpu.hita.data.model.blog.BlogArticle

object BlogHtmlRenderer {
    fun wrap(
        article: BlogArticle,
        isDark: Boolean,
        background: Int,
        onBackground: Int,
        link: Int,
        muted: Int,
        codeBackground: Int,
        tableBorder: Int,
    ): String {
        val themeClass = if (isDark) "dark" else "light"
        val css = """
            @font-face {
              font-family: 'LXGW WenKai';
              src: url('lxgw-wenkai.ttf') format('truetype');
              font-weight: 400;
              font-style: normal;
            }
            html, body {
              margin: 0;
              padding: 0;
              background: ${cssColor(background)};
              color: ${cssColor(onBackground)};
              font-family: 'LXGW WenKai', serif;
              font-size: 17px;
              line-height: 1.8;
              -webkit-text-size-adjust: 100%;
            }
            body { padding: 16px 18px 48px; }
            a { color: ${cssColor(link)}; text-decoration: none; }
            a:hover { text-decoration: underline; }
            h1, h2, h3, h4, h5, h6 {
              font-family: 'LXGW WenKai', serif;
              font-weight: 700;
              line-height: 1.35;
              margin: 1.4em 0 0.6em;
            }
            h1 { font-size: 1.55em; margin-top: 0; }
            p { margin: 0.75em 0; }
            img, svg { max-width: 100%; height: auto; border-radius: 8px; }
            blockquote {
              margin: 1em 0;
              padding: 0.2em 0.9em;
              border-left: 3px solid ${cssColor(link)};
              color: ${cssColor(muted)};
              background: ${cssColor(codeBackground)};
            }
            table {
              border-collapse: collapse;
              width: 100%;
              margin: 1em 0;
              font-size: 0.92em;
            }
            th, td {
              border: 1px solid ${cssColor(tableBorder)};
              padding: 8px 10px;
              vertical-align: top;
            }
            th { background: ${cssColor(codeBackground)}; }
            code {
              font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              font-size: 0.88em;
              background: ${cssColor(codeBackground)};
              padding: 0.1em 0.35em;
              border-radius: 4px;
            }
            pre {
              overflow-x: auto;
              padding: 12px 14px;
              border-radius: 10px;
              background: ${cssColor(codeBackground)};
              font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              font-size: 0.84em;
              line-height: 1.55;
            }
            pre code { background: transparent; padding: 0; }
            pre.shiki { background-color: var(--shiki-light-bg, ${cssColor(codeBackground)}); }
            .shiki span { color: var(--shiki-light); }
            .dark pre.shiki { background-color: var(--shiki-dark-bg, ${cssColor(codeBackground)}) !important; }
            .dark .shiki span { color: var(--shiki-dark) !important; }
            ul, ol { padding-left: 1.4em; }
            hr { border: 0; border-top: 1px solid ${cssColor(tableBorder)}; margin: 1.6em 0; }
            .article-title {
              font-size: 1.6em;
              font-weight: 700;
              line-height: 1.3;
              margin: 0 0 0.4em;
            }
            .article-meta {
              color: ${cssColor(muted)};
              font-size: 0.88em;
              margin-bottom: 1.2em;
            }
        """.trimIndent()
        val title = escapeHtml(article.title)
        val body = absolutizeHoaUrls(article.htmlContent)
        return """
            <!DOCTYPE html>
            <html class="$themeClass">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
              <link rel="stylesheet" href="katex/katex.min.css"/>
              <style>$css</style>
            </head>
            <body class="$themeClass">
              <h1 class="article-title">$title</h1>
              $body
            </body>
            </html>
        """.trimIndent()
    }

    fun cssColor(color: Int): String {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)
        return if (a >= 255) {
            String.format("#%02X%02X%02X", r, g, b)
        } else {
            String.format("rgba(%d,%d,%d,%.3f)", r, g, b, a / 255f)
        }
    }

    internal fun absolutizeHoaUrls(html: String): String {
        return html
            .replace(Regex("""(?i)(\b(?:src|href)\s*=\s*["'])//"""), "$1https://")
            .replace(Regex("""(?i)(\b(?:src|href)\s*=\s*["'])/"""), "$1https://hoa.moe/")
    }

    private fun escapeHtml(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            when (ch) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }
}
