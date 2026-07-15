package cn.limpu.hita.data.model.eas

data class ScoreSummary(
    /** 教务系统返回的平均学分绩（PJXFJ / XFJ）。 */
    val weightedAverage: String = "",
    /** 教务系统返回的 GPA；未提供时保持为空，禁止用百分制成绩自行换算冒充。 */
    val gpa: String = "",
    val rank: String = "",
    val total: String = "",
    val earnedCredits: String = "",
    val passedCourses: String = "",
    val allCourseWeightedAverage: String = "",
    val allPassedCourseGpa: String = "",
    val scope: ScoreSummaryScope = ScoreSummaryScope.UNKNOWN
)

enum class ScoreSummaryScope {
    SELECTED_TERM,
    CUMULATIVE,
    UNKNOWN
}

data class ScoreQueryResult(
    val items: List<CourseScoreItem> = emptyList(),
    val summary: ScoreSummary? = null
)
