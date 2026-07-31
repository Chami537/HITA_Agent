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
        assertEquals(2, result.validCourses)
    }

    @Test
    fun `zero numeric score remains part of weighted estimate`() {
        val result = WeightedScoreCalculator.calculate(
            listOf(
                score(credits = 2f, finalScore = 0),
                score(credits = 2f, finalScore = 80),
            )
        )

        assertEquals(40f, result.weightedAverage, 0f)
        assertEquals(4f, result.totalCredits, 0f)
    }

    @Test
    fun `pass fail text is excluded from numeric estimate`() {
        val passFail = score(credits = 2f, finalScore = 60).apply {
            finalScoresText = "合格"
        }
        val numeric = score(credits = 1f, finalScore = 90).apply {
            finalScoresText = "90"
        }

        val result = WeightedScoreCalculator.calculate(listOf(passFail, numeric))

        assertEquals(90f, result.weightedAverage, 0f)
        assertEquals(1f, result.totalCredits, 0f)
        assertEquals(1, result.validCourses)
    }

    private fun score(credits: Float, finalScore: Int): CourseScoreItem {
        return CourseScoreItem().apply {
            this.credits = credits
            finalScores = finalScore
        }
    }
}
