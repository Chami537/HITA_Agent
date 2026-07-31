package cn.limpu.hita.ui.eas.score

import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTermSelectionTest {
    @Test
    fun `refreshed term list preserves explicit selection`() {
        val spring = term("2025-2026", "2", current = false)
        val summer = term("2025-2026", "3", current = true)

        assertEquals(spring.id, chooseScoreTerm(listOf(summer, spring), spring.id)?.id)
    }

    @Test
    fun `current term is used only when saved selection is unavailable`() {
        val spring = term("2025-2026", "2", current = false)
        val summer = term("2025-2026", "3", current = true)

        assertEquals(summer.id, chooseScoreTerm(listOf(spring, summer), "missing")?.id)
    }

    private fun term(year: String, code: String, current: Boolean): TermItem {
        return TermItem(year, year, code, "").apply { isCurrent = current }
    }
}
