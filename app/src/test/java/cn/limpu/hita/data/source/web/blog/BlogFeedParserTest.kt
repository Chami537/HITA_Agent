package cn.limpu.hita.data.source.web.blog

import cn.limpu.hita.data.repository.BlogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogFeedParserTest {
    @Test
    fun `parse extracts channel date encoded html and nested paths`() {
        val feed = BlogFeedParser.parse(SAMPLE_FEED)

        assertEquals("Tue, 22 Sep 2026 23:56:40 GMT", feed.lastBuildDate)
        assertEquals(3, feed.articles.size)

        val parent = feed.articles[0]
        assertEquals("https://hoa.moe/blog/auto-survival-guide", parent.guid)
        assertEquals("AUTO 野生技术指南", parent.title)
        assertEquals("auto-survival-guide", parent.path)
        assertTrue(parent.htmlContent.contains("parent body"))
        assertTrue(parent.htmlContent.contains("/blog/auto-survival-guide/imu"))

        val child = feed.articles[1]
        assertEquals("auto-survival-guide/imu", child.path)
        assertEquals("IMU", child.title)

        val nested = feed.articles[2]
        assertEquals("auto-survival-guide/imu/bmi088", nested.path)
        assertEquals("IMU 数据读取--以 BMI088 为例", nested.title)
    }

    @Test
    fun `parse skips items missing title or link`() {
        val feed = BlogFeedParser.parse(
            """
            <rss version="2.0">
              <channel>
                <item>
                  <title></title>
                  <link>https://hoa.moe/blog/missing-title</link>
                  <guid>https://hoa.moe/blog/missing-title</guid>
                </item>
                <item>
                  <title>Only title</title>
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )
        assertTrue(feed.articles.isEmpty())
    }

    @Test
    fun `pathFromLink strips origin query and slash`() {
        assertEquals("auto-survival-guide/imu", BlogFeedParser.pathFromLink("https://hoa.moe/blog/auto-survival-guide/imu/"))
        assertEquals("macbook-guide", BlogFeedParser.pathFromLink("https://hoa.moe/blog/macbook-guide?utm=1"))
    }

    @Test
    fun `parseRfc1123 reads GMT pubDate`() {
        val millis = BlogFeedParser.parseRfc1123("Mon, 14 Sep 2026 00:00:00 GMT")
        assertTrue(millis > 0L)
        assertEquals(0L, BlogFeedParser.parseRfc1123(""))
    }

    @Test
    fun `normalizeBlogLink accepts hoa paths and rejects others`() {
        assertEquals(
            "https://hoa.moe/blog/macbook-guide",
            BlogRepository.normalizeBlogLink("/blog/macbook-guide#section"),
        )
        assertEquals(
            "https://hoa.moe/blog/macbook-guide",
            BlogRepository.normalizeBlogLink("blog/macbook-guide"),
        )
        assertEquals(
            "https://hoa.moe/blog/macbook-guide",
            BlogRepository.normalizeBlogLink("https://www.hoa.moe/blog/macbook-guide/"),
        )
        assertNull(BlogRepository.normalizeBlogLink("https://example.com/blog/macbook-guide"))
        assertNull(BlogRepository.normalizeBlogLink("https://hoa.moe/about"))
    }

    companion object {
        private val SAMPLE_FEED = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>HOA 博客</title>
                <lastBuildDate>Tue, 22 Sep 2026 23:56:40 GMT</lastBuildDate>
                <item>
                  <title><![CDATA[AUTO 野生技术指南]]></title>
                  <link>https://hoa.moe/blog/auto-survival-guide</link>
                  <guid isPermaLink="false">https://hoa.moe/blog/auto-survival-guide</guid>
                  <pubDate>Mon, 14 Sep 2026 00:00:00 GMT</pubDate>
                  <description><![CDATA[desc]]></description>
                  <content:encoded><![CDATA[<p>parent body</p><a href="/blog/auto-survival-guide/imu">IMU</a>]]></content:encoded>
                </item>
                <item>
                  <title>IMU</title>
                  <link>https://hoa.moe/blog/auto-survival-guide/imu</link>
                  <guid>https://hoa.moe/blog/auto-survival-guide/imu</guid>
                  <pubDate>Sun, 13 Sep 2026 00:00:00 GMT</pubDate>
                  <description>imu</description>
                  <content:encoded><![CDATA[<p>imu body</p>]]></content:encoded>
                </item>
                <item>
                  <title>IMU 数据读取--以 BMI088 为例</title>
                  <link>https://hoa.moe/blog/auto-survival-guide/imu/bmi088</link>
                  <guid>https://hoa.moe/blog/auto-survival-guide/imu/bmi088</guid>
                  <pubDate>Sat, 12 Sep 2026 00:00:00 GMT</pubDate>
                  <description>bmi</description>
                  <content:encoded><![CDATA[<p>bmi body</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
