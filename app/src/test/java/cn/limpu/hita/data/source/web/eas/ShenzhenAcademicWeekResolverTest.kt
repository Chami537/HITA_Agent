package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.TermItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShenzhenAcademicWeekResolverTest {
    @Test
    fun `summer week one is derived from summer overview instead of today`() {
        val dates = ShenzhenAcademicWeekResolver.resolveWeekDates(
            listOf("2026-07-01", "2026-06-29", "invalid"),
            week = 1
        )

        assertEquals(
            listOf(
                "2026-06-29", "2026-06-30", "2026-07-01", "2026-07-02",
                "2026-07-03", "2026-07-04", "2026-07-05"
            ),
            dates
        )
    }

    @Test
    fun `spring selected week keeps stable seven day offset`() {
        val dates = ShenzhenAcademicWeekResolver.resolveWeekDates(
            listOf("2026-03-09", "2026-03-10"),
            week = 4
        )

        assertEquals("2026-03-30", dates.first())
        assertEquals("2026-04-05", dates.last())
    }

    @Test
    fun `a sparse matrix date still expands to its complete week`() {
        val dates = ShenzhenAcademicWeekResolver.resolveQueryDates(
            matrixDates = listOf("2026-07-01"),
            overviewTermDates = listOf("1999-01-01"),
            requestedWeek = 8
        )

        assertEquals("2026-06-29", dates.first())
        assertEquals("2026-07-05", dates.last())
        assertEquals(7, dates.size)
    }

    @Test
    fun `missing summer matrix uses selected term overview and requested week`() {
        val dates = ShenzhenAcademicWeekResolver.resolveQueryDates(
            matrixDates = emptyList(),
            overviewTermDates = listOf("2026-06-29", "2026-07-01"),
            requestedWeek = 1
        )

        assertEquals("2026-06-29", dates.first())
        assertEquals("2026-07-05", dates.last())
    }

    @Test
    fun `summer scan begins in ending year`() {
        val term = TermItem("2025-2026", "2025-2026", "3", "夏季")

        assertEquals(LocalDate.of(2026, 6, 1), ShenzhenAcademicWeekResolver.approximateAnchor(term))
    }
}
