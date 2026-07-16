package cn.limpu.hita.data.model.eas

enum class ShenzhenTrainingPlanLevel {
    UNDERGRADUATE,
    POSTGRADUATE
}

data class ShenzhenTrainingPlan(
    val id: String,
    val changeId: String = "",
    val name: String,
    val majorCode: String = "",
    val majorName: String = "",
    val majorDirection: String = "",
    val schoolCode: String = "",
    val schoolName: String = "",
    val grade: String = "",
    val version: String = "",
    val programType: String = "",
    val degreeName: String = "",
    val level: ShenzhenTrainingPlanLevel
)

data class ShenzhenTrainingPlanGroup(
    val id: String,
    val parentId: String = "",
    val name: String,
    val type: String = "",
    val required: Boolean? = null,
    val minimumCredits: Double? = null,
    val minimumCourses: Int? = null,
    val minimumHours: Double? = null
)

data class ShenzhenTrainingPlanCourse(
    val id: String,
    val planId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val courseCode: String,
    val courseName: String,
    val courseNameEnglish: String = "",
    val credits: Double? = null,
    val totalHours: Double? = null,
    val theoryHours: Double? = null,
    val labHours: Double? = null,
    val practiceHours: Double? = null,
    val computerHours: Double? = null,
    val assessmentMethod: String = "",
    val required: Boolean? = null,
    val courseNature: String = "",
    val courseCategory: String = "",
    val recommendedTerm: String = "",
    val offeringCollege: String = "",
    val teachingLanguage: String = "",
    val completed: Boolean? = null
)

data class ShenzhenTrainingPlanCategory(
    val id: String,
    val name: String,
    val group: ShenzhenTrainingPlanGroup? = null,
    val courses: List<ShenzhenTrainingPlanCourse>
) {
    val credits: Double
        get() = courses.mapNotNull { it.credits }.sum()
}

data class ShenzhenTrainingPlanDetail(
    val plan: ShenzhenTrainingPlan,
    val groups: List<ShenzhenTrainingPlanGroup>,
    val courses: List<ShenzhenTrainingPlanCourse>,
    val categories: List<ShenzhenTrainingPlanCategory>
) {
    val totalCredits: Double
        get() = courses.mapNotNull { it.credits }.sum()
}

internal data class ShenzhenTrainingPlanIdentity(
    val major: String = "",
    val grade: String = "",
    val studentType: String = ""
)
