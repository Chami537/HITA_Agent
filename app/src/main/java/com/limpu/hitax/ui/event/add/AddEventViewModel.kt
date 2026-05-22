package com.limpu.hitax.ui.event.add

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.limpu.component.data.DataState
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.ScheduleEventCreator
import com.limpu.hitax.data.repository.SubjectRepository
import com.limpu.hitax.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val eventRepo: TimetableRepository,
    private val subjectRepo: SubjectRepository
) : ViewModel() {
    enum class AddMode {
        BATCH_PERIOD,
        FREE_RANGE
    }

    enum class ContentMode {
        SUBJECT,
        CUSTOM
    }

    val addModeLiveData = MutableLiveData(AddMode.BATCH_PERIOD)
    val contentModeLiveData = MutableLiveData(ContentMode.CUSTOM)
    val timetableLiveData = MutableLiveData<DataState<Timetable>>()
    val subjectLiveData = MediatorLiveData<DataState<TermSubject>>()
    val timeRangeLiveDate = MediatorLiveData<DataState<CourseTime>>()
    val customDateLiveData = MutableLiveData<DataState<Long>>()
    val customTimePeriodLiveData = MutableLiveData<DataState<TimePeriodInDay>>()
    val customFromToLiveData = MediatorLiveData<DataState<Pair<Long, Long>>>()
    val nameLiveData = MediatorLiveData<String?>()

    val locationLiveData = MediatorLiveData<DataState<String>>()
    val teacherLiveData = MediatorLiveData<DataState<String>>()

    val doneLiveData = MediatorLiveData<Boolean>()

    var addSubject: Boolean = false

    init {
        doneLiveData.addSource(addModeLiveData) {
            checkDone()
        }
        doneLiveData.addSource(contentModeLiveData) {
            checkDone()
        }
        doneLiveData.addSource(subjectLiveData) {
            checkDone()
        }
        doneLiveData.addSource(nameLiveData) {
            checkDone()
        }
        doneLiveData.addSource(timetableLiveData) {
            checkDone()
        }
        doneLiveData.addSource(timeRangeLiveDate) {
            checkDone()
        }
        doneLiveData.addSource(customFromToLiveData) {
            checkDone()
        }

        timeRangeLiveDate.addSource(timetableLiveData) {
            if (it.state == DataState.STATE.SUCCESS) {
                if (initCourseT != null) {
                    timeRangeLiveDate.value = DataState(initCourseT!!)
                    initCourseT = null
                } else {
                    timeRangeLiveDate.value = DataState(DataState.STATE.NOTHING)
                }
            } else {
                timeRangeLiveDate.value = DataState(DataState.STATE.FETCH_FAILED)
            }
        }

        customDateLiveData.value = DataState(DataState.STATE.NOTHING)
        customTimePeriodLiveData.value = DataState(DataState.STATE.NOTHING)

        customFromToLiveData.addSource(customDateLiveData) {
            refreshCustomFromTo()
        }
        customFromToLiveData.addSource(customTimePeriodLiveData) {
            refreshCustomFromTo()
        }

        subjectLiveData.addSource(timetableLiveData) {
            refreshSubjectState()
        }
        subjectLiveData.addSource(contentModeLiveData) { mode ->
            if (mode == ContentMode.CUSTOM) {
                subjectLiveData.value = DataState(DataState.STATE.NOTHING)
            } else {
                refreshSubjectState()
            }
        }

        teacherLiveData.addSource(subjectLiveData) {
            if (it.state == DataState.STATE.SUCCESS
                || it.state == DataState.STATE.SPECIAL
                || it.state == DataState.STATE.NOTHING
            ) {
                if (teacherLiveData.value?.state != DataState.STATE.SUCCESS) {
                    teacherLiveData.value = DataState(DataState.STATE.NOTHING)
                }
            } else {
                teacherLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
            }
        }
        locationLiveData.addSource(subjectLiveData) {
            if (it.state == DataState.STATE.SUCCESS
                || it.state == DataState.STATE.SPECIAL
                || it.state == DataState.STATE.NOTHING
            ) {
                if (locationLiveData.value?.state != DataState.STATE.SUCCESS) {
                    locationLiveData.value = DataState(DataState.STATE.NOTHING)
                }
            } else {
                locationLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
            }
        }
    }

    fun setAddMode(mode: AddMode) {
        if (addModeLiveData.value == mode) return
        addModeLiveData.value = mode
        checkDone()
    }

    fun setContentMode(mode: ContentMode) {
        if (contentModeLiveData.value == mode) return
        val selectedSubjectName = subjectLiveData.value?.data?.name
        contentModeLiveData.value = mode
        if (mode == ContentMode.CUSTOM) {
            subjectLiveData.value = DataState(DataState.STATE.NOTHING)
            if (nameLiveData.value == selectedSubjectName) {
                nameLiveData.value = ""
            }
        }
        checkDone()
    }

    fun selectSubject(subject: TermSubject) {
        contentModeLiveData.value = ContentMode.SUBJECT
        subjectLiveData.value = DataState(subject)
        if (nameLiveData.value.isNullOrBlank() || nameLiveData.value == subjectLiveData.value?.data?.name) {
            nameLiveData.value = subject.name
        }
        if (!subject.teacher.isNullOrBlank()) {
            teacherLiveData.value = DataState(subject.teacher!!)
        }
        checkDone()
    }

    private fun refreshCustomFromTo() {
        val date = customDateLiveData.value
        val period = customTimePeriodLiveData.value
        if (date?.state == DataState.STATE.SUCCESS && period?.state == DataState.STATE.SUCCESS) {
            val cFrom = Calendar.getInstance()
            cFrom.timeInMillis = date.data ?: 0L
            cFrom.set(Calendar.HOUR_OF_DAY, period.data?.from?.hour ?: 0)
            cFrom.set(Calendar.MINUTE, period.data?.from?.minute ?: 0)
            cFrom.set(Calendar.SECOND, 0)
            cFrom.set(Calendar.MILLISECOND, 0)

            val cTo = Calendar.getInstance()
            cTo.timeInMillis = date.data ?: 0L
            cTo.set(Calendar.HOUR_OF_DAY, period.data?.to?.hour ?: 0)
            cTo.set(Calendar.MINUTE, period.data?.to?.minute ?: 0)
            cTo.set(Calendar.SECOND, 0)
            cTo.set(Calendar.MILLISECOND, 0)

            if (cTo.timeInMillis > cFrom.timeInMillis) {
                customFromToLiveData.value = DataState(Pair(cFrom.timeInMillis, cTo.timeInMillis))
            } else {
                customFromToLiveData.value = DataState(DataState.STATE.NOTHING)
            }
        } else if (date?.state == DataState.STATE.FETCH_FAILED || period?.state == DataState.STATE.FETCH_FAILED) {
            customFromToLiveData.value = DataState(DataState.STATE.FETCH_FAILED)
        } else {
            customFromToLiveData.value = DataState(DataState.STATE.NOTHING)
        }
    }

    private fun refreshSubjectState() {
        if (contentModeLiveData.value != ContentMode.SUBJECT) {
            subjectLiveData.value = DataState(DataState.STATE.NOTHING)
            return
        }
        if (addSubject) {
            subjectLiveData.value = DataState(DataState.STATE.SPECIAL)
            return
        }
        if (initSubject != null) {
            subjectLiveData.value = DataState(initSubject!!)
            initSubject = null
            return
        }
        if (subjectLiveData.value?.state != DataState.STATE.SUCCESS
            || subjectLiveData.value?.data?.timetableId != timetableLiveData.value?.data?.id
        ) {
            subjectLiveData.value = DataState(DataState.STATE.NOTHING)
        }
    }

    private fun checkDone() {
        val mode = addModeLiveData.value ?: AddMode.BATCH_PERIOD
        val contentReady = when (contentModeLiveData.value ?: ContentMode.CUSTOM) {
            ContentMode.SUBJECT -> subjectLiveData.value?.state == DataState.STATE.SUCCESS
            ContentMode.CUSTOM -> !nameLiveData.value.isNullOrBlank()
        }
        val baseReady = timetableLiveData.value?.state == DataState.STATE.SUCCESS
            && contentReady
        val done = when (mode) {
            AddMode.BATCH_PERIOD -> {
                baseReady
                    && timeRangeLiveDate.value?.state == DataState.STATE.SUCCESS
            }

            AddMode.FREE_RANGE -> {
                baseReady && customFromToLiveData.value?.state == DataState.STATE.SUCCESS
            }
        }
        doneLiveData.value = done
    }

    var initSubject: TermSubject? = null
    var initCourseT: CourseTime? = null
    fun init(
        addSubject: Boolean,
        timetable: Timetable?,
        subject: TermSubject?,
        courseTime: CourseTime?
    ) {
        initCourseT = courseTime
        initSubject = subject
        this.addSubject = addSubject

        if (timetable == null) {
            timetableLiveData.value = DataState(DataState.STATE.NOTHING)
        } else {
            timetableLiveData.value = DataState(timetable)
        }

        if (courseTime == null) {
            customTimePeriodLiveData.value = DataState(DataState.STATE.NOTHING)
        } else {
            customTimePeriodLiveData.value = DataState(courseTime.period.clone())
        }
        subject?.let { selectSubject(it) }
    }

    fun setCustomDate(dateMs: Long) {
        customDateLiveData.value = DataState(dateMs)
    }

    fun setCustomTimePeriod(from: TimeInDay, to: TimeInDay) {
        customTimePeriodLiveData.value = DataState(TimePeriodInDay(from, to))
    }

    fun createEvent() {
        timetableLiveData.value?.data?.let { timetable ->
            val subjectToSave: TermSubject?
            val subject: TermSubject?
            if (contentModeLiveData.value == ContentMode.SUBJECT) {
                subject = subjectLiveData.value?.data ?: return
                subjectToSave = null
            } else if (addSubject) {
                subject = TermSubject().apply {
                    name = nameLiveData.value?.trim().orEmpty()
                    timetableId = timetable.id
                }
                subjectToSave = subject
            } else {
                subject = null
                subjectToSave = null
            }
            val content = ScheduleEventCreator.Content(
                name = (subject?.name ?: nameLiveData.value)?.trim().orEmpty(),
                place = locationLiveData.value?.data ?: "",
                teacher = teacherLiveData.value?.data ?: subject?.teacher.orEmpty(),
                subject = subject,
                type = if (subject != null) EventItem.TYPE.CLASS else EventItem.TYPE.OTHER,
            )
            val result = when (addModeLiveData.value ?: AddMode.BATCH_PERIOD) {
                AddMode.BATCH_PERIOD -> {
                    val range = timeRangeLiveDate.value?.data ?: return
                    ScheduleEventCreator.buildEvents(
                        timetable = timetable,
                        content = content,
                        courseTime = range,
                        source = EventItem.SOURCE_MANUAL,
                    )
                }

                AddMode.FREE_RANGE -> {
                    val fromTo = customFromToLiveData.value?.data ?: return
                    ScheduleEventCreator.buildEvents(
                        timetable = timetable,
                        content = content,
                        ranges = listOf(ScheduleEventCreator.FixedRange(fromTo.first, fromTo.second)),
                        source = EventItem.SOURCE_MANUAL,
                    )
                }
            }
            ScheduleEventCreator.persistAsync(result, eventRepo, subjectRepo, subjectToSave)
        }
    }
}
