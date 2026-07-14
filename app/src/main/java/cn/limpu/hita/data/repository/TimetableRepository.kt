package cn.limpu.hita.data.repository

import android.app.Application
import androidx.annotation.WorkerThread
import javax.inject.Inject
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.limpu.component.data.DataState
import com.limpu.component.data.MTransformations
import cn.limpu.hita.R
import cn.limpu.hita.utils.LogUtils
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.SubjectColor
import cn.limpu.hita.data.model.timetable.TimePeriodInDay
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.source.preference.EasPreferenceSource
import cn.limpu.hita.ui.main.timetable.TimetableFragment.Companion.WEEK_MILLS
import cn.limpu.hita.utils.TimeTools
import java.lang.NumberFormatException
import java.util.*
import java.util.concurrent.Executors

import net.fortuna.ical4j.model.component.VAlarm

import net.fortuna.ical4j.model.component.VEvent

import net.fortuna.ical4j.data.CalendarOutputter

import net.fortuna.ical4j.model.Dur

import net.fortuna.ical4j.util.UidGenerator

import net.fortuna.ical4j.model.DateTime

import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.data.CalendarBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream


class TimetableRepository @Inject constructor(val application: Application) {
    companion object {
        private const val EXAM_REMINDER_DAYS = 30
    }

    private val manualEventFallbackColor by lazy {
        ContextCompat.getColor(application, R.color.subject8)
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val eventItemDao = AppDatabase.getDatabase(application).eventItemDao()
    private val timetableDao = AppDatabase.getDatabase(application).timetableDao()
    private val subjectDao = AppDatabase.getDatabase(application).subjectDao()
    private val easPreferenceSource by lazy {
        EasPreferenceSource(application.applicationContext)
    }

    /**
     * 获取[from,to)内的事件
     */
    fun getEventsDuring(from: Long, to: Long): LiveData<List<EventItem>> {
        return eventItemDao.getEventsDuring(from, to)
    }

    /**
     * 获取[from,...)内的至多limit个事件
     */
    fun getEventsAfter(from: Long,limit:Int): LiveData<List<EventItem>> {
        return eventItemDao.getEventsAfter(from,limit)
    }

    fun getUpcomingExamsWithinReminderWindow(from: Long): LiveData<List<EventItem>> {
        return eventItemDao.getExamsDuring(from, from + EXAM_REMINDER_DAYS * 24L * 60L * 60L * 1000L)
    }

    @WorkerThread
    fun getUpcomingExamsWithinReminderWindowSync(from: Long): List<EventItem> {
        return eventItemDao.getExamsDuringSync(
            from,
            from + EXAM_REMINDER_DAYS * 24L * 60L * 60L * 1000L
        )
    }


    /**
     * 获取今日事件
     */
    @WorkerThread
    fun getTodayEventsSync(): List<EventItem> {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY,0)
        now.set(Calendar.MINUTE,0)
        val from = now.timeInMillis
        now.add(Calendar.DATE,1)
        val to = now.timeInMillis
        return eventItemDao.getEventsDuringSync(from, to)
            .sortedBy { it.from.time }
    }

    @WorkerThread
    fun getUpcomingEventsSync(from: Long, to: Long): List<EventItem> {
        return eventItemDao.getEventsDuringSync(from, to)
            .sortedBy { it.from.time }
    }

    @WorkerThread
    fun getTodayEventsWithUpcomingExamSync(): List<EventItem> {
        return appendUpcomingExam(getTodayEventsSync(), System.currentTimeMillis())
    }

    fun appendUpcomingExam(todayEvents: List<EventItem>, now: Long): List<EventItem> {
        val exams = getUpcomingExamsWithinReminderWindowSync(now)
        if (exams.isEmpty()) return todayEvents
        val todayEventIds = todayEvents.mapTo(mutableSetOf()) { it.id }
        return (todayEvents + exams.filter { it.id !in todayEventIds }).sortedBy { it.from.time }
    }
    /**
     * 获取[from,to)内的事件，包含颜色
     *
     * 这是主课表显示入口，底层 DAO 会返回所有课表的事件。
     * 为兼容历史版本产生的重复默认课表，这里对完全相同的 EAS 课程做显示层去重；
     * 手动活动、考试、ICS 导入等非 EAS 课程不在这里合并。
     */
    fun getEventsDuringWithColor(from: Long, to: Long): LiveData<List<EventItem>> {
        return MTransformations.switchMap(eventItemDao.getEventsDuring(from, to)) { events ->
            val displayEvents = dedupeDisplayEvents(events)
            val subjects = mutableSetOf<String>()
            for (e in displayEvents) {
                if (e.subjectId.isNotBlank()) {
                    subjects.add(e.subjectId)
                }
            }
            val colorSource = subjectDao.getSubjectColorsWithId(subjects)
            MTransformations.map(colorSource) { colors ->
                val map = mutableMapOf<String, Int>()
                for (color in colors) {
                    map[color.id] = color.color
                }
                for (e in displayEvents) {
                    map[e.subjectId]?.let {
                        e.color = it
                    } ?: run {
                        if (e.subjectId.isBlank()) {
                            e.color = manualEventFallbackColor
                        }
                    }
                }
                displayEvents
            }
        }
    }

