package cn.limpu.hita.ui.main.timetable.compare

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.feature.timetablecompare.*
import cn.limpu.hita.feature.timetableshare.protocol.*
import cn.limpu.hita.utils.ColorTools
import java.sql.Timestamp
import java.time.*
import java.time.temporal.TemporalAdjusters

val CAMPUS_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** The host keeps its ordinary system-zone anchor. Convert its displayed date, not its instant. */
fun comparisonMonday(anchor: Long, sourceZone: ZoneId): Long = Instant.ofEpochMilli(anchor).atZone(sourceZone)
    .toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    .atStartOfDay(CAMPUS_ZONE).toInstant().toEpochMilli()

data class ComparisonCourseSegment(val display: EventItem, val original: EventItem)
/** Only display copies are split. Raw records remain the time authority and are never mutated. */
fun projectComparisonCourses(courses: List<EventItem>, monday: Long,
    colors: Map<String, Int> = emptyMap()): List<ComparisonCourseSegment> {
    val first = Instant.ofEpochMilli(monday).atZone(CAMPUS_ZONE).toLocalDate()
    val end = first.plusWeeks(1).atStartOfDay(CAMPUS_ZONE).toInstant().toEpochMilli()
    return courses.filter { it.type == EventItem.TYPE.CLASS && it.from.time < end && it.to.time > monday }
        .flatMap { original ->
            val result = mutableListOf<ComparisonCourseSegment>()
            var start = maxOf(monday, original.from.time)
            val limit = minOf(end, original.to.time)
            while (start < limit) {
                val date = Instant.ofEpochMilli(start).atZone(CAMPUS_ZONE).toLocalDate()
                val stop = minOf(limit, date.plusDays(1).atStartOfDay(CAMPUS_ZONE).toInstant().toEpochMilli())
                val copy = EventItem().apply {
                    id = "comparison:${original.id}:$date"
                    type = original.type; source = original.source; name = original.name
                    place = original.place; teacher = original.teacher; subjectId = original.subjectId
                    timetableId = original.timetableId; from = Timestamp(start); to = Timestamp(stop)
                    fromNumber = original.fromNumber; lastNumber = original.lastNumber
                    createdAt = original.createdAt
                    color = colors[original.subjectId]?.takeIf { it != 0 }
                        ?: original.color.takeIf { it != 0 } ?: ColorTools.colorForName(original.name)
                }
                result += ComparisonCourseSegment(copy, original)
                start = stop
            }
            result
        }
}

data class ComparisonCourseDetail(val span: TimeSpan, val name: String?, val place: String?)
private fun intersects(a: TimeSpan, b: TimeSpan) = a.startMillis < b.endMillis && a.endMillis > b.startMillis

/** 每位好友只列出仍参与对比的安排;忙闲不解析名称。 */
fun comparisonFriendSlotDetails(
    slot: TimeSpan,
    side: cn.limpu.hita.data.repository.ComparisonFriendSource,
    excludedIndexes: Set<Int>,
): List<ComparisonCourseDetail> {
    val friend = side.snapshot
    return friend.occurrences.filter { occurrence ->
        intersects(slot, TimeSpan(occurrence.startMillis, occurrence.endMillis)) &&
            (friend.mode != ShareMode.FULL || occurrence.courseIndex == null || occurrence.courseIndex !in excludedIndexes)
    }.map { occurrence ->
        val span = TimeSpan(occurrence.startMillis, occurrence.endMillis)
        if (friend.mode == ShareMode.BUSY) ComparisonCourseDetail(span, null, null)
        else ComparisonCourseDetail(span, occurrence.courseIndex?.let(friend.courses::getOrNull), occurrence.place)
    }
}

/** A selected merged block may become invalid while sources refresh; never fabricate FREE. */
internal fun comparisonDetailSlot(result: ComparisonResult, span: TimeSpan): ComparedPeriod {
    val slots = result.days.flatMap { it.slots }.filter { intersects(it.span, span) }
    val status = when {
        slots.isEmpty() || slots.any { it.status == PeriodStatus.UNKNOWN } -> PeriodStatus.UNKNOWN
        slots.all { it.status == PeriodStatus.FREE } -> PeriodStatus.FREE
        else -> slots.first { it.status != PeriodStatus.FREE }.status
    }
    return ComparedPeriod(-1, span, status)
}
