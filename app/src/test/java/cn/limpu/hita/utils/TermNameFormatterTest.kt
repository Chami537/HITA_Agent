package cn.limpu.hita.utils

import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TermNameFormatterTest {
    @Test
    fun `uses termName when present`() {
        assertEquals("2026春季学期", TermNameFormatter.shortTermName("2025-2026学年 2026春季学期", "2025-2026 2026春季"))
    }

    @Test
    fun `uses fallback as is when termName missing`() {
        assertEquals("2026春季学期", TermNameFormatter.shortTermName("", "2025-2026学年 2026春季学期"))
        assertEquals("2026春季", TermNameFormatter.shortTermName(null, "2026春季"))
    }

    @Test
    fun `handles blanks`() {
        assertEquals("", TermNameFormatter.shortTermName(" ", ""))
    }

    @Test
    fun `full term name always includes academic year`() {
        val term = TermItem("2025-2026", "2025-2026", "2", "2026春季学期")

        assertEquals("2025-2026 春季", TermNameFormatter.fullTermName(term))
    }
}
