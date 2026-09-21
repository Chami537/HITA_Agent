package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.utils.CourseNameUtils
import java.util.Calendar
import java.util.SortedSet

/** 一周毫秒数（课次周次推算用）。 */
private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

/**
 * 参与课表刷新比对的一门课。
 *
 * 纯数据类：不持有 Room 实体，保证 [TimetableRefreshMergePolicy] 可在 JVM 单测中直接构造。
 * 源端新课的 [subjectId] 为导入时生成的 UUID；与本地课匹配成功时复用本地 subject id。
 */
data class MergeCourse(
    val subjectId: String,
    val name: String,
    val code: String?,
    val lessons: List<MergeLesson>
) {
    val lessonCount: Int get() = lessons.size
}

/** 一个课次（某一周的一次上课）。 */
data class MergeLesson(
    val name: String,
    val place: String,
    val teacher: String,
    val fromMillis: Long,
    val toMillis: Long,
    val fromNumber: Int,
    val lastNumber: Int,
    /** 第几周（1 起）；无法推断（无开学日期）时为 0。 */
    val weekOfTerm: Int = 0
)

/** 保留本地课的原因。 */
enum class CourseVetoReason {
    /** 课表源整门课没有返回。 */
    MISSING_FROM_SOURCE,

    /** 课表源返回的课次数比本地少。 */
    LESSON_COUNT_REDUCED
}

/** 一门被本地缓存保住的课的展示信息。 */
data class KeptCourse(
    val name: String,
    val reason: CourseVetoReason,
    val localLessonCount: Int,
    val incomingLessonCount: Int
)

/** 槽位变化类型：调整 / 源端新增 / 源端减少。 */
enum class SlotChangeKind { ADJUSTED, ADDED, REMOVED }

/**
 * 一个课次槽位（周几 + 节次）上的变化。
 *
 * [clock]/[weeks]/[place]/[teacher] 为变化后（或新增/减少一侧）的值；
 * 带 Before 后缀的字段仅在确实发生变化时非空，供界面渲染 "原值 → 新值"。
 * [weeks] 是紧凑周次区间标签（如 "1-16"、"1-8、11-16"），无法推断时为空串。
 */
data class LessonSlotChange(
    val kind: SlotChangeKind,
    /** 1=周一 … 7=周日。 */
    val dow: Int,
    val fromNumber: Int,
    val lastNumber: Int,
    val clock: String,
    val weeks: String,
    val place: String,
    val teacher: String,
    val clockBefore: String? = null,
    val weeksBefore: String? = null,
    val placeBefore: String? = null,
    val teacherBefore: String? = null
)

/**
 * 某门课持续被课表源遗漏/削减的记录。
 *
 * 用于两件事：
 * 1. 同一门课反复缺失时统计观察次数与跨度，达到阈值后升级为待用户确认；
 * 2. 课重新在源端出现时清除记录。
 */
data class CourseVetoRecord(
    val courseKey: String,
    val name: String,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val observationCount: Int
)

/** 一门被采纳的课的变更描述，供变更摘要展示。 */
data class CourseChange(
    val name: String,
    /** 课程改名时的旧名称；未改名为 null。 */
    val previousName: String?,
    val timeAdjusted: Boolean,
    val placeAdjusted: Boolean,
    val teacherAdjusted: Boolean,
    val lessonCountDelta: Int,
    /** 逐槽位的完整变化明细。 */
    val slotChanges: List<LessonSlotChange> = emptyList()
)

/** 升级为待用户确认的一门课；[incoming] 为源端数据（整门缺失时为 null，即采纳=删除本地课）。 */
data class CourseDecision(
    val courseKey: String,
    val subjectId: String,
    val name: String,
    val reason: CourseVetoReason,
    val localLessonCount: Int,
    val firstSeenMillis: Long,
    val observationCount: Int,
    val incoming: MergeCourse?
)

