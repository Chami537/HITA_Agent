package cn.limpu.hita.utils

import cn.limpu.hita.data.model.eas.CourseScoreItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightedScoreCalculatorTest {
    @Test
    fun `fractional credits are displayed without losing decimal part`() {
        assertEquals("1.5", formatCredits(1.5f))
        assertEquals("2", formatCredits(2f))
    }

    @Test
    fun `fractional credits contribute to weighted results`() {
        val result = WeightedScoreCalculator.calculate(
            listOf(
                score(credits = 1.5f, finalScore = 80),
                score(credits = 2f, finalScore = 90),
            ),
        )

        assertEquals(3.5f, result.totalCredits, 0f)
        assertEquals(85.714f, result.weightedAverage, 0.001f)
        assertEquals(3.657f, result.gpa, 0.001f)
    }

    private fun score(credits: Float, finalScore: Int): CourseScoreItem {
        return CourseScoreItem().apply {
            this.credits = credits
            finalScores = finalScore
        }
    }
}
