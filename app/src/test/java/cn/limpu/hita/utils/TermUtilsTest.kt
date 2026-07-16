package cn.limpu.hita.utils

import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TermUtilsTest {
    @Test
    fun `import picker recent filter keeps 2026 summer and sorts it before spring`() {
        val spring = TermItem("2025-2026", "2025-2026", "2", "春季")
        val summer = TermItem("2025-2026", "2025-2026", "3", "夏季")
        val old = TermItem("2021-2022", "2021-2022", "1", "秋季")

        val result = TermUtils.filterRecentTerms(listOf(spring, old, summer), currentYear = 2026)

        assertEquals(listOf("2025-2026-3", "2025-2026-2"), result.map { it.id })
    }

    @Test
    fun `student term filter keeps enrollment and later terms newest first`() {
        val beforeEnrollment = TermItem("2021-2022", "2021-2022", "2", "春季")
        val enrollmentFall = TermItem("2022-2023", "2022-2023", "1", "秋季")
        val currentSpring = TermItem("2025-2026", "2025-2026", "2", "春季").apply {
            isCurrent = true
        }
        val currentSummer = TermItem("2025-2026", "2025-2026", "3", "夏季")
        val future = TermItem("2026-2027", "2026-2027", "1", "秋季")

        val result = TermUtils.filterTermsForStudent(
            listOf(enrollmentFall, future, currentSpring, beforeEnrollment, currentSummer),
            grade = "2022级",
            currentYear = 2026,
            currentMonth = Calendar.JULY
        )

        assertEquals(
            listOf("2025-2026-3", "2025-2026-2", "2022-2023-1"),
            result.map { it.id }
        )
    }
}