/** 一次刷新的合并计划。 */
data class TimetableMergePlan(
    /** 采纳源端的课（含匹配更新的课与源端新增的课）。 */
    val adopt: List<MergeCourse> = emptyList(),
    /** 匹配后被更新的课的变更描述（不含新增）。 */
    val updated: List<CourseChange> = emptyList(),
    /** 源端新增的课。 */
    val added: List<MergeCourse> = emptyList(),
    /** 静默保留本地的课（未达到待确认阈值），含保留原因与课次对比。 */
    val kept: List<KeptCourse> = emptyList(),
    /** 达到待确认阈值、等待用户决策的课。 */
    val decisions: List<CourseDecision> = emptyList(),
    /** 匹配上的本地课中将被源端版本替换的那些（需要先删本地课次）。 */
    val replacedLocal: List<MergeCourse> = emptyList(),
    /** 源端与本地重叠过低：整批挂起，不写库。 */
    val holdBatch: Boolean = false,
    val holdLocalCount: Int = 0,
    val holdIncomingCount: Int = 0,
    val holdMatchedCount: Int = 0,
    /** 整批挂起时源端未返回的本地课：用户选择"保留当前课表"后据此建立 veto 追踪，避免反复询问。 */
    val holdUnmatchedLocal: List<MergeCourse> = emptyList(),
    /** 更新后的 veto 记忆（含新增、累加与清除）。 */
    val vetoes: Map<String, CourseVetoRecord> = emptyMap()
) {
    /**
     * 是否发生了用户可见的真实变化（决定要不要捕获刷新前快照、写入变更摘要）。
     * 匹配后无任何变化的课照常落库，但不产生快照与"已更新"提示。
     */
    val hasChanges: Boolean
        get() = updated.isNotEmpty() || added.isNotEmpty() || decisions.isNotEmpty()
}

/**
 * 课表刷新合并策略。
 *
 * 背景：课表源会临时调课（地点/时间变动，必须及时采纳），也会整门漏课或削减课次
 *（课程并未取消，必须保留本地，不被错误源覆盖）。策略按课程粒度逐门裁决：
 * - 源端课次数 ≥ 本地 → 采纳源端（覆盖时间/地点/教师变更，或新增课次）；
 * - 源端整门缺失或课次数减少 → 保留本地，并记 veto 记忆；
 * - 同一门课持续缺失/削减达到 [DECISION_OBSERVATIONS] 次且跨度 ≥ [DECISION_WINDOW_MILLIS]
 *   → 升级为待用户确认，由用户决定采纳源端还是继续保留；
 * - 源端与本地匹配率过低 → 判定源端整批异常，挂起等用户决策；
 *   已被 veto 追踪的持续缺失不算新异常（走上一行的升级路径）。
 *
 * 纯函数、无 Android 依赖；仓库层负责把 Room 实体映射为 [MergeCourse] 并落库。
 */
object TimetableRefreshMergePolicy {

    /** 源端与本地匹配率低于该比例时判定整批异常。 */
    private const val LOW_OVERLAP_RATIO = 0.5

    /** 持续缺失升级为待确认所需的观察次数。 */
    const val DECISION_OBSERVATIONS = 3

    /** 持续缺失升级为待确认所需的时间跨度。 */
    const val DECISION_WINDOW_MILLIS = 48L * 60 * 60 * 1000

    /**
     * 课程的稳定标识：优先课程代码，否则规范化课程名。
     * veto 记忆与待确认项都用它定位同一门课。
     */
    fun courseKey(name: String, code: String?): String {
        val trimmedCode = code?.trim().orEmpty()
        if (trimmedCode.isNotEmpty()) return "code:$trimmedCode"
        val normalized = CourseNameUtils.normalizeKey(name)
        return if (normalized.isNotEmpty()) "name:$normalized" else "name:${name.trim()}"
    }

    private fun identityKeys(course: MergeCourse): Set<String> =
        EasImportIdentity.subjectLookupKeys(
            code = course.code,
            normalizedName = CourseNameUtils.normalize(course.name) ?: course.name,
            rawName = course.name
        )

