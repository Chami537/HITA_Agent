package cn.limpu.hita.data.model.eas

data class ShenzhenCreditProgress(
    val requiredCredits: Double,
    val completedCredits: Double,
    val remainingCredits: Double,
    val requiredCourses: Int? = null,
    val completedCourses: Int? = null,
    val remainingCourses: Int? = null,
    val averageCreditScore: Double? = null,
    val rank: Int? = null,
    val cohortSize: Int? = null,
    val currentTerm: String = "",
    val categories: List<ShenzhenCreditRequirement> = emptyList(),
    val groups: List<ShenzhenCreditGroupProgress> = emptyList(),
    val courseRecords: List<ShenzhenCreditCourseRecord> = emptyList()
) {
    val completionRatio: Float
        get() = if (requiredCredits > 0.0) {
            (completedCredits / requiredCredits).toFloat().coerceIn(0f, 1f)
        } else 0f
}

data class ShenzhenCreditRequirement(
    val id: String,
    val name: String,
    val majorDirection: String = "",
    val teachingLanguage: String = "",
    val courseNature: String = "",
    val requiredCredits: Double,
    val completedCredits: Double,
    val remainingCredits: Double,
    val completedHours: Int? = null,
    val includesMooc: Boolean = false,
    val creditedMoocCredits: Double = 0.0,
    val earnedMoocCredits: Double = 0.0,
    val passed: Boolean
)

data class ShenzhenCreditGroupProgress(
    val id: String,
    val parentId: String = "",
    val name: String,
    val depth: Int,
    val requiredCredits: Double? = null,
    val completedCredits: Double = 0.0,
    val requiredCourses: Int? = null,
    val completedCourses: Int = 0,
    val passed: Boolean,
    val courses: List<ShenzhenCreditGroupCourse> = emptyList()
) {
    val remainingCredits: Double?
        get() = requiredCredits?.let { (it - completedCredits).coerceAtLeast(0.0) }
}

data class ShenzhenCreditGroupCourse(
    val courseCode: String,
    val courseName: String,
    val credits: Double,
    val recommendedTerm: String = "",
    val courseNature: String = "",
    val completed: Boolean
)

data class ShenzhenCreditCourseRecord(
    val id: String,
    val term: String,
    val courseCode: String,
    val courseName: String,
    val credits: Double,
    val score: String = "",
    val teacher: String = "",
    val courseNature: String = "",
    val courseCategory: String = ""
)

internal data class ShenzhenCreditIdentity(
    val studentNumber: String,
    val studentRecordId: String,
    val studentType: String,
    val grade: String
)

internal data class ShenzhenCreditPlanContext(
    val planId: String,
    val changeId: String = ""
)