    fun getClassesOfSubject(subjectId: String): LiveData<List<EventItem>> {
        return eventItemDao.getClassesOfSubject(subjectId)
    }

    fun getEventsOfTimetable(timetableId: String): LiveData<List<EventItem>> {
        return eventItemDao.getEventsOfTimetable(timetableId)
    }

    /**
     * 获取所有课表
     */
    fun getTimetables(): LiveData<List<Timetable>> {
        return timetableDao.getTimetables()
    }

    fun getTimetablesById(id: String): LiveData<Timetable> {
        return timetableDao.getTimetableById(id)
    }

    @WorkerThread
    fun getRecentTimetableSync(): Timetable? {
        return timetableDao.getTimetableClosestToTimestampSync(System.currentTimeMillis())
    }

    @WorkerThread
    fun getTimetableByIdSync(id: String): Timetable? {
        return timetableDao.getTimetableByIdSync(id)
    }

    @WorkerThread
    fun getEventsOfTimetableSync(
        timetableId: String,
        fromMs: Long? = null,
        toMs: Long? = null
    ): List<EventItem> {
        if (fromMs != null && toMs != null) {
            return eventItemDao.getEventsOfTimetableDuringSync(timetableId, fromMs, toMs)
        }
        return eventItemDao.getEventsOfTimetableSync(timetableId)
    }

    @WorkerThread
    fun getEventsOfAllTimetablesSync(fromMs: Long?, toMs: Long?): List<EventItem> {
        if (fromMs != null && toMs != null) {
            return eventItemDao.getEventsOfAllTimetablesDuringSync(fromMs, toMs)
        }
        return emptyList()
    }

    fun getTimetableByEasCode(code: String): LiveData<Timetable?> {
        return timetableDao.getTimetableByEASCode(code)
    }

    /**
     * 获得某学期（本地可能没有）的当前周数
     */
    fun getCurrentWeekOfTimetable(termItem: TermItem?): LiveData<Int> {
        if (termItem == null) {
            return MTransformations.map(timetableDao.getTimetableByEASCode("")) {
                1
            }
        }
        val campus = easPreferenceSource.getEasToken().campus
        val preferredCode = EASTimetableCode.of(campus, termItem)
        val legacyCode = termItem.getCode()
        return MTransformations.map(
            timetableDao.getTimetableByEASCodeCandidates(
                EASTimetableCode.candidates(termItem, campus),
                preferredCode,
                legacyCode
            )
        ) {
            it?.getWeekNumber(System.currentTimeMillis()) ?: 1
        }
    }

    fun getRecentTimetable(): LiveData<Timetable?> {
        return timetableDao.getTimetableClosestToTimestamp(System.currentTimeMillis())
    }

    fun getTimetableCount(): LiveData<Int> {
        return timetableDao.geeTimetableCount()
    }

    fun searchLocation(str: String): LiveData<List<String>> {
        return timetableDao.searchLocation("%$str%")
    }

    @WorkerThread
    fun searchEventsByKeywordSync(keyword: String): List<EventItem> {
        return eventItemDao.searchEventsByNameSync(keyword)
    }

    fun actionDeleteTimetables(timetables: List<Timetable>) {
        val ids = mutableListOf<String>()
        for (tt in timetables) {
            ids.add(tt.id)
        }
        executor.execute {
            timetableDao.deleteTimetablesSync(timetables)
            eventItemDao.deleteEventsFromTimetablesSync(ids)
            subjectDao.deleteSubjectsFromTimetablesSync(ids)
        }

    }