    /**
     * 生成本次刷新的合并计划。
     *
     * @param local 本地当前课（仅 EAS 导入课次）
     * @param incoming 课表源返回的课
     * @param vetoes 该学期已有的 veto 记忆
     * @param nowMillis 当前时间（注入以便单测）
     */
    fun plan(
        local: List<MergeCourse>,
        incoming: List<MergeCourse>,
        vetoes: Map<String, CourseVetoRecord>,
        nowMillis: Long
    ): TimetableMergePlan {
        val incomingKeys = incoming.map { identityKeys(it) }
        val usedIncoming = BooleanArray(incoming.size)
        val replacedPairs = mutableListOf<Pair<MergeCourse, MergeCourse>>()
        val unmatchedLocal = mutableListOf<MergeCourse>()

        for (localCourse in local) {
            val keys = identityKeys(localCourse)
            var matchIndex = -1
            for (index in incoming.indices) {
                if (usedIncoming[index]) continue
                if (incomingKeys[index].any { it in keys }) {
                    matchIndex = index
                    break
                }
            }
            if (matchIndex >= 0) {
                usedIncoming[matchIndex] = true
                replacedPairs += localCourse to incoming[matchIndex]
            } else {
                unmatchedLocal += localCourse
            }
        }

        val matchedCount = replacedPairs.size
        val maxCount = maxOf(local.size, incoming.size)
        // 已进入 veto 追踪的缺失是课程级现象（走“持续缺失升级确认”路径），
        // 不算新的整批异常；只有出现未追踪过的新缺失且匹配率过低时才整批挂起。
        val hasUntrackedMissing = unmatchedLocal.any {
            vetoes[courseKey(it.name, it.code)] == null
        }
        if (local.isNotEmpty() && hasUntrackedMissing && matchedCount < maxCount * LOW_OVERLAP_RATIO) {
            return TimetableMergePlan(
                holdBatch = true,
                holdLocalCount = local.size,
                holdIncomingCount = incoming.size,
                holdMatchedCount = matchedCount,
                holdUnmatchedLocal = unmatchedLocal,
                vetoes = vetoes
            )
        }

        val nextVetoes = vetoes.toMutableMap()
        val adopt = mutableListOf<MergeCourse>()
        val updated = mutableListOf<CourseChange>()
        val kept = mutableListOf<KeptCourse>()
        val decisions = mutableListOf<CourseDecision>()
        val replacedLocal = mutableListOf<MergeCourse>()

        for ((localCourse, incomingCourse) in replacedPairs) {
            if (incomingCourse.lessonCount >= localCourse.lessonCount) {
                adopt += incomingCourse
                describeChange(localCourse, incomingCourse)?.let { updated += it }
                replacedLocal += localCourse
                nextVetoes.remove(courseKey(localCourse.name, localCourse.code))
            } else {
                val key = courseKey(localCourse.name, localCourse.code)
                val record = upsertVeto(nextVetoes, key, localCourse.name, nowMillis)
                if (record.isDueForDecision(nowMillis)) {
                    decisions += CourseDecision(
                        courseKey = key,
                        subjectId = localCourse.subjectId,
                        name = localCourse.name,
                        reason = CourseVetoReason.LESSON_COUNT_REDUCED,
                        localLessonCount = localCourse.lessonCount,
                        firstSeenMillis = record.firstSeenMillis,
                        observationCount = record.observationCount,
                        incoming = incomingCourse
                    )
                } else {
                    kept += KeptCourse(
                        name = localCourse.name,
                        reason = CourseVetoReason.LESSON_COUNT_REDUCED,
                        localLessonCount = localCourse.lessonCount,
                        incomingLessonCount = incomingCourse.lessonCount
                    )
                }
            }
        }

        for (localCourse in unmatchedLocal) {
            val key = courseKey(localCourse.name, localCourse.code)
            val record = upsertVeto(nextVetoes, key, localCourse.name, nowMillis)
            if (record.isDueForDecision(nowMillis)) {
                decisions += CourseDecision(
                    courseKey = key,
                    subjectId = localCourse.subjectId,
                    name = localCourse.name,
                    reason = CourseVetoReason.MISSING_FROM_SOURCE,
                    localLessonCount = localCourse.lessonCount,
                    firstSeenMillis = record.firstSeenMillis,
                    observationCount = record.observationCount,
                    incoming = null
                )
            } else {
                kept += KeptCourse(
                    name = localCourse.name,
                    reason = CourseVetoReason.MISSING_FROM_SOURCE,
                    localLessonCount = localCourse.lessonCount,
                    incomingLessonCount = 0
                )
            }
        }

        val added = incoming.filterIndexed { index, _ -> !usedIncoming[index] }
        adopt += added

        return TimetableMergePlan(
            adopt = adopt,
            updated = updated,
            added = added,
            kept = kept,
            decisions = decisions,
            replacedLocal = replacedLocal,
            holdBatch = false,
            vetoes = nextVetoes
        )
    }

