package cn.limpu.hita.data.model.eas

enum class ShenzhenGradeStatus {
    PUBLISHED,
    EARLY,
    SELECTED
}

enum class ShenzhenGradeAnalysisScope {
    /** 新版 seeFx 携带 cjid 后只返回当前学生的分项。 */
    PERSONAL,

    /** 兼容旧后端曾经返回的匿名教学班明细。 */
    CLASS
}

data class ShenzhenGradeCourse(
    val rowId: String = "",
    val recordId: String = "",
    val taskId: String,
    val taskNumber: String = "",
    val courseCode: String = "",
    val courseName: String,
    val termCode: String = "",
    val teacher: String = "",
    val credits: Double? = null,
    val myScore: Double? = null,
    val status: ShenzhenGradeStatus = ShenzhenGradeStatus.SELECTED
)

data class ShenzhenGradeComponent(
    val name: String,
    val score: Double?,
    val fullScore: Double,
    val weight: Double
)

data class ShenzhenStudentGrade(
    val anonymousId: String,
    val total: Double,
    val components: List<ShenzhenGradeComponent>
)

data class ShenzhenScoreBand(
    val label: String,
    val count: Int
)

data class ShenzhenTeacherFailureRate(
    val teacher: String,
    val classCount: Int,
    val studentCount: Int,
    val failCount: Int,
    val failureRate: Double,
    val averageScore: Double,
    val top20AverageScore: Double,
    val excludedIncompleteStudentCount: Int
)

data class ShenzhenHistoricalFailureReport(
    val courseName: String,
    val courseCode: String,
    val targetTerm: TermItem,
    val matchedClassCount: Int,
    val analyzedClassCount: Int,
    val skippedClassCount: Int,
    val teacherRates: List<ShenzhenTeacherFailureRate>
)

data class ShenzhenGradeAnalysis(
    val course: ShenzhenGradeCourse,
    val scope: ShenzhenGradeAnalysisScope,
    val students: List<ShenzhenStudentGrade>,
    val componentDefinitions: List<ShenzhenGradeComponent>,
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val maximum: Double,
    val minimum: Double,
    val failCount: Int,
    val excludedIncompleteStudentCount: Int = 0,
    val myStudentId: String? = null,
    val myScore: Double? = null,
    val myRank: Int? = null,
    val percentile: Double? = null,
    val identityMatchCount: Int = 0,
    val bands: List<ShenzhenScoreBand>
) {
    val failRate: Double
        get() = if (students.isEmpty()) 0.0 else failCount * 100.0 / students.size

    val myComponents: List<ShenzhenGradeComponent>
        get() = students.firstOrNull { it.anonymousId == myStudentId }?.components.orEmpty()
}