    fun actionDeleteEvents(courses: Collection<EventItem>) {
        val ids = mutableListOf<String>()
        for (tt in courses) {
            ids.add(tt.id)
        }
        executor.execute {
            eventItemDao.deleteEventsInIdsSync(ids)
        }
    }

    fun actionNewTimetable() {
        executor.execute {
            val newTable = buildNextDefaultTimetableSync()
            timetableDao.saveTimetableSync(newTable)
        }
    }

    /**
     * 进入主页/课表管理页前的轻量维护动作。
     *
     * 顺序很重要：
     * 1. 先清理历史遗留的纯重复默认课表；
     * 2. 如果数据库完全没有课表，再创建一个默认自定义课表。
     *
     * 这样不会在已有 EAS 学期课表时额外造一张空“默认课表”。
     */
    fun actionPrepareTimetableList() {
        executor.execute {
            cleanupDefaultDuplicateTimetablesSync()
            ensureDefaultCustomTimetableSync()
        }
    }

    fun ensureDefaultCustomTimetableAsync() {
        executor.execute {
            ensureDefaultCustomTimetableSync()
        }
    }

    @WorkerThread
    fun ensureDefaultCustomTimetableSync(): Timetable {
        val existing = timetableDao.getFirstCustomTimetableSync()
        if (existing != null) return existing

        val firstTimetable = timetableDao.getTimetablesSync().firstOrNull()
        if (firstTimetable != null) return firstTimetable

        val newTable = buildNextDefaultTimetableSync()
        timetableDao.saveTimetableSync(newTable)
        return newTable
    }

    @WorkerThread
    fun cleanupDefaultDuplicateTimetablesSync() {
        val defaultPrefix = application.getString(R.string.default_timetable_name)
        val defaults = timetableDao.getDefaultNamedCustomTimetablesSync("$defaultPrefix%")
        if (defaults.isEmpty()) return

        val easTables = timetableDao.getTimetablesSync()
            .filter { !it.code.isNullOrBlank() }
        if (easTables.isEmpty()) return

        val easEventKeys = easTables.associate { timetable ->
            timetable.id to eventItemDao.getImportedClassEventsOfTimetableSync(timetable.id)
                .mapTo(mutableSetOf()) { importedClassEventIdentityKey(it) }
        }

        val deleteIds = defaults.mapNotNull { timetable ->
            val eventCount = eventItemDao.countEventsOfTimetableSync(timetable.id)
            if (eventCount == 0) {
                return@mapNotNull timetable.id
            }

            val nonImportedClassCount = eventItemDao.countNonImportedClassEventsOfTimetableSync(timetable.id)
            if (nonImportedClassCount > 0) {
                return@mapNotNull null
            }

            val defaultKeys = eventItemDao.getImportedClassEventsOfTimetableSync(timetable.id)
                .mapTo(mutableSetOf()) { importedClassEventIdentityKey(it) }
            val duplicatedByEas = defaultKeys.isNotEmpty() && easEventKeys.values.any { easKeys ->
                easKeys.isNotEmpty() && easKeys.containsAll(defaultKeys)
            }
            if (duplicatedByEas) timetable.id else null
        }
        if (deleteIds.isEmpty()) return

        LogUtils.d("cleanupDefaultDuplicateTimetables: deleting defaults=$deleteIds")
        timetableDao.deleteTimetablesInIdsSync(deleteIds)
        eventItemDao.deleteEventsFromTimetablesSync(deleteIds)
        subjectDao.deleteSubjectsFromTimetablesSync(deleteIds)
    }

    @WorkerThread
    private fun buildNextDefaultTimetableSync(): Timetable {
        val defaultPrefix = application.getString(R.string.default_timetable_name)
        val defaultTables = timetableDao.getTimetableNamesWithDefaultSync("$defaultPrefix%")
        var max = 0
        for (tt in defaultTables) {
            val i = try {
                tt.replace(defaultPrefix, "").toInt()
            } catch (e: NumberFormatException) {
                null
            }
            if (i != null && i > max) {
                max = i
            }
        }
        val newTable = Timetable()
        val c = TimeTools.getMonday(System.currentTimeMillis())
        newTable.startTime.time = c.timeInMillis
        newTable.endTime.time = c.timeInMillis + WEEK_MILLS
        newTable.name = defaultPrefix + (max + 1).toString()
        return newTable
    }

