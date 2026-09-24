package cn.limpu.hita.data.source.web.blog

import cn.limpu.hita.data.model.blog.BlogArticle
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

data class BlogFeedProbe(
    val lastBuildDate: String,
    val firstGuid: String,
)

data class BlogFeed(
    val lastBuildDate: String,
    val articles: List<BlogArticle>,
)

object BlogFeedParser {
    const val FEED_URL = "https://hoa.moe/blog/rss.xml"
    const val SITE_ORIGIN = "https://hoa.moe"
    const val BLOG_PATH_PREFIX = "/blog/"

    private const val CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"

    private val rfc1123: SimpleDateFormat
        get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

    fun probe(xml: String): BlogFeedProbe {
        val feed = parse(xml)
        return BlogFeedProbe(
            lastBuildDate = feed.lastBuildDate,
            firstGuid = feed.articles.firstOrNull()?.guid.orEmpty(),
        )
    }

    fun parse(xml: String): BlogFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = true
            isCoalescing = true
            isValidating = false
            isExpandEntityReferences = false
            runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val channel = document.getElementsByTagName("channel").item(0) as? Element
            ?: return BlogFeed(lastBuildDate = "", articles = emptyList())
        val lastBuildDate = childText(channel, "lastBuildDate")
        val articles = mutableListOf<BlogArticle>()
        val children = channel.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (element.localName != "item" && element.tagName != "item") continue
            parseItem(element)?.let { articles.add(it) }
        }
        return BlogFeed(lastBuildDate = lastBuildDate, articles = articles)
    }

    private fun parseItem(item: Element): BlogArticle? {
        val guid = childText(item, "guid").ifBlank { childText(item, "link") }
        val title = childText(item, "title")
        val link = childText(item, "link")
        if (guid.isBlank() || title.isBlank() || link.isBlank()) return null
        val description = childText(item, "description")
        val html = namespacedChildText(item, CONTENT_NS, "encoded").ifBlank { description }
        val pubDateMillis = parseRfc1123(childText(item, "pubDate"))
        return BlogArticle(
            guid = guid.trim(),
            title = title.trim(),
            link = link.trim(),
            pubDateMillis = pubDateMillis,
            description = description.trim(),
            htmlContent = html,
            path = pathFromLink(link),
        )
    }

    fun pathFromLink(link: String): String {
        val trimmed = link.trim()
        val path = try {
            java.net.URI(trimmed).path.orEmpty()
        } catch (_: Exception) {
            trimmed.substringAfter(SITE_ORIGIN).substringBefore('?')
        }
        return path.removePrefix("/").removePrefix("blog/").trim('/')
    }

    fun parseRfc1123(raw: String): Long {
        if (raw.isBlank()) return 0L
        return try {
            rfc1123.parse(raw)?.time ?: 0L
        } catch (_: ParseException) {
            0L
        }
    }

    private fun childText(parent: Element, tag: String): String {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (element.localName == tag || element.tagName == tag) {
                return element.textContent.orEmpty()
            }
        }
        return ""
    }

    private fun namespacedChildText(parent: Element, namespace: String, localName: String): String {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (element.localName == localName &&
                (element.namespaceURI == namespace || element.tagName.endsWith(":$localName"))
            ) {
                return element.textContent.orEmpty()
            }
        }
        return ""
    }
}
