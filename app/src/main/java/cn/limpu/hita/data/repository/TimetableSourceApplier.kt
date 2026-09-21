package cn.limpu.hita.data.repository

import android.app.Application
import androidx.annotation.WorkerThread
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.eas.EASToken
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.TermSubject
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.source.preference.EasPreferenceSource
import cn.limpu.hita.utils.ColorTools
import cn.limpu.hita.utils.CourseNameUtils
import java.sql.Timestamp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课表源数据的用户决策应用层。
 *
 * 职责边界：
 * - [EASRepository] 负责"拉取 → 生成 → 按 [TimetableRefreshMergePolicy] 合并"的自动刷新链路；
 * - 本类负责"用户对挂起数据做出决策后的落库"：采用某门课的源端版本、删除某门课、
 *   或整批采用挂起的源端数据。写库前统一捕获 [TimetableSnapshotKind.BEFORE_USER_ADOPT] 快照兜底。
 *
 * 与自动刷新解耦：这里只处理用户在课表变更详情里显式确认过的数据。
 */
@Singleton
class TimetableSourceApplier @Inject constructor(
    application: Application,
    private val easPreferenceSource: EasPreferenceSource,
    private val changeStore: TimetableChangeStore
) {
    private val appContext = application.applicationContext
    private val eventItemDao = AppDatabase.getDatabase(application).eventItemDao()
    private val timetableDao = AppDatabase.getDatabase(application).timetableDao()
    private val subjectDao = AppDatabase.getDatabase(application).subjectDao()
    private val snapshotStore = TimetableSnapshotStore(appContext)

    /** 采用某门待确认课程的源端版本（源端整门缺失时即删除本地该课）。 */
    fun adoptCourse(termId: String, courseKey: String) {
        // 先落库再消费：落库失败时待确认项仍在，用户可以重试
        val item = changeStore.currentState().decisions.firstOrNull {
            it.termId == termId && it.courseKey == courseKey
        } ?: error("待确认项不存在或已处理")
        applyCourseDecision(item)
        changeStore.consumeDecision(termId, courseKey)
    }

    /** 继续保留某门待确认课程：保留本地，并重置观察窗口。 */
    fun dismissCourse(termId: String, courseKey: String) {
        changeStore.consumeDecision(termId, courseKey)
        changeStore.resetVeto(termId, courseKey)
    }

    /** 采用整批挂起的源端数据：全量替换当前课表。 */
    fun adoptHeldBatch() {
        val batch = changeStore.currentState().heldBatch ?: error("没有待处理的课表源数据")
        applyHeldBatch(batch)
        changeStore.consumeHeldBatch()
    }

    /** 保留当前课表：丢弃整批挂起的源端数据，并为缺失课建立 veto 追踪避免反复询问。 */
    fun dismissHeldBatch() {
        val batch = changeStore.consumeHeldBatch() ?: return
        changeStore.trackHeldBatchMissing(
            batch.termId,
            batch.unmatchedLocal,
            System.currentTimeMillis()
        )
    }

    @WorkerThread
    private fun applyCourseDecision(item: TimetableDecisionItem) {
        val timetable = timetableDao.getTimetableByIdSync(item.timetableId)
            ?: error("课表不存在")
        captureSnapshot(timetable, item.termId)

        val incoming = item.incoming
        if (incoming == null) {
            // 用户确认该课真实取消：删除本地课程与课次
            eventItemDao.deleteEventsFromSubjectsSync(listOf(item.subjectId))
            subjectDao.deleteSubjectsInIdsSync(listOf(item.subjectId))
            return
        }
        val localSubject = subjectDao.getSubjectsSync(timetable.id)
            .firstOrNull { it.id == item.subjectId }
        val subject = localSubject ?: TermSubject().apply {
            id = incoming.subjectId
            timetableId = timetable.id
            color = ColorTools.colorForName(CourseNameUtils.normalize(incoming.name) ?: incoming.name)
        }
        subject.name = incoming.name
        subject.timetableId = timetable.id
        if (!incoming.code.isNullOrBlank()) subject.code = incoming.code
        eventItemDao.deleteEventsFromSubjectsSync(listOf(subject.id))
        subjectDao.saveSubjectsSync(listOf(subject))
        eventItemDao.saveEvents(
            incoming.lessons.map { it.toEventItem(subject.id, timetable.id) }
        )
        timetable.endTime = Timestamp(
            maxOf(timetable.endTime.time, incoming.lessons.maxOfOrNull { it.toMillis } ?: 0L)
        )
        timetableDao.saveTimetableSync(timetable)
    }

    @WorkerThread
    private fun applyHeldBatch(batch: TimetableHeldBatch) {
        val timetable = timetableDao.getTimetableByIdSync(batch.timetableId)
            ?: error("课表不存在")
        captureSnapshot(timetable, batch.termId)

        val localSubjects = subjectDao.getSubjectsSync(timetable.id).associateBy { it.id }
        val subjects = batch.courses.map { course ->
            val existing = localSubjects[course.subjectId]
            if (existing != null) {
                existing.name = course.name
                if (!course.code.isNullOrBlank()) existing.code = course.code
                existing
            } else {
                TermSubject().apply {
                    id = course.subjectId
                    name = course.name
                    timetableId = timetable.id
                    code = course.code
                    color = ColorTools.colorForName(
                        CourseNameUtils.normalize(course.name) ?: course.name
                    )
                }
            }
        }
        val events = batch.courses.flatMap { course ->
            course.lessons.map { it.toEventItem(course.subjectId, timetable.id) }
        }
        eventItemDao.deleteCourseFromTimetable(timetable.id)
        subjectDao.saveSubjectsSync(subjects)
        eventItemDao.saveEvents(events)
        timetable.name = batch.timetableName
        timetable.code = batch.timetableCode
        timetable.startTime = Timestamp(batch.startMillis)
        timetable.scheduleStructure = batch.schedule
        timetable.endTime = Timestamp(events.maxOfOrNull { it.to.time } ?: batch.startMillis)
        timetableDao.saveTimetableSync(timetable)
        // 本地已被源端整体替换：该学期的 veto 记忆与待确认项作废
        changeStore.clearTerm(batch.termId)
    }

    private fun captureSnapshot(timetable: Timetable, termId: String) {
        val token: EASToken = easPreferenceSource.getEasToken()
        snapshotStore.capture(
            FollowedTeachingSectionStore.ownerKey(token),
            termOf(termId),
            timetable,
            TimetableSnapshotKind.BEFORE_USER_ADOPT
        )
    }

    /** termId 形如 "yearCode-termCode"（见 [TermItem.id]），快照捕获需要 TermItem。 */
    private fun termOf(termId: String): TermItem {
        val parts = termId.split('-')
        val yearCode = parts.take(2).joinToString("-")
        return TermItem(
            yearCode = yearCode,
            yearName = yearCode,
            termCode = parts.lastOrNull().orEmpty(),
            termName = ""
        )
    }

    private fun MergeLesson.toEventItem(subjectId: String, timetableId: String): EventItem {
        return EventItem().apply {
            type = EventItem.TYPE.CLASS
            source = EventItem.SOURCE_EAS_IMPORT
            name = this@toEventItem.name
            place = this@toEventItem.place
            teacher = this@toEventItem.teacher
            this.subjectId = subjectId
            this.timetableId = timetableId
            from = Timestamp(this@toEventItem.fromMillis)
            to = Timestamp(this@toEventItem.toMillis)
            fromNumber = this@toEventItem.fromNumber
            lastNumber = this@toEventItem.lastNumber
        }
    }
}