    fun actionSaveTimetable(timetable: Timetable) {
        executor.execute {
            timetableDao.saveTimetableSync(timetable)
        }
    }

    @WorkerThread
    fun saveTimetableSync(timetable: Timetable) {
        timetableDao.saveTimetableSync(timetable)
    }

    fun actionChangeTimetableStartDate(timetable: Timetable, startTime: Long) {
        val calendar = TimeTools.getMonday(startTime)
        val offset = calendar.timeInMillis - timetable.startTime.time
        timetable.endTime.time = timetable.endTime.time + offset
        timetable.startTime.time = calendar.timeInMillis
        executor.execute {
            timetableDao.saveTimetableSync(timetable)
            eventItemDao.updateClassesAddOffset(timetableId = timetable.id, offset)
        }
    }

    fun actionChangeTimetableStructure(timetable: Timetable, tp: TimePeriodInDay, position: Int) {
        timetable.setScheduleStructure(tp, position)
        executor.execute {
            timetableDao.saveTimetableSync(timetable)
            val fromToChange = eventItemDao.getClassAtFromNumberSync(timetable.id, position + 1)
            val tmp = Calendar.getInstance()
            val ids = mutableListOf<String>()
            for (e in fromToChange) {
                ids.add(e.id)
                tmp.timeInMillis = e.from.time
                tmp.set(Calendar.HOUR_OF_DAY, tp.from.hour)
                tmp.set(Calendar.MINUTE, tp.from.minute)
                e.from.time = tmp.timeInMillis
            }
            eventItemDao.saveEvents(fromToChange)
            val endToChange = eventItemDao.getClassAtToNumberSync(timetable.id, position + 1)
            for (e in endToChange) {
                ids.add(e.id)
                tmp.timeInMillis = e.to.time
                tmp.set(Calendar.HOUR_OF_DAY, tp.to.hour)
                tmp.set(Calendar.MINUTE, tp.to.minute)
                e.to.time = tmp.timeInMillis
            }
            eventItemDao.saveEvents(endToChange)
        }
    }

    fun actionAddEvents(data:List<EventItem>) {
        executor.execute {
            eventItemDao.addEvents(data)
        }
    }

    fun actionUpdateEvent(event: EventItem) {
        executor.execute {
            eventItemDao.updateEventSync(event)
        }
    }

    @WorkerThread
    fun addEventsSync(data: List<EventItem>) {
        eventItemDao.addEvents(data)
    }


    fun exportToICS(timetableName: String, timetableId: String): LiveData<DataState<String>> {
        val res = MutableLiveData<DataState<String>>();
        executor.execute {
            val filename = "HITSZ助手：$timetableName.ics"
            val path = application.getExternalFilesDir("ics").toString() + "/" + filename
            try {
                val calendar = net.fortuna.ical4j.model.Calendar()
                calendar.properties.add(ProdId("-//StupidTree//HITA//EN"))
                calendar.properties.add(Version.VERSION_2_0)
                calendar.properties.add(CalScale.GREGORIAN)
                val timetable = timetableDao.getTimetableByIdSync(timetableId)
                for (ei in eventItemDao.getEventsOfTimetableSync(timetableId)) {
                    val start = DateTime(ei.from)
                    start.isUtc = true
                    val end = DateTime(ei.to)
                    end.isUtc = true
                    val event = VEvent(start, end, ei.name)

                    val place = ei.place?.trim().orEmpty()
                    if (place.isNotEmpty()) {
                        event.properties.add(Location(place))
                    }

                    val weekNumber = timetable?.getWeekNumber(ei.from.time) ?: -1
                    val weekText = if (weekNumber > 0) "第${weekNumber}周" else "未知周次"
                    val description = buildString {
                        if (!ei.teacher.isNullOrBlank()) appendLine("教师：${ei.teacher}")
                        appendLine("周次：$weekText")
                        appendLine("来源：HITA Android")
                    }.trim()
                    event.properties.add(Description(description))

                    event.properties.add(XProperty("X-HITA-SCHEMA", "1.0"))
                    event.properties.add(XProperty("X-HITA-SOURCE", "android"))
                    event.properties.add(XProperty("X-HITA-COURSE-NAME", ei.name))
                    if (!ei.teacher.isNullOrBlank()) {
                        event.properties.add(XProperty("X-HITA-TEACHER", ei.teacher))
                    }
                    if (place.isNotEmpty()) {
                        event.properties.add(XProperty("X-HITA-CLASSROOM", place))
                    }
                    if (weekNumber > 0) {
                        event.properties.add(XProperty("X-HITA-WEEKS", weekNumber.toString()))
                    }
                    event.properties.add(XProperty("X-HITA-TYPE", ei.type.name.lowercase()))

                    event.properties.add(Uid(UidGenerator("hita").generateUid().value))

                    val valarm = VAlarm(Dur(0, 0, -10, 0))
                    val isCourseLike = ei.type == EventItem.TYPE.CLASS || ei.type == EventItem.TYPE.EXAM
                    valarm.properties.add(Summary(if (isCourseLike) "课程提醒" else "事件提醒"))
                    valarm.properties.add(Action.DISPLAY)
                    valarm.properties.add(Description(ei.name + if (isCourseLike) "马上就要开始啦！" else "马上开始啦！"))
                    event.alarms.add(valarm)
                    calendar.components.add(event)

                }
                calendar.validate()

                val fos = FileOutputStream(path)
                val outputter = CalendarOutputter()
                outputter.output(calendar, fos)
                res.postValue(DataState(path))
            } catch (e: Exception) {
                LogUtils.e("Failed to export timetable to ICS", e)
                res.postValue(DataState(DataState.STATE.FETCH_FAILED))
            }
        }
        return res;
    }

