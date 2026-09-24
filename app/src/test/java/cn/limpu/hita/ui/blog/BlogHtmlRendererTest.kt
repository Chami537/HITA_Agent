package cn.limpu.hita.ui.blog

import org.junit.Assert.assertEquals
import org.junit.Test

class BlogHtmlRendererTest {
    @Test
    fun `absolutizeHoaUrls rewrites root-relative and protocol-relative urls`() {
        val html = """
            <a href="/blog/auto-survival-guide/imu">IMU</a>
            <img src="//hoa.moe/pic.png"/>
            <a href="https://hoa.moe/blog/macbook-guide">keep</a>
        """.trimIndent()

        val out = BlogHtmlRenderer.absolutizeHoaUrls(html)

        assertEquals(
            """
            <a href="https://hoa.moe/blog/auto-survival-guide/imu">IMU</a>
            <img src="https://hoa.moe/pic.png"/>
            <a href="https://hoa.moe/blog/macbook-guide">keep</a>
            """.trimIndent(),
            out,
        )
    }
}
