package com.limpu.hitax.utils

import com.limpu.hitax.data.model.eas.CourseScoreItem

object GPAUtils {

    data class LocalGPA(
        val weightedAverage: Float,  // 加权平均分
        val totalCredits: Int,       // 总学分
        val validCourses: Int        // 有效课程数
    )

    /**
     * 计算加权平均分（学分绩）
     * 公式: Σ(成绩 × 学分) / Σ(学分)
     * 只计入有学分且有成绩的课程
     */
    fun calculate(items: List<CourseScoreItem>): LocalGPA {
        val validItems = items.filter { it.credits > 0 && it.finalScores > 0 }
        if (validItems.isEmpty()) {
            return LocalGPA(0f, 0, 0)
        }
        val totalScoreXCredit = validItems.sumOf { it.finalScores * it.credits }
        val totalCredits = validItems.sumOf { it.credits }
        val weightedAverage = totalScoreXCredit.toFloat() / totalCredits
        return LocalGPA(weightedAverage, totalCredits, validItems.size)
    }
}