    /**
     * 从 ICS 文件导入课表
     * @param inputStream ICS 文件输入流
     * @param timetableId 目标课表 ID
     * @return 导入结果，包含导入的课程数量
     */
    fun importFromICS(inputStream: InputStream, timetableId: String): LiveData<DataState<Int>> {
        val res = MutableLiveData<DataState<Int>>()
        executor.execute {
            try {
                val builder = CalendarBuilder()
                val calendar = builder.build(inputStream)
                val events = calendar.components.filterIsInstance<VEvent>()
                var importedCount = 0
                
                for (event in events) {
                    val ei = IcsImportEventMapper.map(event, timetableId) ?: continue
                    eventItemDao.insertEventSync(ei)
                    importedCount++
                }
                
                res.postValue(DataState(importedCount))
            } catch (e: Exception) {
                LogUtils.e("Failed to import events from ICS", e)
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, e.message))
            }
        }
        return res
    }

    fun importFromICSAsNewTimetable(
        inputStream: InputStream,
        sourceName: String?
    ): LiveData<DataState<IcsImportResult>> {
        val res = MutableLiveData<DataState<IcsImportResult>>()
        executor.execute {
            try {
                val calendar = inputStream.use { CalendarBuilder().build(it) }
                val bundle = IcsImportBundleBuilder.build(
                    events = calendar.components.filterIsInstance<VEvent>(),
                    sourceName = sourceName
                )
                timetableDao.saveTimetableSync(bundle.timetable)
                subjectDao.saveSubjectsSync(bundle.subjects)
                eventItemDao.saveEvents(bundle.events)

                res.postValue(
                    DataState(
                        IcsImportResult(
                            timetableId = bundle.timetable.id,
                            timetableName = bundle.timetable.name ?: application.getString(R.string.default_timetable_name),
                            importedCount = bundle.events.size
                        )
                    )
                )
            } catch (e: Exception) {
                LogUtils.e("Failed to import ICS as new timetable", e)
                val message = if (e is IllegalArgumentException) e.message else e.message
                res.postValue(DataState(DataState.STATE.FETCH_FAILED, message))
            }
        }
        return res
    }

    fun actionClearData() {
        executor.execute {
            eventItemDao.clear()
            subjectDao.clear()
            timetableDao.clear()
        }
    }

    private fun dedupeDisplayEvents(events: List<EventItem>): List<EventItem> {
        val seenImportedClasses = mutableSetOf<String>()
        return events.filter { event ->
            if (event.source != EventItem.SOURCE_EAS_IMPORT || event.type != EventItem.TYPE.CLASS) {
                true
            } else {
                seenImportedClasses.add(importedClassEventIdentityKey(event))
            }
        }
    }

    private fun importedClassEventIdentityKey(event: EventItem): String {
        return listOf(
            event.name.trim(),
            event.place.orEmpty().trim(),
            event.teacher.orEmpty().trim(),
            event.from.time.toString(),
            event.to.time.toString(),
            event.fromNumber.toString(),
            event.lastNumber.toString(),
        ).joinToString("|")
    }

}
