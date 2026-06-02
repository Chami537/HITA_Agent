package cn.limpu.hita.ui.main.navigation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository
) : ViewModel() {

    private val recentTimetableController = MutableLiveData<Trigger>()
    val recentTimetableLiveData: LiveData<Timetable?> = recentTimetableController.switchMap {
            return@switchMap timetableRepository.getRecentTimetable()
        }
    val timetableCountLiveData: LiveData<Int> = recentTimetableController.switchMap {
            return@switchMap timetableRepository.getTimetableCount()
        }

    fun startRefresh() {
        recentTimetableController.value = Trigger.actioning
    }
}
