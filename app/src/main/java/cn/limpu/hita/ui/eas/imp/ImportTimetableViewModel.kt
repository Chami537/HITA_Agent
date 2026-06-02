package cn.limpu.hita.ui.eas.imp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.timetable.TimePeriodInDay
import cn.limpu.hita.data.repository.EASRepository
import cn.limpu.hita.data.source.preference.BenbuStartDatePreferenceSource
import cn.limpu.hita.ui.eas.EASViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ImportTimetableViewModel @Inject constructor(
    easRepo: EASRepository,
    private val benbuStartDatePreference: BenbuStartDatePreferenceSource
) : EASViewModel(easRepo) {

    private val termsController = MutableLiveData<Trigger>()
    private var startDateSource: LiveData<DataState<Calendar>>? = null

    val termsLiveData: LiveData<DataState<List<TermItem>>> = termsController.switchMap {
        easRepo.getAllTerms()
    }

    val selectedTermLiveData: MutableLiveData<TermItem?> = MutableLiveData()
    val startDateLiveData = MediatorLiveData<DataState<Calendar>>()
    val benbuCalibrationConfirmedLiveData = MediatorLiveData<Boolean>()
    val importTimetableResultLiveData = MediatorLiveData<DataState<Boolean>>()
    val isUndergraduateLiveData = MutableLiveData<Boolean>()
    val scheduleStructureLiveData: MediatorLiveData<DataState<MutableList<TimePeriodInDay>>> =
        MediatorLiveData()
    private var scheduleInnerSource1: LiveData<DataState<MutableList<TimePeriodInDay>>>? = null
    private var scheduleInnerSource2: LiveData<DataState<MutableList<TimePeriodInDay>>>? = null

    init {
        startDateLiveData.value = DataState(Calendar.getInstance())
        benbuCalibrationConfirmedLiveData.value = true

        startDateLiveData.addSource(selectedTermLiveData) { term ->
            startDateSource?.let { startDateLiveData.removeSource(it) }
            if (term == null) {
                startDateLiveData.value = DataState(Calendar.getInstance())
                benbuCalibrationConfirmedLiveData.value = true
                return@addSource
            }
            val source = easRepo.getStartDateOfTerm(term)
            startDateSource = source
            startDateLiveData.addSource(source) { state ->
                startDateLiveData.value = resolveStartDateState(term, state)
                benbuCalibrationConfirmedLiveData.value = isBenbuCalibrationConfirmed(term)
            }
        }

        benbuCalibrationConfirmedLiveData.addSource(selectedTermLiveData) { term ->
            benbuCalibrationConfirmedLiveData.value = term?.let { isBenbuCalibrationConfirmed(it) } ?: true
        }

        scheduleStructureLiveData.addSource(selectedTermLiveData) { term ->
            scheduleInnerSource1?.let { scheduleStructureLiveData.removeSource(it) }
            if (term == null) return@addSource
            isUndergraduateLiveData.value?.let { isu ->
                val src = easRepo.getScheduleStructure(term, isu)
                scheduleInnerSource1 = src
                scheduleStructureLiveData.addSource(src) { itt ->
                    scheduleStructureLiveData.value = itt
                }
            }
        }
        scheduleStructureLiveData.addSource(isUndergraduateLiveData) { isu ->
            scheduleInnerSource2?.let { scheduleStructureLiveData.removeSource(it) }
            selectedTermLiveData.value?.let { st ->
                val src = easRepo.getScheduleStructure(st, isu)
                scheduleInnerSource2 = src
                scheduleStructureLiveData.addSource(src) { itt ->
                    scheduleStructureLiveData.value = itt
                }
            }
        }
    }

    fun startRefreshTerms() {
        termsController.value = Trigger.actioning
    }

    fun changeSelectedTerm(termItem: TermItem) {
        selectedTermLiveData.value = termItem
    }

    fun changeIsUndergraduate(isUnder: Boolean) {
        isUndergraduateLiveData.value = isUnder
    }

    fun startGetAllTerms(): List<TermItem> {
        return termsLiveData.value?.data ?: listOf()
    }

    fun startImportTimetable(): Boolean {
        selectedTermLiveData.value?.let { term ->
            startDateLiveData.value?.let { date ->
                scheduleStructureLiveData.value?.let { schedule ->
                    if (schedule.data != null && date.state == DataState.STATE.SUCCESS && date.data != null) {
                        easRepo.startImportTimetableOfTerm(
                            term,
                            date.data!!,
                            schedule.data!!,
                            importTimetableResultLiveData
                        )
                        return true
                    }
                }
            }
        }
        return false
    }

    fun retryImportTimetable(): Boolean {
        return startImportTimetable()
    }


    fun setStructureData(periodInDay: TimePeriodInDay, position: Int) {
        if (position < (scheduleStructureLiveData.value?.data?.size ?: 0)) {
            scheduleStructureLiveData.value?.data?.set(position, periodInDay)
            scheduleStructureLiveData.value = scheduleStructureLiveData.value
        }
    }

    fun changeStartDate(date: Calendar) {
        startDateLiveData.value = DataState(cloneCalendar(date))
    }

    fun shiftStartDateByWeek(offsetWeeks: Int) {
        val current = startDateLiveData.value?.data ?: return
        val shifted = cloneCalendar(current).apply {
            add(Calendar.DAY_OF_MONTH, offsetWeeks * 7)
        }
        startDateLiveData.value = DataState(shifted)
    }

    fun saveBenbuCalibration() {
        val term = selectedTermLiveData.value ?: return
        val date = startDateLiveData.value?.data ?: return
        if (!isBenbuTerm(term)) return
        benbuStartDatePreference.saveCalibration(term.getCode(), date.timeInMillis, true)
        benbuCalibrationConfirmedLiveData.value = true
    }

    fun isBenbuTerm(term: TermItem? = selectedTermLiveData.value): Boolean {
        return term != null && easRepo.getEasToken().isBenbuCampus()
    }

    private fun resolveStartDateState(term: TermItem, state: DataState<Calendar>): DataState<Calendar> {
        val sourceDate = state.data ?: return state
        val resolved = cloneCalendar(sourceDate)
        if (isBenbuTerm(term)) {
            benbuStartDatePreference.getStartDateMillis(term.getCode())?.let {
                resolved.timeInMillis = it
            }
        }
        return DataState(resolved, state.state).apply {
            message = state.message
        }
    }

    private fun isBenbuCalibrationConfirmed(term: TermItem): Boolean {
        return !isBenbuTerm(term) || benbuStartDatePreference.isConfirmed(term.getCode())
    }

    private fun cloneCalendar(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
