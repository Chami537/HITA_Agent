package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCreditRequirement
import cn.limpu.hita.data.model.eas.ShenzhenCourseRecommendationResult
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationPreference
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationCreditProgress
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationCreditType
import cn.limpu.hita.data.model.eas.ShenzhenRecommendedPlan
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class ShenzhenCourseMeeting(
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>
)

internal object ShenzhenCourseRecommendationEngine {
    private const val BEAM_SIZE = 240
    private const val MOOC_GENERAL_EDUCATION_CAP = 2.0

    fun recommend(
        selected: List<ShenzhenCourseCatalogItem>,
        candidates: List<ShenzhenCourseCatalogItem>,
        options: ShenzhenRecommendationOptions,
        requirements: List<ShenzhenCreditRequirement> = emptyList()
    ): ShenzhenCourseRecommendationResult {
        val selectedMeetings = selected.flatMap { parseMeetings(it.schedule) }
        val selectedCredits = selected.sumOf(::credits)
        val minAdditionalCredits = options.minAdditionalCredits.coerceAtLeast(0.0)
        val maxAdditionalCredits = max(minAdditionalCredits, options.maxAdditionalCredits)
        val requirementTargets = requirementTargets(
            requirements,
            options.includePracticeInnovationCourses
        )
        val recognizedCrossMajorCourseCodes = options.recognizedCrossMajorCourseCodes
            .mapTo(hashSetOf(), ::normalizedCourseCode)
        val selectedBuckets = creditBuckets(selected, recognizedCrossMajorCourseCodes)
        val selectedKeys = selected.mapTo(mutableSetOf(), ::courseKey)
        val prepared = candidates.distinctBy { it.taskId.ifBlank { it.id } }
            .filter { courseKey(it) !in selectedKeys && credits(it) > 0.0 }

        val minorPlanCount = prepared.count(::isMinorPlanCourse)
        val afterMinorPlan = if (options.includeMinorPlanCourses) {
            prepared
        } else {
            prepared.filterNot(::isMinorPlanCourse)
        }
        val practiceInnovationCount = afterMinorPlan.count(::isPracticeInnovationCourse)
        val afterPracticeInnovation = if (options.includePracticeInnovationCourses) {
            afterMinorPlan
        } else {
            afterMinorPlan.filterNot(::isPracticeInnovationCourse)
        }
        val fullCount = afterPracticeInnovation.count { (it.remainingSeats ?: 1) <= 0 }
        val afterFull = if (options.excludeFull) {
            afterPracticeInnovation.filter { (it.remainingSeats ?: 1) > 0 }
        } else afterPracticeInnovation
        val conflictCount = afterFull.count { candidate ->
            candidate.hasConflict || conflicts(parseMeetings(candidate.schedule), selectedMeetings)
        }
        val usable = if (options.excludeConflicts) {
            afterFull.filterNot { candidate ->
                candidate.hasConflict || conflicts(parseMeetings(candidate.schedule), selectedMeetings)
            }
        } else afterFull

        val groups = usable.groupBy(::courseKey).values
            .map { rows -> rows.sortedByDescending { it.remainingSeats ?: Int.MIN_VALUE }.take(8) }
            .sortedBy { rows -> rows.firstOrNull()?.courseName.orEmpty() }

        data class State(
            val courses: List<ShenzhenCourseCatalogItem> = emptyList(),
            val credits: Double = 0.0,
            val creditBuckets: CreditBuckets = CreditBuckets(),
            val meetings: List<ShenzhenCourseMeeting> = selectedMeetings,
            val conflicts: Int = 0
        )

        var states = listOf(State())
        groups.forEach { group ->
            val expanded = ArrayList<State>(states.size * (group.size + 1))
            states.forEach { state ->
                expanded += state
                group.forEach { candidate ->
                    val candidateMeetings = parseMeetings(candidate.schedule)
                    val overlaps = candidate.hasConflict || conflicts(candidateMeetings, state.meetings)
                    if (options.excludeConflicts && overlaps) return@forEach
                    val nextCredits = state.credits + credits(candidate)
                    if (nextCredits > maxAdditionalCredits + 6.0) return@forEach
                    expanded += State(
                        courses = state.courses + candidate,
                        credits = nextCredits,
                        creditBuckets = state.creditBuckets + creditBuckets(
                            candidate,
                            recognizedCrossMajorCourseCodes
                        ),
                        meetings = state.meetings + candidateMeetings,
                        conflicts = state.conflicts + if (overlaps) 1 else 0
                    )
                }
            }
            states = expanded
                .distinctBy { state -> state.courses.map { it.taskId.ifBlank { it.id } }.sorted() }
                .sortedBy {
                    score(
                        it.credits,
                        selectedBuckets + it.creditBuckets,
                        it.meetings,
                        it.conflicts,
                        options,
                        requirementTargets,
                        minAdditionalCredits,
                        maxAdditionalCredits
                    )
                }
                .take(BEAM_SIZE)
        }

        val plans = states.asSequence()
            .filter { it.courses.isNotEmpty() }
            .sortedBy {
                score(
                    it.credits,
                    selectedBuckets + it.creditBuckets,
                    it.meetings,
                    it.conflicts,
                    options,
                    requirementTargets,
                    minAdditionalCredits,
                    maxAdditionalCredits
                )
            }
            .distinctBy { state ->
                state.courses.map { it.taskId.ifBlank { it.id } }.sorted()
            }
            .take(3)
            .map { state ->
                val activeDays = state.meetings.map { it.weekday }.distinct().size
                val early = earlyClassCount(state.meetings)
                val projectedBuckets = selectedBuckets + state.creditBuckets
                ShenzhenRecommendedPlan(
                    courses = state.courses.sortedBy { it.courseName },
                    additionalCredits = rounded(state.credits),
                    totalCredits = rounded(selectedCredits + state.credits),
                    activeWeekdays = activeDays,
                    earlyClassCount = early,
                    conflictCount = state.conflicts,
                    summary = summary(options.preference, activeDays, early, state.conflicts),
                    creditProgress = creditProgress(requirementTargets, projectedBuckets),
                    countedMoocCredits = countedMoocCredits(
                        requirementTargets,
                        projectedBuckets
                    )
                )
            }
            .toList()

        return ShenzhenCourseRecommendationResult(
            selectedCredits = rounded(selectedCredits),
            targetAdditionalCredits = options.targetAdditionalCredits,
            minAdditionalCredits = rounded(minAdditionalCredits),
            maxAdditionalCredits = rounded(maxAdditionalCredits),
            candidateCount = usable.size,
            excludedFullCount = if (options.excludeFull) fullCount else 0,
            excludedConflictCount = if (options.excludeConflicts) conflictCount else 0,
            excludedPracticeInnovationCount = if (options.includePracticeInnovationCourses) {
                0
            } else practiceInnovationCount,
            excludedMinorPlanCount = if (options.includeMinorPlanCourses) 0 else minorPlanCount,
            plans = plans,
            currentCreditProgress = creditProgress(requirementTargets, selectedBuckets),
            moocCreditCap = MOOC_GENERAL_EDUCATION_CAP,
            currentCountedMoocCredits = countedMoocCredits(
                requirementTargets,
                selectedBuckets
            )
        )
    }

