package cn.limpu.hita.data.repository

import androidx.lifecycle.asFlow
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.TimeInDay
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity
import cn.limpu.hita.feature.timetablecompare.*
import cn.limpu.hita.feature.timetableshare.protocol.ShareLimits
import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import cn.limpu.hita.feature.timetableshare.protocol.SharedTimetable
import cn.limpu.hita.feature.timetableshare.protocol.TimetableShareCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.Callable
import javax.inject.Inject

data class ComparisonFriendSource(
    val row: FriendTimetableEntity,
    val snapshot: SharedTimetable,
    val schedule: ComparisonSchedule,
    val excludedCourseIndexes: Set<Int> = emptySet(),
)

data class ComparisonSource(
    val ownTimetable: Timetable,
    val ownCourses: List<EventItem>,
    val friendRow: FriendTimetableEntity,
    val friendSnapshot: SharedTimetable,
    val own: ComparisonSchedule,
    val friend: ComparisonSchedule,
    val ownTimetables: List<Timetable> = emptyList(),
    val friends: List<ComparisonFriendSource> = emptyList(),
) {
    fun participatingTimetables(): List<Timetable> = ownTimetables.ifEmpty { listOf(ownTimetable) }
    fun participatingFriends(): List<ComparisonFriendSource> =
        friends.ifEmpty { listOf(ComparisonFriendSource(friendRow, friendSnapshot, friend)) }
}

sealed interface ComparisonSourceState {
    data class Ready(val source: ComparisonSource) : ComparisonSourceState
    data object MissingOwn : ComparisonSourceState
    data object MissingFriend : ComparisonSourceState
    data object Invalid : ComparisonSourceState
    data object Incompatible : ComparisonSourceState
}

/** Observations invalidate a complete, read-only transaction; their partial payloads are never mixed. */
class TimetableComparisonRepository @Inject constructor(private val db: AppDatabase) {
    /** 配置面板的我的课表与组合列表（组合在一级界面整体出现）。 */
    fun observePersonalTimetables() = db.timetableDao().getTimetables()
    fun observeGroups() = db.timetableGroupDao().getGroups()
    private val codec = TimetableShareCodec()

