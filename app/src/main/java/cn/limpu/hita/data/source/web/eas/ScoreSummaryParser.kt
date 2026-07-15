package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ScoreSummary
import cn.limpu.hita.data.model.eas.ScoreSummaryScope

internal object ScoreSummaryParser {
    private val shenzhenKeys = listOf(
        "PJXFJ",
        "GPA",
        "PJXFJ_PM",
        "PM",
        "ZRS",
        "HDXF",
        "TGKC",
        "QBKCPJXFJ",
        "GPA_QBJQKC"
    )

    fun shenzhenKeys(): List<String> = shenzhenKeys

    fun fromShenzhenGpa(values: Map<String, String>): ScoreSummary? {
        fun value(key: String): String = values[key].orEmpty().trim()
        val summary = ScoreSummary(
            weightedAverage = value("PJXFJ"),
            gpa = value("GPA"),
            rank = value("PJXFJ_PM").ifBlank { value("PM") },
            total = value("ZRS"),
            earnedCredits = value("HDXF"),
            passedCourses = value("TGKC"),
            allCourseWeightedAverage = value("QBKCPJXFJ"),
            allPassedCourseGpa = value("GPA_QBJQKC"),
            scope = ScoreSummaryScope.SELECTED_TERM
        )
        return summary.takeIf {
            it.weightedAverage.isNotBlank() || it.gpa.isNotBlank() ||
                it.rank.isNotBlank() || it.earnedCredits.isNotBlank()
        }
    }
}
