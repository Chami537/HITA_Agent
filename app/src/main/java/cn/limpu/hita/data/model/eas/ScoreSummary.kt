package cn.limpu.hita.data.model.eas

data class ScoreSummary(
    val gpa: String = "",
    val rank: String = "",
    val total: String = ""
)

data class ScoreQueryResult(
    val items: List<CourseScoreItem> = emptyList(),
    val summary: ScoreSummary? = null
)
