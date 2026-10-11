package cn.limpu.hita.ui.main.timeline

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.limpu.component.data.Trigger
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.repository.DisplayScopeResolver
import cn.limpu.hita.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FragmentTimelineViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    scopeResolver: DisplayScopeResolver
) : ViewModel() {

    /**
     * 数据区
     *
     * 今日/考试/本周后续全部跟随主课表显示目标过滤;触发器与显示目标任一变化都重建查询。
     * 显示目标尚未解析完成时保持空列表,避免闪现其他课表事件。
     */
    private val todayEventsController: MutableLiveData<Trigger> = MutableLiveData()
    private val queryInput = MediatorLiveData<List<String>>()

    val todayEventsLiveData: LiveData<List<EventItem>> = queryInput.switchMap { ids ->
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        val from = now.timeInMillis
        now.add(Calendar.DATE, 1)
        val to = now.timeInMillis
        return@switchMap timetableRepository.getEventsDuring(from, to, ids)
    }

    val upcomingExamLiveData: LiveData<List<EventItem>> = queryInput.switchMap { ids ->
        return@switchMap timetableRepository.getUpcomingExamsWithinReminderWindow(
            System.currentTimeMillis(), ids)
    }

    val weekEventsLiveData: LiveData<List<EventItem>> = queryInput.switchMap { ids ->
        return@switchMap timetableRepository.getEventsAfter(System.currentTimeMillis(), 4, ids)
    }


    private val _enterTick = MutableLiveData(0L)
    val enterTick: LiveData<Long> = _enterTick

    init {
        queryInput.addSource(todayEventsController) { refreshIds(scopeResolver, force = true) }
        queryInput.addSource(scopeResolver.resolved) { refreshIds(scopeResolver) }
    }

    private fun refreshIds(scopeResolver: DisplayScopeResolver, force: Boolean = false) {
        if (todayEventsController.value == null) return
        val ids = scopeResolver.resolved.value?.timetableIds.orEmpty()
        if (force || queryInput.value != ids) queryInput.value = ids
    }

    fun startRefresh() {
        todayEventsController.value = Trigger.actioning
    }

    fun triggerEnterAnimation() {
        _enterTick.value = System.currentTimeMillis()
    }
}