    private fun CourseVetoRecord.isDueForDecision(nowMillis: Long): Boolean =
        observationCount >= DECISION_OBSERVATIONS &&
            nowMillis - firstSeenMillis >= DECISION_WINDOW_MILLIS

    private fun upsertVeto(
        vetoes: MutableMap<String, CourseVetoRecord>,
        key: String,
        name: String,
        nowMillis: Long
    ): CourseVetoRecord {
        val previous = vetoes[key]
        val record = if (previous == null) {
            CourseVetoRecord(
                courseKey = key,
                name = name,
                firstSeenMillis = nowMillis,
                lastSeenMillis = nowMillis,
                observationCount = 1
            )
        } else {
            previous.copy(
                name = name,
                lastSeenMillis = nowMillis,
                observationCount = previous.observationCount + 1
            )
        }
        vetoes[key] = record
        return record
    }

    /**
     * 描述一门被采纳课的完整变化；无任何真实变化时返回 null（不产生"已更新"提示）。
     *
     * 按「周几 + 节次」槽位对齐本地与源端：槽位对不齐 = 时间变动（增减槽位），
     * 槽位内比较时钟、上课周次、地点、教师，逐字段给出 原值 → 新值。
     */
    private fun describeChange(local: MergeCourse, incoming: MergeCourse): CourseChange? {
        val localBySlot = local.lessons.groupBy { slotKey(it) }
        val incomingBySlot = incoming.lessons.groupBy { slotKey(it) }
        val slotChanges = mutableListOf<LessonSlotChange>()
        for ((key, localLessons) in localBySlot) {
            val incomingLessons = incomingBySlot[key]
            if (incomingLessons.isNullOrEmpty()) {
                slotChanges += slotChange(localLessons, SlotChangeKind.REMOVED)
            } else {
                adjustedSlotChange(localLessons, incomingLessons)?.let { slotChanges += it }
            }
        }
        for ((key, incomingLessons) in incomingBySlot) {
            if (localBySlot[key].isNullOrEmpty()) {
                slotChanges += slotChange(incomingLessons, SlotChangeKind.ADDED)
            }
        }
        val renamed = incoming.name != local.name
        val lessonCountDelta = incoming.lessonCount - local.lessonCount
        if (slotChanges.isEmpty() && !renamed && lessonCountDelta == 0) return null
        slotChanges.sortWith(compareBy({ it.dow }, { it.fromNumber }, { it.kind.ordinal }))
        return CourseChange(
            name = incoming.name,
            previousName = local.name.takeIf { renamed },
            timeAdjusted = slotChanges.any {
                it.kind != SlotChangeKind.ADJUSTED || it.clockBefore != null || it.weeksBefore != null
            },
            placeAdjusted = slotChanges.any { it.placeBefore != null },
            teacherAdjusted = slotChanges.any { it.teacherBefore != null },
            lessonCountDelta = lessonCountDelta,
            slotChanges = slotChanges
        )
    }

    private fun slotKey(lesson: MergeLesson): String =
        "${dowOf(lesson.fromMillis)}|${lesson.fromNumber}|${lesson.lastNumber}"

    /** 槽位内字段比对；无变化返回 null。 */
    private fun adjustedSlotChange(
        localLessons: List<MergeLesson>,
        incomingLessons: List<MergeLesson>
    ): LessonSlotChange? {
        val localSummary = SlotSummary.of(localLessons)
        val incomingSummary = SlotSummary.of(incomingLessons)
        if (localSummary == incomingSummary) return null
        return LessonSlotChange(
            kind = SlotChangeKind.ADJUSTED,
            dow = incomingSummary.dow,
            fromNumber = incomingSummary.fromNumber,
            lastNumber = incomingSummary.lastNumber,
            clock = incomingSummary.clock,
            weeks = incomingSummary.weeks,
            place = incomingSummary.place,
            teacher = incomingSummary.teacher,
            clockBefore = localSummary.clock.takeIf { it != incomingSummary.clock },
            weeksBefore = localSummary.weeks.takeIf {
                it.isNotEmpty() && it != incomingSummary.weeks
            },
            placeBefore = localSummary.place.takeIf {
                it.isNotEmpty() && it != incomingSummary.place
            },
            teacherBefore = localSummary.teacher.takeIf {
                it.isNotEmpty() && it != incomingSummary.teacher
            }
        )
    }

