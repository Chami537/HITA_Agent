package com.limpu.hitax.utils

import com.limpu.hitax.data.model.eas.CourseScoreItem
object WeightedScoreCalculator {

    data class ScoreResult(
        val gpa: Float,              // 学期绩点（GPA）
        val cgpa: Float,             // 累计绩点（CGPA），单学期时等于GPA
        val weightedAverage: Float,  // 加权平均分（学分绩）
        val totalCredits: Int,       // 总学分
        val validCourses: Int        // 有效课程数
    )

    /**
     * 成绩转绩点（HIT标准4.0制）
     */
    private fun scoreToGradePoint(score: Int): Float = when {
        score >= 90 -> 4.0f
        score >= 85 -> 3.6f
        score >= 80 -> 3.2f
        score >= 75 -> 2.7f
        score >= 70 -> 2.2f
        score >= 65 -> 1.7f
        score >= 60 -> 1.0f
        else -> 0f
    }

    /**
     * 计算当前学期所有指标
     * GPA = Σ(绩点 × 学分) / Σ学分
     * 学分绩 = Σ(成绩 × 学分) / Σ学分
     * CGPA = GPA（单学期数据下相同）
     */
    fun calculate(items: List<CourseScoreItem>): ScoreResult {
        val validItems = items.filter { it.credits > 0 && it.finalScores > 0 }
        if (validItems.isEmpty()) {
            return ScoreResult(0f, 0f, 0f, 0, 0)
        }
        val totalCredits = validItems.sumOf { it.credits }
        val totalScoreXCredit = validItems.sumOf { it.finalScores * it.credits }
        val totalGradePointXCredit = validItems.sumOf {
            (scoreToGradePoint(it.finalScores) * it.credits.toDouble())
        }.toFloat()

        val weightedAverage = totalScoreXCredit.toFloat() / totalCredits
        val gpa = totalGradePointXCredit / totalCredits

        return ScoreResult(
            gpa = gpa,
            cgpa = gpa, // 单学期CGPA = GPA，有全量数据时可独立计算
            weightedAverage = weightedAverage,
            totalCredits = totalCredits,
            validCourses = validItems.size
        )
    }

    /**
     * 多学期累计CGPA
     * CGPA = Σ(所有学期绩点×学分) / Σ(所有学期学分)
     */
    fun calculateCGPA(allSemesterItems: List<List<CourseScoreItem>>): Float {
        val allValid = allSemesterItems.flatten().filter { it.credits > 0 && it.finalScores > 0 }
        if (allValid.isEmpty()) return 0f
        val totalCredits = allValid.sumOf { it.credits }
        val totalGradePointXCredit = allValid.sumOf {
            (scoreToGradePoint(it.finalScores) * it.credits.toDouble())
        }.toFloat()
        return totalGradePointXCredit / totalCredits
    }
}
