package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ScoreSummaryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreSummaryParserTest {
    @Test
    fun `official Shenzhen fields keep GPA and weighted average separate`() {
        val summary = ScoreSummaryParser.fromShenzhenGpa(
            mapOf(
                "PJXFJ" to "86.42",
                "GPA" to "3.61",
                "PJXFJ_PM" to "12",
                "ZRS" to "130",
                "HDXF" to "72.5",
                "TGKC" to "28",
                "QBKCPJXFJ" to "84.10",
                "GPA_QBJQKC" to "3.72"
            )
        )

        requireNotNull(summary)
        assertEquals("86.42", summary.weightedAverage)
        assertEquals("3.61", summary.gpa)
        assertEquals("12", summary.rank)
        assertEquals("72.5", summary.earnedCredits)
        assertEquals(ScoreSummaryScope.SELECTED_TERM, summary.scope)
    }

    @Test
    fun `empty official summary is ignored`() {
        assertNull(ScoreSummaryParser.fromShenzhenGpa(emptyMap()))
    }
}
