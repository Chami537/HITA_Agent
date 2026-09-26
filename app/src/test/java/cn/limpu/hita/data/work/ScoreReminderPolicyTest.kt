package cn.limpu.hita.data.work

import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.CourseScoreItem
import cn.limpu.hita.data.model.eas.TermItem
import com.limpu.component.data.DataState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreReminderPolicyTest {
    @Test
    fun `placeholder states do not finish a score query`() {
        assertFalse(ScoreReminderPolicy.isTerminal(DataState.STATE.NOTHING))
        assertFalse(ScoreReminderPolicy.isTerminal(DataState.STATE.LOADING))
        assertTrue(ScoreReminderPolicy.isTerminal(DataState.STATE.SUCCESS))
        assertTrue(ScoreReminderPolicy.isTerminal(DataState.STATE.FETCH_FAILED))
    }

    @Test
    fun `first grade after an empty baseline is new`() {
        assertEquals(emptySet<String>(), ScoreReminderPolicy.newKeys(null, setOf("math:90")))
        assertEquals(setOf("math:90"), ScoreReminderPolicy.newKeys(emptySet(), setOf("math:90")))
    }

    @Test
    fun `account identity separates identical score keys`() {
        val first = EASToken().apply { campus = EASToken.Campus.BENBU; stuId = "001" }
        val second = EASToken().apply { campus = EASToken.Campus.BENBU; stuId = "002" }
        val third = EASToken().apply { campus = EASToken.Campus.WEIHAI; stuId = "001" }
        assertEquals("BENBU:001", ScoreReminderPolicy.ownerKey(first))
        assertEquals("BENBU:002", ScoreReminderPolicy.ownerKey(second))
        assertEquals("WEIHAI:001", ScoreReminderPolicy.ownerKey(third))
    }

    @Test
    fun `current and preceding terms are checked across academic years`() {
        val older = term("2024-2025", "2")
        val previous = term("2024-2025", "3")
        val current = term("2025-2026", "1", isCurrent = true)
        assertEquals(
            listOf(current, previous),
            ScoreReminderPolicy.termsToCheck(listOf(older, current, previous))
        )
    }

    @Test
    fun `newest two terms are checked when current marker is missing`() {
        val older = term("2024-2025", "2")
        val previous = term("2024-2025", "3")
        val newest = term("2025-2026", "1")
        assertEquals(
            listOf(newest, previous),
            ScoreReminderPolicy.termsToCheck(listOf(older, previous, newest, previous))
        )
    }

    @Test
    fun `baseline is isolated by owner and term`() {
        assertEquals("BENBU:001|2025-2026-1", ScoreReminderPolicy.baselineKey("BENBU:001", "2025-2026-1"))
        assertTrue(ScoreReminderPolicy.baselineKey("BENBU:001", "2025-2026-1") !=
            ScoreReminderPolicy.baselineKey("BENBU:001", "2024-2025-3"))
    }

    @Test
    fun `score text distinguishes grades with the same integer value`() {
        val first = CourseScoreItem().apply {
            courseCode = "CS101"
            courseName = "Algorithms"
            finalScores = 85
            finalScoresText = "A-"
        }
        val changed = CourseScoreItem().apply {
            courseCode = "CS101"
            courseName = "Algorithms"
            finalScores = 85
            finalScoresText = "B+"
        }
        assertTrue(ScoreReminderPolicy.scoreKey(first) != ScoreReminderPolicy.scoreKey(changed))
        first.finalScoresText = "85.1"
        changed.finalScoresText = "85.9"
        assertTrue(ScoreReminderPolicy.scoreKey(first) != ScoreReminderPolicy.scoreKey(changed))
    }

    private fun term(year: String, code: String, isCurrent: Boolean = false) =
        TermItem(year, year, code, code).apply { this.isCurrent = isCurrent }
}