    fun observe(personalId: String, friendId: String): Flow<ComparisonSourceState> =
        observe(listOf(personalId), listOf(friendId), emptyMap(), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(
        personalIds: List<String>,
        friendIds: List<String>,
        excludedOwnCourses: Map<String, Set<String>>,
        excludedFriendCourses: Map<String, Set<Int>>,
        personalGroupIds: List<String> = emptyList(),
        excludedFriendCourseKeys: Map<String, Set<String>> = emptyMap(),
    ): Flow<ComparisonSourceState> {
        return combine(
            db.timetableDao().getTimetables().asFlow(),
            db.timetableGroupDao().getGroups().asFlow(),
            db.friendTimetableDao().observeAll().asFlow(),
        ) { tables, _, _ ->
            (personalIds + tables.filter { it.groupId in personalGroupIds }.map { it.id }).distinct()
        }.flatMapLatest { ids ->
            val events = ids.filter { it.isNotBlank() }.map { db.eventItemDao().observeClassesFlow(it) }
            val invalidations = if (events.isEmpty()) flowOf(Unit) else combine(events) { Unit }
            invalidations.map {
                try {
                    db.runInTransaction(Callable {
                        readSource(personalIds, friendIds, excludedOwnCourses, excludedFriendCourses,
                            personalGroupIds, excludedFriendCourseKeys)
                    })
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    ComparisonSourceState.Invalid
                }
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(ComparisonSourceState.Invalid)
        }.flowOn(Dispatchers.IO)
    }

    fun ownCourseChoices(timetableId: String): Flow<List<ComparisonCourseChoice>> =
        db.eventItemDao().observeClassesFlow(timetableId).map { courses ->
            courses.groupBy { ComparisonSelection.ownCourseKey(it.subjectId, it.name) }
                .map { (key, events) -> ComparisonCourseChoice(key, events.first().name.ifBlank { key }) }
                .sortedBy { it.label }
        }.flowOn(Dispatchers.IO)

    private fun readSource(
        personalIds: List<String>,
        friendIds: List<String>,
        excludedOwnCourses: Map<String, Set<String>>,
        excludedFriendCourses: Map<String, Set<Int>>,
        personalGroupIds: List<String>,
        excludedFriendCourseKeys: Map<String, Set<String>>,
    ): ComparisonSourceState {
        val groupMembers = personalGroupIds.distinct().flatMap { groupId ->
            if (db.timetableGroupDao().getGroupByIdSync(groupId) == null) return ComparisonSourceState.MissingOwn
            db.timetableGroupDao().getMembersSync(groupId).also {
                if (it.isEmpty()) return ComparisonSourceState.MissingOwn
            }
        }
        val tables = (personalIds.filter { it.isNotBlank() }.distinct().map { id ->
            db.timetableDao().getTimetableByIdSync(id) ?: return ComparisonSourceState.MissingOwn
        } + groupMembers).distinctBy { it.id }
        if (tables.isEmpty()) return ComparisonSourceState.MissingOwn
        val anchor = periodsOf(tables.first())
        if (tables.any { table ->
                table.startTime.time >= table.endTime.time || table.scheduleStructure.size > ShareLimits.PERIODS ||
                    table.scheduleStructure.any { !validTime(it.from) || !validTime(it.to) } ||
                    !PeriodStructure.isValid(periodsOf(table))
            }) return ComparisonSourceState.Invalid
        if (tables.any { periodsOf(it) != anchor }) return ComparisonSourceState.Incompatible
        var courseCount = 0
        val ownCourses = tables.flatMap { table ->
            val excluded = excludedOwnCourses[table.id].orEmpty()
            val courses = db.eventItemDao().getEventsOfTimetableSync(table.id).filter { event ->
                event.timetableId == table.id && event.type == EventItem.TYPE.CLASS
            }
            courseCount += courses.size
            if (courseCount > ShareLimits.OCCURRENCES) return ComparisonSourceState.Invalid
            if (courses.any { it.from.time >= it.to.time }) return ComparisonSourceState.Invalid
            courses.filter { ComparisonSelection.ownCourseKey(it.subjectId, it.name) !in excluded }
        }
        val validStart = tables.maxOf { it.startTime.time }
        val validEnd = tables.minOf { it.endTime.time }
        val own = ComparisonSchedule(
            if (validStart < validEnd) TimeSpan(validStart, validEnd) else TimeSpan(0, 1),
            anchor,
            ownCourses.map { TimeSpan(it.from.time, it.to.time) },
        )
        val friendSides = friendIds.filter { it.isNotBlank() }.distinct().map { friendId ->
            val row = db.friendTimetableDao().getSync(friendId) ?: return ComparisonSourceState.MissingFriend
            val snapshot = try { codec.decodeJson(row.snapshotJson) } catch (error: Exception) {
                if (error is CancellationException) throw error
                return ComparisonSourceState.Invalid
            }
            if (!row.matchesSnapshot(snapshot, codec)) return ComparisonSourceState.Invalid
            val periods = snapshot.periods.map { ComparisonPeriod(it.startMinute, it.endMinute) }
            if (!PeriodStructure.isValid(periods)) return ComparisonSourceState.Invalid
            if (periods != anchor) return ComparisonSourceState.Incompatible
            val excludedIndexes = ComparisonSelection.friendExcludedIndexes(snapshot.courses,
                excludedFriendCourseKeys[friendId], excludedFriendCourses[friendId].orEmpty())
            val spans = ComparisonSelection.friendSpans(
                snapshot.mode,
                snapshot.occurrences,
                excludedIndexes,
            )
            if (spans.any { it.startMillis >= it.endMillis }) return ComparisonSourceState.Invalid
            ComparisonFriendSource(
                row,
                snapshot,
                ComparisonSchedule(TimeSpan(snapshot.term.startMillis, snapshot.term.endMillis), periods, spans),
                excludedIndexes,
            )
        }
        if (friendSides.isEmpty()) return ComparisonSourceState.MissingFriend
        val first = friendSides.first()
        return ComparisonSourceState.Ready(
            ComparisonSource(
                tables.first(), ownCourses, first.row, first.snapshot, own, first.schedule, tables, friendSides,
            )
        )
    }

    private fun periodsOf(table: Timetable): List<ComparisonPeriod> =
        table.scheduleStructure.map {
            ComparisonPeriod(it.from.hour * 60 + it.from.minute, it.to.hour * 60 + it.to.minute)
        }

    private fun validTime(time: TimeInDay): Boolean =
        (time.hour in 0..23 && time.minute in 0..59) || (time.hour == 24 && time.minute == 0)
}
