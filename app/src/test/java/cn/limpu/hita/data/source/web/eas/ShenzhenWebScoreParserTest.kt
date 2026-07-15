package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ShenzhenWebScoreParserTest {
    @Test
    fun `web score payload keeps numeric and level grades`() {
        val term = TermItem("2025-2026", "2025-2026", "2", "春季").apply {
            name = "2026春季"
        }
        val parsed = ShenzhenWebScoreParser.parse(
            """{
                "code":0,
                "content":{"list":[
                    {"kcdm":"COMP1001","kcmc":"程序设计","xf":3.5,"xs":"56","xscj":"93","kcxz":"必修","kclb":"专业课"},
                    {"kcdm":"ART1001","kcmc":"艺术鉴赏","xf":2,"xscj":"A-","xnxqmc":"2026春季"}
                ]}
            }""",
            term
        )

        requireNotNull(parsed)
        assertEquals(2, parsed.items.size)
        assertEquals(93, parsed.items[0].finalScores)
        assertEquals("A-", parsed.items[1].finalScoresText)
        assertEquals(-1, parsed.items[1].finalScores)
        assertEquals("2026春季", parsed.items[0].termName)
    }
}
