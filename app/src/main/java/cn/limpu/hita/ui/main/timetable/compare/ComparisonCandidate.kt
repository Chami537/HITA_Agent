package cn.limpu.hita.ui.main.timetable.compare

import cn.limpu.hita.data.model.timetable.TimeInDay
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity
import cn.limpu.hita.feature.timetablecompare.ComparisonPeriod
import cn.limpu.hita.feature.timetablecompare.PeriodStructure
import cn.limpu.hita.feature.timetableshare.protocol.ShareLimits
import cn.limpu.hita.feature.timetableshare.protocol.TimetableShareCodec
import cn.limpu.hita.data.repository.matchesSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Value snapshot produced BEFORE Compose equality filtering; Timetable.equals ignores structure/term dates. */
data class ComparisonPersonalChoice(val id: String, val name: String, val startMillis: Long,
    val endMillis: Long, val periods: List<ComparisonPeriod>, val valid: Boolean)

/** 配置面板的我的课表条目：独立课表单独成行；组合课表整体一行，成员不单独出现。 */
sealed interface ComparisonPersonalItem {
    data class Single(val choice: ComparisonPersonalChoice) : ComparisonPersonalItem
    data class Group(val groupId: String, val name: String,
        val members: List<ComparisonPersonalChoice>) : ComparisonPersonalItem
}
fun comparisonPersonalChoice(table: Timetable): ComparisonPersonalChoice {
    val periods = table.scheduleStructure.map {
        ComparisonPeriod(it.from.hour * 60 + it.from.minute, it.to.hour * 60 + it.to.minute)
    }
    return ComparisonPersonalChoice(table.id, table.name.orEmpty(), table.startTime.time, table.endTime.time,
        periods, table.startTime.time < table.endTime.time && periods.size <= ShareLimits.PERIODS &&
            table.scheduleStructure.all { validComparisonTime(it.from) && validComparisonTime(it.to) } &&
            PeriodStructure.isValid(periods))
}
data class ComparisonCandidate(val personal: ComparisonPersonalChoice, val friend: FriendTimetableEntity,
    val ownPeriods: List<ComparisonPeriod>, val friendPeriods: List<ComparisonPeriod>, val valid: Boolean)

/** Decode only the selected persisted snapshot; never decode JSON in a Composable/main-thread callback. */
suspend fun readComparisonCandidate(personal: ComparisonPersonalChoice, friend: FriendTimetableEntity): ComparisonCandidate =
    withContext(Dispatchers.Default) {
        try {
            val codec = TimetableShareCodec()
            val snapshot = codec.decodeJson(friend.snapshotJson)
            ComparisonCandidate(personal, friend, personal.periods,
                snapshot.periods.map { ComparisonPeriod(it.startMinute, it.endMinute) },
                friend.matchesSnapshot(snapshot, codec) && personal.valid)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error
        } catch (error: Exception) { ComparisonCandidate(personal, friend, personal.periods, emptyList(), false) }
    }
private fun validComparisonTime(time: TimeInDay) =
    (time.hour in 0..23 && time.minute in 0..59) || (time.hour == 24 && time.minute == 0)

data class FriendCourseMenu(
    val periods: List<ComparisonPeriod>,
    val courses: List<cn.limpu.hita.feature.timetablecompare.ComparisonCourseChoice>,
    val busy: Boolean,
    val valid: Boolean,
)

suspend fun readFriendCourseMenu(friend: FriendTimetableEntity): FriendCourseMenu = withContext(Dispatchers.Default) {
    try {
        val codec = TimetableShareCodec()
        val snapshot = codec.decodeJson(friend.snapshotJson)
        if (!friend.matchesSnapshot(snapshot, codec)) {
            return@withContext FriendCourseMenu(emptyList(), emptyList(), busy = false, valid = false)
        }
        val periods = snapshot.periods.map { ComparisonPeriod(it.startMinute, it.endMinute) }
        val courseKeys = cn.limpu.hita.feature.timetablecompare.ComparisonSelection.friendCourseKeys(snapshot.courses)
        val courses = if (snapshot.mode == cn.limpu.hita.feature.timetableshare.protocol.ShareMode.FULL) {
            snapshot.courses.mapIndexed { index, name ->
                cn.limpu.hita.feature.timetablecompare.ComparisonCourseChoice(courseKeys[index], name)
            }
        } else {
            emptyList()
        }
        FriendCourseMenu(periods, courses, snapshot.mode != cn.limpu.hita.feature.timetableshare.protocol.ShareMode.FULL,
            PeriodStructure.isValid(periods))
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        FriendCourseMenu(emptyList(), emptyList(), busy = false, valid = false)
    }
}
