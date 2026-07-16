package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseRecommendationResult
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationPreference
import cn.limpu.hita.data.model.eas.ShenzhenRecommendedPlan
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class ShenzhenCourseMeeting(
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>
)

internal object ShenzhenCourseRecommendationEngine {
    private const val BEAM_SIZE = 240

    fun recommend(
        selected: List<ShenzhenCourseCatalogItem>,
        candidates: List<ShenzhenCourseCatalogItem>,
        options: ShenzhenRecommendationOptions
    ): ShenzhenCourseRecommendationResult {
        val selectedMeetings = selected.flatMap { parseMeetings(it.schedule) }
        val selectedCredits = selected.sumOf(::credits)
        val selectedKeys = selected.mapTo(mutableSetOf(), ::courseKey)
        val prepared = candidates.distinctBy { it.taskId.ifBlank { it.id } }
            .filter { courseKey(it) !in selectedKeys && credits(it) > 0.0 }

        val fullCount = prepared.count { (it.remainingSeats ?: 1) <= 0 }
        val afterFull = if (options.excludeFull) {
            prepared.filter { (it.remainingSeats ?: 1) > 0 }
        } else prepared
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
                    if (nextCredits > options.targetAdditionalCredits + 6.0) return@forEach
                    expanded += State(
                        courses = state.courses + candidate,
                        credits = nextCredits,
                        meetings = state.meetings + candidateMeetings,
                        conflicts = state.conflicts + if (overlaps) 1 else 0
                    )
                }
            }
            states = expanded
                .distinctBy { state -> state.courses.map { it.taskId.ifBlank { it.id } }.sorted() }
                .sortedBy { score(it.credits, it.meetings, it.conflicts, options) }
                .take(BEAM_SIZE)
        }

        val plans = states.asSequence()
            .filter { it.courses.isNotEmpty() }
            .sortedBy { score(it.credits, it.meetings, it.conflicts, options) }
            .distinctBy { state ->
                state.courses.map { it.taskId.ifBlank { it.id } }.sorted()
            }
            .take(3)
            .map { state ->
                val activeDays = state.meetings.map { it.weekday }.distinct().size
                val early = earlyClassCount(state.meetings)
                ShenzhenRecommendedPlan(
                    courses = state.courses.sortedBy { it.courseName },
                    additionalCredits = rounded(state.credits),
                    totalCredits = rounded(selectedCredits + state.credits),
                    activeWeekdays = activeDays,
                    earlyClassCount = early,
                    conflictCount = state.conflicts,
                    summary = summary(options.preference, activeDays, early, state.conflicts)
                )
            }
            .toList()

        return ShenzhenCourseRecommendationResult(
            selectedCredits = rounded(selectedCredits),
            targetAdditionalCredits = options.targetAdditionalCredits,
            candidateCount = usable.size,
            excludedFullCount = if (options.excludeFull) fullCount else 0,
            excludedConflictCount = if (options.excludeConflicts) conflictCount else 0,
            plans = plans
        )
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
        meetings: List<ShenzhenCourseMeeting>,
        conflicts: Int,
        options: ShenzhenRecommendationOptions
    ): Double {
        val gap = abs(options.targetAdditionalCredits - credits)
        val over = max(0.0, credits - options.targetAdditionalCredits)
        val activeDays = meetings.map { it.weekday }.distinct().size
        val early = earlyClassCount(meetings)
        val gaps = scheduleGapCount(meetings)
        val preferencePenalty = when (options.preference) {
            ShenzhenRecommendationPreference.FREE_DAY -> activeDays * 140 + early * 12 + gaps * 10
            ShenzhenRecommendationPreference.FEWER_EARLY_CLASSES -> early * 170 + activeDays * 18 + gaps * 8
            ShenzhenRecommendationPreference.BALANCED -> activeDays * 45 + early * 60 + gaps * 18
        }
        return gap * 1000 + over * 40 + conflicts * 800 + preferencePenalty
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
