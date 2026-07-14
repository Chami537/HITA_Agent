package cn.limpu.hita.utils

import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TermUtilsTest {
    @Test
    fun `import picker recent filter keeps 2026 summer and sorts it before spring`() {
        val spring = TermItem("2025-2026", "2025-2026", "2", "春季")
        val summer = TermItem("2025-2026", "2025-2026", "3", "夏季")
        val old = TermItem("2021-2022", "2021-2022", "1", "秋季")

        val result = TermUtils.filterRecentTerms(listOf(spring, old, summer), currentYear = 2026)

        assertEquals(listOf("2025-2026-3", "2025-2026-2"), result.map { it.id })
    }
}