    private fun slotChange(lessons: List<MergeLesson>, kind: SlotChangeKind): LessonSlotChange {
        val summary = SlotSummary.of(lessons)
        return LessonSlotChange(
            kind = kind,
            dow = summary.dow,
            fromNumber = summary.fromNumber,
            lastNumber = summary.lastNumber,
            clock = summary.clock,
            weeks = summary.weeks,
            place = summary.place,
            teacher = summary.teacher
        )
    }

    /** 一个槽位下多周课次的聚合摘要（按 distinct 值归并，保证比对稳定）。 */
    private data class SlotSummary(
        val dow: Int,
        val fromNumber: Int,
        val lastNumber: Int,
        val clock: String,
        val weeks: String,
        val place: String,
        val teacher: String
    ) {
        companion object {
            fun of(lessons: List<MergeLesson>): SlotSummary {
                val sorted = lessons.sortedBy { it.fromMillis }
                val first = sorted.first()
                return SlotSummary(
                    dow = dowOf(first.fromMillis),
                    fromNumber = first.fromNumber,
                    lastNumber = first.lastNumber,
                    clock = sorted.map { clockLabel(it.fromMillis, it.toMillis) }
                        .distinct().joinToString("、"),
                    weeks = formatWeeks(sorted.map { it.weekOfTerm }.toSortedSet()),
                    place = sorted.map { it.place }.filter { it.isNotEmpty() }
                        .distinct().joinToString("、"),
                    teacher = sorted.map { it.teacher }.filter { it.isNotEmpty() }
                        .distinct().joinToString("、")
                )
            }
        }
    }

    /** 周一=1 … 周日=7。 */
    private fun dowOf(millis: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        calendar.firstDayOfWeek = Calendar.MONDAY
        return if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            7
        } else {
            calendar.get(Calendar.DAY_OF_WEEK) - 1
        }
    }

    private fun clockLabel(fromMillis: Long, toMillis: Long): String =
        "${timeLabel(fromMillis)}-${timeLabel(toMillis)}"

    private fun timeLabel(millis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return "%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    /** 周次集合 → 紧凑区间标签（如 "1-16"、"1-8、11-16"）；无法推断时返回空串。 */
    private fun formatWeeks(weeks: SortedSet<Int>): String {
        val known = weeks.filter { it > 0 }
        if (known.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var start = known.first()
        var previous = start
        known.drop(1).forEach { week ->
            if (week != previous + 1) {
                ranges += rangeLabel(start, previous)
                start = week
            }
            previous = week
        }
        ranges += rangeLabel(start, previous)
        return ranges.joinToString("、")
    }

    private fun rangeLabel(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"
}

/** Room 事件 → 比对用课次。 */
internal fun EventItem.toMergeLesson(termStartMillis: Long): MergeLesson {
    val weekOfTerm = if (termStartMillis > 0) {
        (((from.time - termStartMillis) / WEEK_MILLIS).toInt() + 1).coerceAtLeast(1)
    } else {
        0
    }
    return MergeLesson(
        name = name,
        place = place.orEmpty().trim(),
        teacher = teacher.orEmpty().trim(),
        fromMillis = from.time,
        toMillis = to.time,
        fromNumber = fromNumber,
        lastNumber = lastNumber,
        weekOfTerm = weekOfTerm
    )
}

/**
 * 课表导入模式。
 *
 * - [REPLACE]：手动导入，课表源全量替换当前课表；
 * - [MERGE]：打开应用时的自动刷新，按 [TimetableRefreshMergePolicy] 逐门合并，
 *   课表源漏课/削减课次时保留本地。
 */
enum class TimetableImportMode { REPLACE, MERGE }
