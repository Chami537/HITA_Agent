package cn.limpu.hita.data.model.eas

enum class ShenzhenRecommendationPreference {
    BALANCED,
    FREE_DAY,
    FEWER_EARLY_CLASSES
}

data class ShenzhenRecommendationOptions(
    val targetAdditionalCredits: Double = 6.0,
    val preference: ShenzhenRecommendationPreference = ShenzhenRecommendationPreference.BALANCED,
    val excludeFull: Boolean = true,
    val excludeConflicts: Boolean = true
)

data class ShenzhenRecommendedPlan(
    val courses: List<ShenzhenCourseCatalogItem>,
    val additionalCredits: Double,
    val totalCredits: Double,
    val activeWeekdays: Int,
    val earlyClassCount: Int,
    val conflictCount: Int,
    val summary: String
)

data class ShenzhenCourseRecommendationResult(
    val selectedCredits: Double,
    val targetAdditionalCredits: Double,
    val candidateCount: Int,
    val excludedFullCount: Int,
    val excludedConflictCount: Int,
    val plans: List<ShenzhenRecommendedPlan>
)
