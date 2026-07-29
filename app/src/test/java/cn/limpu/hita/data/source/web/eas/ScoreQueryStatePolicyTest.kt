package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.CourseScoreItem
import cn.limpu.hita.data.model.eas.ScoreQueryResult
import cn.limpu.hita.data.model.eas.ScoreSummary
import cn.limpu.hita.data.model.eas.ScoreSummaryScope
import com.limpu.component.data.DataState
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreQueryStatePolicyTest {
    @Test
    fun `expired Web summary preserves successful API scores and summary`() {
        val apiScore = CourseScoreItem().apply {
            courseCode = "COMP1001"
            courseName = "程序设计"
            finalScoresText = "92"
        }
        val apiSummary = ScoreSummary(
            weightedAverage = "88.50",
            rank = "12",
            total = "120",
            scope = ScoreSummaryScope.CUMULATIVE
        )
        val successful = ScoreQueryResult(listOf(apiScore), apiSummary)

        val state = ScoreQueryStatePolicy.sessionExpired(successful)

        assertEquals(DataState.STATE.NOT_LOGGED_IN, state.state)
        assertEquals(listOf("程序设计"), state.data?.items.orEmpty().map { it.courseName })
        assertEquals(apiSummary, state.data?.summary)
        assertEquals("深圳 Web 会话已失效", state.message)
    }
}