    private data class RequirementTarget(
        val type: ShenzhenRecommendationCreditType,
        val label: String,
        val requiredCredits: Double,
        val completedCredits: Double
    )

    private data class RequirementTargets(
        val rows: List<RequirementTarget> = emptyList(),
        val officialCountedMoocCredits: Double = 0.0
    )

    private data class CreditBuckets(
        val crossMajor: Double = 0.0,
        val generalEducation: Double = 0.0,
        val mooc: Double = 0.0,
        val aesthetic: Double = 0.0,
        val fourHistories: Double = 0.0,
        val englishTaught: Double = 0.0,
        val innovation: Double = 0.0,
        val socialPractice: Double = 0.0
    ) {
        operator fun plus(other: CreditBuckets) = CreditBuckets(
            crossMajor = crossMajor + other.crossMajor,
            generalEducation = generalEducation + other.generalEducation,
            mooc = mooc + other.mooc,
            aesthetic = aesthetic + other.aesthetic,
            fourHistories = fourHistories + other.fourHistories,
            englishTaught = englishTaught + other.englishTaught,
            innovation = innovation + other.innovation,
            socialPractice = socialPractice + other.socialPractice
        )
    }

    private fun requirementTargets(
        requirements: List<ShenzhenCreditRequirement>,
        includePracticeInnovation: Boolean
    ): RequirementTargets {
        fun requirement(predicate: (ShenzhenCreditRequirement) -> Boolean) =
            requirements.firstOrNull(predicate)

        val crossMajor = requirement { it.name.contains("跨专业发展") }
        val aesthetic = requirement {
            it.name.contains("美育") || it.name.contains("艺术鉴赏")
        }
        val fourHistories = requirement { it.name.contains("四史") }
        val englishTaught = requirement {
            it.teachingLanguage.isEnglishTeachingLanguage()
        }
        val generalEducation = requirement {
            it.name.contains("文理通识") &&
                it.teachingLanguage.isBlank() &&
                !it.name.contains("美育") &&
                !it.name.contains("四史")
        }
        val practiceInnovationTotal = requirement {
            it.name.contains("创新创业") && it.name.contains("社会实践")
        }
        val innovation = requirement {
            it.name.contains("创新创业") && !it.name.contains("社会实践")
        }
        val socialPractice = requirement {
            it.name.trim() == "社会实践"
        }

        fun target(
            value: ShenzhenCreditRequirement?,
            type: ShenzhenRecommendationCreditType,
            label: String
        ) = value?.let {
            RequirementTarget(type, label, it.requiredCredits, it.completedCredits)
        }

        return RequirementTargets(
            rows = listOfNotNull(
                target(crossMajor, ShenzhenRecommendationCreditType.CROSS_MAJOR, "跨专业"),
                target(
                    generalEducation,
                    ShenzhenRecommendationCreditType.GENERAL_EDUCATION,
                    "文理通识"
                ),
                target(
                    aesthetic,
                    ShenzhenRecommendationCreditType.AESTHETIC_EDUCATION,
                    "美育"
                ),
                target(
                    fourHistories,
                    ShenzhenRecommendationCreditType.FOUR_HISTORIES,
                    "四史"
                ),
                target(
                    englishTaught,
                    ShenzhenRecommendationCreditType.ENGLISH_TAUGHT,
                    "纯英文"
                ),
                target(
                    practiceInnovationTotal.takeIf { includePracticeInnovation },
                    ShenzhenRecommendationCreditType.PRACTICE_INNOVATION_TOTAL,
                    "创新实践合计"
                ),
                target(
                    innovation.takeIf { includePracticeInnovation },
                    ShenzhenRecommendationCreditType.INNOVATION,
                    "创新类"
                ),
                target(
                    socialPractice.takeIf { includePracticeInnovation },
                    ShenzhenRecommendationCreditType.SOCIAL_PRACTICE,
                    "社会实践"
                )
            ),
            officialCountedMoocCredits = generalEducation?.creditedMoocCredits ?: 0.0
        )
    }

