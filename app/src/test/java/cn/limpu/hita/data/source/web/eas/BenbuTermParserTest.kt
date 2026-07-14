package cn.limpu.hita.data.source.web.eas

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class BenbuTermParserTest {
    @Test
    fun `actual selectors parse and retain summer after current spring`() {
        val scoreDoc = Jsoup.parse(
            """
            <select name="pageXnxq">
              <option value="2025-20262" selected>2025-2026学年第二学期</option>
            </select>
            """.trimIndent()
        )
        val timetableDoc = Jsoup.parse(
            """
            <select name="xnxq">
              <option value="2025-20262" selected>2025-2026学年第二学期</option>
              <option value="2025-2026-3">2025-2026学年夏季学期</option>
            </select>
            """.trimIndent()
        )

        val availableTerms = BenbuTermParser.mergeTerms(
            BenbuTermParser.parseTerms(scoreDoc, "pageXnxq"),
            BenbuTermParser.parseTerms(timetableDoc, "xnxq")
        )

        assertEquals(listOf("2025-2026-2", "2025-2026-3"), availableTerms.map { it.id })
        assertEquals("2025-2026 夏季学期", availableTerms.last().name)
    }
}
