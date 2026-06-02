package cn.limpu.hita.ui.main.timetable

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.MTransformations
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.repository.TimetableRepository
import cn.limpu.hita.data.repository.TimetableStyleRepository
import cn.limpu.hita.data.repository.KEY_WALLPAPER_PATH
import cn.limpu.hita.ui.main.timetable.TimetableFragment.Companion.WEEK_MILLS
import cn.limpu.hita.ui.main.timetable.TimetableFragment.Companion.WINDOW_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val timetableStyleRepository: TimetableStyleRepository
) : ViewModel() {

    private val timetableController = MutableLiveData<Trigger>()
    val timetableLiveData: LiveData<List<Timetable>> = timetableController.switchMap{
            return@switchMap timetableRepository.getTimetables()
        }
    val startTimeLiveData: LiveData<Int>
        get() = timetableStyleRepository.startTimeLiveData
    val periodLabelLiveData: LiveData<Boolean>
        get() = timetableStyleRepository.periodLabelLiveData
    val wallpaperPathLiveData: LiveData<String>
        get() = timetableStyleRepository.wallpaperPathLiveData
    val wallpaperDateColorLiveData: LiveData<Int>
        get() = timetableStyleRepository.wallpaperDateColorLiveData
    val wallpaperLabelColorLiveData: LiveData<Int>
        get() = timetableStyleRepository.wallpaperLabelColorLiveData

    var currentPageStartDate: MutableLiveData<Long>
    var currentIndex = 0
    var startIndex = 0

    private val timetableStyleLiveData: LiveData<TimetableStyleSheet> =
        timetableStyleRepository.getStyleSheetLiveData()
    val windowEventsData: MutableList<MediatorLiveData<EventStylePair>> =
        mutableListOf()
    val windowStartData: MutableList<MutableLiveData<Long>> = mutableListOf()
    val windowHashesData = mutableListOf<Int>()

    init {
        val ws = Calendar.getInstance()
        ws.firstDayOfWeek = Calendar.MONDAY
        ws[Calendar.DAY_OF_WEEK] = Calendar.MONDAY
        ws[Calendar.HOUR_OF_DAY] = 0
        ws[Calendar.MINUTE] = 0
        ws[Calendar.SECOND] = 0
        ws[Calendar.MILLISECOND] = 0
        currentPageStartDate = MutableLiveData(ws.timeInMillis)
        for (i in 0 until WINDOW_SIZE) {
            windowHashesData.add(0)
            val startLD = MutableLiveData<Long>()
            windowStartData.add(startLD)
            val eventsRawData =  startLD.switchMap{
                return@switchMap timetableRepository.getEventsDuringWithColor(
                    it,
                    it + WEEK_MILLS
                )
            }
            val eventsData = MTransformations.switchMap(eventsRawData,timetableStyleLiveData){
                return@switchMap MutableLiveData(EventStylePair(it.first, it.second))
            }
            windowEventsData.add(eventsData)
        }
    }

    fun startRefresh() {
        timetableRepository.ensureDefaultCustomTimetableAsync()
        timetableController.value = Trigger.actioning
    }

    fun clearWallpaperPath() {
        timetableStyleRepository.putData(KEY_WALLPAPER_PATH, "")
    }

    fun addStartDate(offset: Long) {
        currentPageStartDate.value?.let {
            currentPageStartDate.value = it + offset
        }
    }

}

/** Wrapper that uses reference equality so Compose always recomposes on new emissions. */
class EventStylePair(
    val events: List<EventItem>,
    val style: TimetableStyleSheet
)