    private fun creditBuckets(
        items: List<ShenzhenCourseCatalogItem>,
        recognizedCrossMajorCourseCodes: Set<String>
    ): CreditBuckets = items.fold(CreditBuckets()) { total, item ->
        total + creditBuckets(item, recognizedCrossMajorCourseCodes)
    }

    private fun creditBuckets(
        item: ShenzhenCourseCatalogItem,
        recognizedCrossMajorCourseCodes: Set<String>
    ): CreditBuckets {
        val value = credits(item)
        if (value <= 0.0) return CreditBuckets()
        val pool = item.selectionPoolName.lowercase(Locale.ROOT)
        val descriptors = courseDescriptors(item)
        val isMooc = pool.contains("mooc") || descriptors.contains("慕课")
        val isCrossMajor = descriptors.contains("跨专业") ||
            normalizedCourseCode(item.courseCode) in recognizedCrossMajorCourseCodes
        val isAesthetic = descriptors.contains("美育") || descriptors.contains("艺术鉴赏")
        val isFourHistories = descriptors.contains("四史")
        val isGeneralEducation = isMooc || descriptors.contains("文理通识") ||
            isAesthetic || isFourHistories
        val isEnglishTaught = item.teachingLanguage.isEnglishTeachingLanguage()
        val isSocialPractice = descriptors.contains("社会实践")
        val isInnovation = isInnovationCourseDescriptors(descriptors)

        return CreditBuckets(
            crossMajor = if (isCrossMajor) value else 0.0,
            generalEducation = if (isGeneralEducation && !isMooc) value else 0.0,
            mooc = if (isMooc) value else 0.0,
            aesthetic = if (isGeneralEducation && isAesthetic) value else 0.0,
            fourHistories = if (isGeneralEducation && isFourHistories) value else 0.0,
            englishTaught = if (isGeneralEducation && isEnglishTaught) value else 0.0,
            innovation = if (isInnovation) value else 0.0,
            socialPractice = if (isSocialPractice) value else 0.0
        )
    }

