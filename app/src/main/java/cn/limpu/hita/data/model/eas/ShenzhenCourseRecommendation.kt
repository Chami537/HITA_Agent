package cn.limpu.hita.data.model.eas

enum class ShenzhenRecommendationPreference {
    BALANCED,
    FREE_DAY,
    FEWER_EARLY_CLASSES
}

enum class ShenzhenRecommendationCreditType {
    CROSS_MAJOR,
    GENERAL_EDUCATION,
    AESTHETIC_EDUCATION,
    FOUR_HISTORIES,
    ENGLISH_TAUGHT,
    PRACTICE_INNOVATION_TOTAL,
    INNOVATION,
    SOCIAL_PRACTICE
}

data class ShenzhenRecommendationCreditProgress(
    val type: ShenzhenRecommendationCreditType,
    val label: String,
    val requiredCredits: Double,
    val projectedCredits: Double,
    val remainingCredits: Double
)

data class ShenzhenRecommendationTrack(
    val id: String,
    val name: String,
    val courseCodes: Set<String>,
    val compulsoryCourseCodes: Set<String> = courseCodes
)

fun recognizedCrossMajorCourseCodes(
    tracks: List<ShenzhenRecommendationTrack>,
    plannedTrackId: String
): Set<String> {
    val plannedTrack = tracks.firstOrNull { it.id == plannedTrackId } ?: return emptySet()
    val ownCourseCodes = plannedTrack.courseCodes.mapTo(hashSetOf()) { it.normalizedCourseCode() }
    return tracks.asSequence()
        .filter { it.id != plannedTrackId }
        .flatMap { it.compulsoryCourseCodes.asSequence() }
        .map { it.normalizedCourseCode() }
        .filter { it.isNotBlank() && it !in ownCourseCodes }
        .toSet()
}

private fun String.normalizedCourseCode(): String = trim().uppercase()

data class ShenzhenRecommendationOptions(
    val targetAdditionalCredits: Double = 6.0,
    val minAdditionalCredits: Double = targetAdditionalCredits,
    val maxAdditionalCredits: Double = targetAdditionalCredits,
    val preference: ShenzhenRecommendationPreference = ShenzhenRecommendationPreference.BALANCED,
    val excludeFull: Boolean = true,
    val excludeConflicts: Boolean = true,
    val includePracticeInnovationCourses: Boolean = false,
    val includeMinorPlanCourses: Boolean = false,
    val plannedTrackId: String = "",
    val recognizedCrossMajorCourseCodes: Set<String> = emptySet()
)

data class ShenzhenRecommendedPlan(
    val courses: List<ShenzhenCourseCatalogItem>,
    val additionalCredits: Double,
    val totalCredits: Double,
    val activeWeekdays: Int,
    val earlyClassCount: Int,
    val conflictCount: Int,
    val summary: String,
    val creditProgress: List<ShenzhenRecommendationCreditProgress> = emptyList(),
    val countedMoocCredits: Double = 0.0
)

data class ShenzhenCourseRecommendationResult(
    val selectedCredits: Double,
    val targetAdditionalCredits: Double,
    val minAdditionalCredits: Double = targetAdditionalCredits,
    val maxAdditionalCredits: Double = targetAdditionalCredits,
    val candidateCount: Int,
    val excludedFullCount: Int,
    val excludedConflictCount: Int,
    val excludedPracticeInnovationCount: Int = 0,
    val excludedMinorPlanCount: Int = 0,
    val plans: List<ShenzhenRecommendedPlan>,
    val currentCreditProgress: List<ShenzhenRecommendationCreditProgress> = emptyList(),
    val moocCreditCap: Double = 2.0,
    val currentCountedMoocCredits: Double = 0.0
)
