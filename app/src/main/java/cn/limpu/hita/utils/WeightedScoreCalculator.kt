package cn.limpu.hita.utils

import cn.limpu.hita.data.model.eas.CourseScoreItem
object WeightedScoreCalculator {

    data class ScoreResult(
        val weightedAverage: Float,
        val totalCredits: Float,
        val validCourses: Int
    )

    /**
     * 对当前页面中可解析为百分制数字的成绩做加权平均。
     *
     * 这不是教务系统的平均学分绩：接口没有提供“是否计入”、重修替代等规则，
     * 等级制和“合格/不合格”成绩也会被排除。结果只能用于核对当前列表。
     */
    fun calculate(items: List<CourseScoreItem>): ScoreResult {
        val validItems = items.mapNotNull { item ->
            if (item.credits <= 0f) return@mapNotNull null
            val rawText = item.finalScoresText?.trim()
            val score = if (!rawText.isNullOrEmpty()) {
                rawText.toDoubleOrNull()
            } else {
                item.finalScores.takeIf { it >= 0 }?.toDouble()
            }
            score?.takeIf { it in 0.0..100.0 }?.let { item to it }
        }
        if (validItems.isEmpty()) {
            return ScoreResult(0f, 0f, 0)
        }
        val totalCredits = validItems.sumOf { (item, _) -> item.credits.toDouble() }.toFloat()
        val totalScoreXCredit = validItems.sumOf { (item, score) -> score * item.credits.toDouble() }

        val weightedAverage = (totalScoreXCredit / totalCredits).toFloat()

        return ScoreResult(
            weightedAverage = weightedAverage,
            totalCredits = totalCredits,
            validCourses = validItems.size
        )
    }
}