    private fun isPracticeInnovationCourse(item: ShenzhenCourseCatalogItem): Boolean {
        val descriptors = courseDescriptors(item)
        return descriptors.contains("社会实践") || isInnovationCourseDescriptors(descriptors)
    }

    private fun isMinorPlanCourse(item: ShenzhenCourseCatalogItem): Boolean =
        courseDescriptors(item).contains("辅修")

    private fun isInnovationCourseDescriptors(descriptors: String): Boolean = listOf(
        "创新创业",
        "创新研修",
        "创新实验",
        "竞赛指导",
        "创业指导"
    ).any(descriptors::contains)

    private fun courseDescriptors(item: ShenzhenCourseCatalogItem): String = listOf(
        item.selectionPoolName,
        item.courseCategory,
        item.courseNature,
        item.selectionRequirement,
        item.trainingLevel
    ).joinToString(" ").lowercase(Locale.ROOT)

    private fun normalizedCourseCode(value: String): String = value.trim().uppercase(Locale.ROOT)

    private fun creditProgress(
        targets: RequirementTargets,
        buckets: CreditBuckets
    ): List<ShenzhenRecommendationCreditProgress> = targets.rows.map { target ->
        val added = when (target.type) {
            ShenzhenRecommendationCreditType.CROSS_MAJOR -> buckets.crossMajor
            ShenzhenRecommendationCreditType.GENERAL_EDUCATION ->
                buckets.generalEducation + additionalCountedMoocCredits(targets, buckets)
            ShenzhenRecommendationCreditType.AESTHETIC_EDUCATION -> buckets.aesthetic
            ShenzhenRecommendationCreditType.FOUR_HISTORIES -> buckets.fourHistories
            ShenzhenRecommendationCreditType.ENGLISH_TAUGHT -> buckets.englishTaught
            ShenzhenRecommendationCreditType.PRACTICE_INNOVATION_TOTAL ->
                buckets.innovation + buckets.socialPractice
            ShenzhenRecommendationCreditType.INNOVATION -> buckets.innovation
            ShenzhenRecommendationCreditType.SOCIAL_PRACTICE -> buckets.socialPractice
        }
        val projected = target.completedCredits + added
        ShenzhenRecommendationCreditProgress(
            type = target.type,
            label = target.label,
            requiredCredits = rounded(target.requiredCredits),
            projectedCredits = rounded(projected),
            remainingCredits = rounded((target.requiredCredits - projected).coerceAtLeast(0.0))
        )
    }

    private fun additionalCountedMoocCredits(
        targets: RequirementTargets,
        buckets: CreditBuckets
    ): Double = min(
        buckets.mooc,
        (MOOC_GENERAL_EDUCATION_CAP - targets.officialCountedMoocCredits).coerceAtLeast(0.0)
    )

    private fun countedMoocCredits(
        targets: RequirementTargets,
        buckets: CreditBuckets
    ): Double = rounded(
        min(
            MOOC_GENERAL_EDUCATION_CAP,
            targets.officialCountedMoocCredits + buckets.mooc
        )
    )

    private fun String.isEnglishTeachingLanguage(): Boolean {
        val value = trim().lowercase(Locale.ROOT)
        return value.contains("英文") || value.contains("英语") || value.contains("english")
    }

    internal fun parseMeetings(schedule: String): List<ShenzhenCourseMeeting> {
        if (schedule.isBlank()) return emptyList()
        val regex = Regex(
            "((?:\\d+(?:-\\d+)?)(?:[,，]\\d+(?:-\\d+)?)*)周[,，]?星期([一二三四五六日天])第(\\d+)(?:-(\\d+))?节"
        )
        return regex.findAll(schedule).mapNotNull { match ->
            val weekday = weekday(match.groupValues[2]) ?: return@mapNotNull null
            val start = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val end = match.groupValues[4].toIntOrNull() ?: start
            ShenzhenCourseMeeting(
                weekday = weekday,
                startPeriod = start,
                endPeriod = end,
                weeks = expandWeeks(match.groupValues[1])
            )
        }.distinct().toList()
    }

