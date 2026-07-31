package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenHistoricalGradeAnalyzerTest {
    @Test
    fun `two years before keeps the same term`() {
        val current = TermItem("2025-2026", "2025-2026", "2", "春季")

        val historical = ShenzhenHistoricalGradeAnalyzer.termYearsBefore(current, 2)

        requireNotNull(historical)
        assertEquals("2023-2024", historical.yearCode)
        assertEquals("2", historical.termCode)
        assertEquals("2023-2024-2", historical.id)
    }

    @Test
    fun `course matching uses normalized exact name across course code changes`() {
        val reference = course("A-1", "大学物理（下）")

        assertTrue(
            ShenzhenHistoricalGradeAnalyzer.matches(
                reference,
                course("OLD-1", "大学物理 (下)")
            )
        )
        assertFalse(
            ShenzhenHistoricalGradeAnalyzer.matches(
                reference,
                course("A-1", "大学物理（上）")
            )
        )
    }

    @Test
    fun `teacher rates combine classes by student count and sort ascending`() {
        val rates = ShenzhenHistoricalGradeAnalyzer.aggregate(
            listOf(
                ShenzhenHistoricalClassStats(
                    "张老师",
                    scores = List(40) { if (it < 4) 50.0 else 80.0 },
                    excludedIncompleteStudentCount = 3
                ),
                ShenzhenHistoricalClassStats("张老师", scores = List(60) { if (it < 6) 50.0 else 80.0 }),
                ShenzhenHistoricalClassStats("李老师", scores = List(50) { if (it < 2) 50.0 else 90.0 })
            )
        )

        assertEquals(listOf("李老师", "张老师"), rates.map { it.teacher })
        assertEquals(4.0, rates[0].failureRate, 0.001)
        assertEquals(10.0, rates[1].failureRate, 0.001)
        assertEquals(2, rates[1].classCount)
        assertEquals(100, rates[1].studentCount)
        assertEquals(10, rates[1].failCount)
        assertEquals(77.0, rates[1].averageScore, 0.001)
        assertEquals(80.0, rates[1].top20AverageScore, 0.001)
        assertEquals(3, rates[1].excludedIncompleteStudentCount)
    }

    private fun course(code: String, name: String) = ShenzhenCourseCatalogItem(
        id = code,
        courseCode = code,
        courseName = name,
        source = ShenzhenCourseCatalogSource.SCHOOL
    )
}