    private fun score(
        credits: Double,
        creditBuckets: CreditBuckets,
        meetings: List<ShenzhenCourseMeeting>,
        conflicts: Int,
        options: ShenzhenRecommendationOptions,
        requirementTargets: RequirementTargets,
        minAdditionalCredits: Double,
        maxAdditionalCredits: Double
    ): Double {
        val gap = when {
            credits < minAdditionalCredits -> minAdditionalCredits - credits
            credits > maxAdditionalCredits -> credits - maxAdditionalCredits
            else -> 0.0
        }
        val over = max(0.0, credits - maxAdditionalCredits)
        val activeDays = meetings.map { it.weekday }.distinct().size
        val early = earlyClassCount(meetings)
        val gaps = scheduleGapCount(meetings)
        val preferencePenalty = when (options.preference) {
            ShenzhenRecommendationPreference.FREE_DAY -> activeDays * 140 + early * 12 + gaps * 10
            ShenzhenRecommendationPreference.FEWER_EARLY_CLASSES -> early * 170 + activeDays * 18 + gaps * 8
            ShenzhenRecommendationPreference.BALANCED -> activeDays * 45 + early * 60 + gaps * 18
        }
        val requirementPenalty = creditProgress(requirementTargets, creditBuckets).sumOf { row ->
            val weight = when (row.type) {
                ShenzhenRecommendationCreditType.AESTHETIC_EDUCATION,
                ShenzhenRecommendationCreditType.FOUR_HISTORIES,
                ShenzhenRecommendationCreditType.ENGLISH_TAUGHT,
                ShenzhenRecommendationCreditType.INNOVATION,
                ShenzhenRecommendationCreditType.SOCIAL_PRACTICE -> 12_000.0
                ShenzhenRecommendationCreditType.CROSS_MAJOR,
                ShenzhenRecommendationCreditType.GENERAL_EDUCATION,
                ShenzhenRecommendationCreditType.PRACTICE_INNOVATION_TOTAL -> 10_000.0
            }
            row.remainingCredits * weight
        }
        return requirementPenalty + gap * 1000 + over * 40 + conflicts * 800 + preferencePenalty
    }

    private fun earlyClassCount(meetings: List<ShenzhenCourseMeeting>): Int = meetings
        .filter { it.startPeriod <= 2 }
        .map { Triple(it.weekday, it.startPeriod, it.endPeriod) }
        .distinct().size

    private fun scheduleGapCount(meetings: List<ShenzhenCourseMeeting>): Int = meetings
        .groupBy { it.weekday }
        .values.sumOf { rows ->
            rows.distinctBy { it.startPeriod to it.endPeriod }
                .sortedBy { it.startPeriod }
                .zipWithNext()
                .count { (left, right) -> right.startPeriod > left.endPeriod + 1 }
        }

    private fun conflicts(
        left: List<ShenzhenCourseMeeting>,
        right: List<ShenzhenCourseMeeting>
    ): Boolean = left.any { a ->
        right.any { b ->
            a.weekday == b.weekday &&
                a.startPeriod <= b.endPeriod && b.startPeriod <= a.endPeriod &&
                (a.weeks.isEmpty() || b.weeks.isEmpty() || a.weeks.any(b.weeks::contains))
        }
    }

    private fun expandWeeks(value: String): Set<Int> = buildSet {
        value.split(',', '，').forEach { part ->
            val bounds = part.split('-').mapNotNull(String::toIntOrNull)
            when (bounds.size) {
                1 -> add(bounds[0])
                2 -> (bounds[0]..bounds[1]).forEach(::add)
            }
        }
    }

    private fun weekday(value: String): Int? = when (value) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天" -> 7
        else -> null
    }

    private fun courseKey(item: ShenzhenCourseCatalogItem): String = item.courseCode
        .trim().lowercase(Locale.ROOT)
        .ifBlank { item.courseName.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "") }

    private fun credits(item: ShenzhenCourseCatalogItem): Double = item.credits.toDoubleOrNull() ?: 0.0

    private fun rounded(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private fun summary(
        preference: ShenzhenRecommendationPreference,
        activeDays: Int,
        early: Int,
        conflicts: Int
    ): String {
        val focus = when (preference) {
            ShenzhenRecommendationPreference.FREE_DAY -> "优先压缩上课日"
            ShenzhenRecommendationPreference.FEWER_EARLY_CLASSES -> "优先减少早八"
            ShenzhenRecommendationPreference.BALANCED -> "均衡课表"
        }
        return "$focus · $activeDays 天有课 · $early 个早八" +
            if (conflicts > 0) " · $conflicts 处冲突" else ""
    }
}
