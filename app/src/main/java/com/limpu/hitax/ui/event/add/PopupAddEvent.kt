package com.limpu.hitax.ui.event.add

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.TeacherInfoRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.databinding.DialogBottomAddEventBinding
import com.limpu.hitax.ui.widgets.PopUpCalendarPicker
import com.limpu.hitax.ui.widgets.PopUpPickCourseTime
import com.limpu.hitax.ui.widgets.PopUpTimePeriodPicker
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.style.widgets.DialogAutoEditText
import com.limpu.style.widgets.DialogSelectableLiveList
import com.limpu.style.widgets.TransparentModeledBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class PopupAddEvent(private val addSubjectMode: Boolean = false) :
    TransparentModeledBottomSheetDialog<AddEventViewModel, DialogBottomAddEventBinding>() {

    var initTimetable: Timetable? = null
    var initSubject: TermSubject? = null
    var initCourseTime: CourseTime? = null

    override fun getViewModelClass(): Class<AddEventViewModel> {
        return AddEventViewModel::class.java
    }

    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_add_event
    }

    override fun initViewBinding(v: View): DialogBottomAddEventBinding {
        return DialogBottomAddEventBinding.bind(v)
    }

    fun setInitTimetable(timetable: Timetable?): PopupAddEvent {
        initTimetable = timetable
        return this
    }

    fun setInitTime(dow: Int, week: Int, period: TimePeriodInDay): PopupAddEvent {
        val ct = CourseTime()
        ct.dow = dow
        ct.weeks = mutableListOf(week)
        ct.period = period
        initCourseTime = ct
        return this
    }

    fun setInitSubject(subject: TermSubject): PopupAddEvent {
        initSubject = subject
        return this
    }

    @SuppressLint("SetTextI18n")
    override fun initViews(view: View) {
        binding?.title?.setText(if (addSubjectMode) R.string.add_subject else R.string.ade_title)
        binding?.cancel?.setOnClickListener { dismiss() }

        binding?.pickSubject?.setOnClickListener {
            showExistingEventPicker()
        }
        binding?.contentCustom?.setOnClickListener {
            viewModel.setContentMode(AddEventViewModel.ContentMode.CUSTOM)
        }
        binding?.dateSingle?.setOnClickListener {
            viewModel.setDateMode(AddEventViewModel.DateMode.SINGLE)
        }
        binding?.dateWeekly?.setOnClickListener {
            viewModel.setDateMode(AddEventViewModel.DateMode.WEEKLY)
        }
        binding?.modeFree?.setOnClickListener {
            viewModel.setTimeMode(AddEventViewModel.TimeMode.CLOCK)
        }
        binding?.modeBatch?.setOnClickListener {
            viewModel.setTimeMode(AddEventViewModel.TimeMode.PERIOD)
        }

        viewModel.doneLiveData.observe(this) {
            if (it) binding?.adeBtDone?.show() else binding?.adeBtDone?.hide()
        }

        viewModel.contentModeLiveData.observe(this) {
            applyContentModeUi(it, viewModel.selectedEventLiveData.value)
            refreshNameInputByContentMode(it)
        }
        viewModel.selectedEventLiveData.observe(this) {
            applyContentModeUi(viewModel.contentModeLiveData.value ?: AddEventViewModel.ContentMode.CUSTOM, it)
        }
        viewModel.dateModeLiveData.observe(this) {
            applyDateModeUi(it)
            refreshDateText()
        }
        viewModel.timeModeLiveData.observe(this) {
            applyTimeModeUi(it)
            refreshTimeText()
        }

        viewModel.customDateLiveData.observe(this) {
            refreshDateText()
        }
        viewModel.customTimePeriodLiveData.observe(this) {
            refreshTimeText()
        }
        viewModel.timeRangeLiveDate.observe(this) {
            refreshDateText()
            refreshTimeText()
        }

        viewModel.teacherLiveData.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTeacherIcon?.setColorFilter(getColorPrimary())
                binding?.pickTeacherText?.setTextColor(getColorPrimary())
                binding?.pickTeacher?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTeacherText?.text = it.data
                binding?.pickTeacherCancel?.visibility = View.VISIBLE
            } else {
                binding?.pickTeacherText?.text = getString(R.string.ade_pick_teacher)
                binding?.pickTeacher?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTeacherText?.setTextColor(getTextColorSecondary())
                binding?.pickTeacherCancel?.visibility = View.GONE
                binding?.pickTeacherIcon?.clearColorFilter()
            }
        }

        viewModel.locationLiveData.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickLocationIcon?.setColorFilter(getColorPrimary())
                binding?.pickLocationText?.setTextColor(getColorPrimary())
                binding?.pickLocation?.setCardBackgroundColor(getColorPrimary())
                binding?.pickLocationText?.text = it.data
                binding?.pickLocationCancel?.visibility = View.VISIBLE
            } else {
                binding?.pickLocationText?.text = getString(R.string.ade_pick_location)
                binding?.pickLocation?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickLocationText?.setTextColor(getTextColorSecondary())
                binding?.pickLocationIcon?.clearColorFilter()
                binding?.pickLocationCancel?.visibility = View.GONE
            }
        }

        viewModel.timetableLiveData.observe(this) {
            binding?.pickTimetable?.visibility =
                if (it.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTimetableIcon?.setColorFilter(getColorPrimary())
                binding?.pickTimetableText?.setTextColor(getColorPrimary())
                binding?.pickTimetable?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTimetableText?.text = it.data?.name
            } else {
                binding?.pickTimetableText?.text = getString(R.string.ade_pick_timetable)
                binding?.pickTimetable?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTimetableText?.setTextColor(getTextColorSecondary())
                binding?.pickTimetableIcon?.clearColorFilter()
            }
        }

        binding?.pickTimetable?.setOnClickListener {
            DialogSelectableLiveList<Timetable>().setTitle(R.string.ade_pick_timetable)
                .setInitValue(viewModel.timetableLiveData.value?.data)
                .setDataLoader(object : DialogSelectableLiveList.DataLoader<Timetable> {
                    override fun loadData(): LiveData<List<DialogSelectableLiveList.ItemData<Timetable>>> {
                        return TimetableRepository(activity!!.application).getTimetables()
                            .switchMap {
                                val res = mutableListOf<DialogSelectableLiveList.ItemData<Timetable>>()
                                for (data: Timetable in it) {
                                    res.add(DialogSelectableLiveList.ItemData(data.name, data))
                                }
                                MutableLiveData(res)
                            }
                    }
                }).setOnConfirmListener(object :
                    DialogSelectableLiveList.OnConfirmListener<Timetable> {
                    override fun onConfirm(title: String?, key: Timetable) {
                        viewModel.timetableLiveData.value = DataState(key)
                    }
                }).show(childFragmentManager, "set_timetable")
        }

        binding?.pickDate?.setOnClickListener {
            if (viewModel.dateModeLiveData.value == AddEventViewModel.DateMode.WEEKLY) {
                showCourseTimePicker(requireWeeks = true)
            } else {
                val initDate = viewModel.customDateLiveData.value?.data
                PopUpCalendarPicker()
                    .setInitValue(initDate)
                    .setOnConfirmListener(object : PopUpCalendarPicker.OnConfirmListener {
                        override fun onConfirm(c: Calendar) {
                            c.set(Calendar.HOUR_OF_DAY, 0)
                            c.set(Calendar.MINUTE, 0)
                            c.set(Calendar.SECOND, 0)
                            c.set(Calendar.MILLISECOND, 0)
                            viewModel.setCustomDate(c.timeInMillis)
                        }
                    })
                    .show(childFragmentManager, "pick_custom_date")
            }
        }

        binding?.pickTime?.setOnClickListener {
            if (viewModel.timeModeLiveData.value == AddEventViewModel.TimeMode.PERIOD) {
                showCourseTimePicker(requireWeeks = viewModel.dateModeLiveData.value == AddEventViewModel.DateMode.WEEKLY)
            } else {
                val initPeriod = viewModel.customTimePeriodLiveData.value?.data
                PopUpTimePeriodPicker()
                    .setDialogTitle(R.string.ade_pick_time_range)
                    .setInitialValue(initPeriod?.from, initPeriod?.to)
                    .setOnDialogConformListener(object : PopUpTimePeriodPicker.OnDialogConformListener {
                        override fun onClick(timePeriodInDay: TimePeriodInDay) {
                            viewModel.setCustomTimePeriod(
                                TimeInDay(timePeriodInDay.from.hour, timePeriodInDay.from.minute),
                                TimeInDay(timePeriodInDay.to.hour, timePeriodInDay.to.minute)
                            )
                        }
                    }).show(childFragmentManager, "pick_custom_time_range")
            }
        }

        binding?.pickTeacherCancel?.setOnClickListener {
            viewModel.teacherLiveData.value = DataState(DataState.STATE.NOTHING)
        }
        binding?.pickTeacher?.setOnClickListener {
            DialogAutoEditText().setTitle(getString(R.string.ade_pick_teacher))
                .setOnConfirmListener(object : DialogAutoEditText.OnConfirmListener {
                    override fun OnConfirm(content: String) {
                        viewModel.teacherLiveData.value = DataState(content)
                    }
                }).setInitValue(viewModel.teacherLiveData.value?.data ?: "")
                .setDataLoader(object : DialogAutoEditText.DataLoader {
                    override fun loadData(str: String): LiveData<List<String>> {
                        return TeacherInfoRepository(activity!!.application)
                            .searchTeachers(str).switchMap {
                                val r = mutableListOf<String>()
                                it.data?.let { dt ->
                                    for (t in dt) {
                                        r.add(t.name)
                                    }
                                }
                                MutableLiveData(r)
                            }
                    }
                }).show(childFragmentManager, "pick_teacher")
        }

        binding?.pickLocationCancel?.setOnClickListener {
            viewModel.locationLiveData.value = DataState(DataState.STATE.NOTHING)
        }
        binding?.pickLocation?.setOnClickListener {
            DialogAutoEditText().setTitle(getString(R.string.ade_pick_location))
                .setOnConfirmListener(object : DialogAutoEditText.OnConfirmListener {
                    override fun OnConfirm(content: String) {
                        viewModel.locationLiveData.value = DataState(content)
                    }
                }).setInitValue(viewModel.locationLiveData.value?.data ?: "")
                .setDataLoader(object : DialogAutoEditText.DataLoader {
                    override fun loadData(str: String): LiveData<List<String>> {
                        return TimetableRepository(activity!!.application)
                            .searchLocation(str)
                    }
                }).show(childFragmentManager, "pick_location")
        }

        binding?.adeBtDone?.setOnClickListener {
            viewModel.createEvent()
            activity?.let { WidgetUtils.sendRefreshToAll(it) }
            dismiss()
        }
        binding?.name?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                viewModel.nameLiveData.value = p0.toString()
            }
        })

        viewModel.init(addSubjectMode, initTimetable, initSubject, initCourseTime)
    }

    private fun showCourseTimePicker(requireWeeks: Boolean) {
        viewModel.timetableLiveData.value?.data?.let { tt ->
            PopUpPickCourseTime(tt)
                .setMode(if (requireWeeks) PopUpPickCourseTime.Mode.DATE_AND_PERIOD else PopUpPickCourseTime.Mode.PERIOD_ONLY)
                .setInitialValue(tt, viewModel.timeRangeLiveDate.value?.data)
                .setSelectListener(object : PopUpPickCourseTime.OnTimeSelectedListener {
                    override fun onSelected(data: CourseTime) {
                        if (requireWeeks && data.weeks.isEmpty()) {
                            viewModel.timeRangeLiveDate.value = DataState(DataState.STATE.NOTHING)
                        } else {
                            viewModel.timeRangeLiveDate.value = DataState(data)
                        }
                    }
                }).show(childFragmentManager, "pick_course_time")
        }
    }

    private fun applyContentModeUi(
        mode: AddEventViewModel.ContentMode,
        state: DataState<EventItem>?,
    ) {
        val isCustom = mode == AddEventViewModel.ContentMode.CUSTOM
        binding?.contentCustom?.setCardBackgroundColor(if (isCustom) getColorPrimary() else getTextColorSecondary())
        binding?.contentCustomText?.setTextColor(if (isCustom) getColorPrimary() else getTextColorSecondary())
        binding?.pickSubject?.setCardBackgroundColor(if (isCustom) getTextColorSecondary() else getColorPrimary())
        binding?.pickSubjectText?.setTextColor(if (isCustom) getTextColorSecondary() else getColorPrimary())
        binding?.pickSubjectIcon?.setColorFilter(if (isCustom) getTextColorSecondary() else getColorPrimary())
        binding?.pickSubjectText?.text = if (state?.state == DataState.STATE.SUCCESS) {
            state.data?.name ?: getString(R.string.ade_content_existing)
        } else {
            getString(R.string.ade_content_existing)
        }
        binding?.contentSourceRow?.visibility = if (addSubjectMode) View.GONE else View.VISIBLE
    }

    private fun applyDateModeUi(mode: AddEventViewModel.DateMode) {
        val isSingle = mode == AddEventViewModel.DateMode.SINGLE
        binding?.dateSingle?.setCardBackgroundColor(if (isSingle) getColorPrimary() else getTextColorSecondary())
        binding?.dateSingleText?.setTextColor(if (isSingle) getColorPrimary() else getTextColorSecondary())
        binding?.dateWeekly?.setCardBackgroundColor(if (isSingle) getTextColorSecondary() else getColorPrimary())
        binding?.dateWeeklyText?.setTextColor(if (isSingle) getTextColorSecondary() else getColorPrimary())
    }

    private fun applyTimeModeUi(mode: AddEventViewModel.TimeMode) {
        val isPeriod = mode == AddEventViewModel.TimeMode.PERIOD
        binding?.modeBatch?.setCardBackgroundColor(if (isPeriod) getColorPrimary() else getTextColorSecondary())
        binding?.modeBatchText?.setTextColor(if (isPeriod) getColorPrimary() else getTextColorSecondary())
        binding?.modeFree?.setCardBackgroundColor(if (isPeriod) getTextColorSecondary() else getColorPrimary())
        binding?.modeFreeText?.setTextColor(if (isPeriod) getTextColorSecondary() else getColorPrimary())
        binding?.agentTraceContainer?.visibility = View.GONE
    }

    private fun refreshNameInputByContentMode(mode: AddEventViewModel.ContentMode) {
        val isCustom = mode == AddEventViewModel.ContentMode.CUSTOM || addSubjectMode
        binding?.name?.isEnabled = isCustom
        binding?.name?.visibility = if (isCustom) View.VISIBLE else View.GONE
        binding?.adeNamelayout?.visibility = if (isCustom) View.VISIBLE else View.GONE
        binding?.name?.hint = getString(R.string.ade_namehint)
    }

    private fun refreshDateText() {
        val mode = viewModel.dateModeLiveData.value ?: AddEventViewModel.DateMode.SINGLE
        val selected = when (mode) {
            AddEventViewModel.DateMode.SINGLE -> {
                val state = viewModel.customDateLiveData.value
                if (state?.state == DataState.STATE.SUCCESS) formatDateLabel(state.data ?: 0L) else null
            }

            AddEventViewModel.DateMode.WEEKLY -> {
                val state = viewModel.timeRangeLiveDate.value
                if (state?.state == DataState.STATE.SUCCESS) formatWeeklyLabel(state.data) else null
            }
        }
        val isSelected = !selected.isNullOrBlank()
        binding?.pickDate?.setCardBackgroundColor(if (isSelected) getColorPrimary() else getTextColorSecondary())
        binding?.pickDateIcon?.setColorFilter(if (isSelected) getColorPrimary() else getTextColorSecondary())
        binding?.dateShow?.setTextColor(if (isSelected) getColorPrimary() else getTextColorSecondary())
        binding?.dateShow?.text = selected ?: if (mode == AddEventViewModel.DateMode.WEEKLY) {
            getString(R.string.ade_pick_weekly_date)
        } else {
            getString(R.string.ade_set_date)
        }
    }

    private fun refreshTimeText() {
        val mode = viewModel.timeModeLiveData.value ?: AddEventViewModel.TimeMode.PERIOD
        val selected = when (mode) {
            AddEventViewModel.TimeMode.CLOCK -> {
                val state = viewModel.customTimePeriodLiveData.value
                if (state?.state == DataState.STATE.SUCCESS) formatTimePeriodLabel(state.data) else null
            }

            AddEventViewModel.TimeMode.PERIOD -> {
                val state = viewModel.timeRangeLiveDate.value
                if (state?.state == DataState.STATE.SUCCESS) formatPeriodLabel(state.data) else null
            }
        }
        val isSelected = !selected.isNullOrBlank()
        binding?.pickTime?.setCardBackgroundColor(if (isSelected) getColorPrimary() else getTextColorSecondary())
        binding?.pickTimeIcon?.setColorFilter(if (isSelected) getColorPrimary() else getTextColorSecondary())
        binding?.timeShow?.setTextColor(if (isSelected) getColorPrimary() else getTextColorSecondary())
        binding?.timeShow?.text = selected ?: if (mode == AddEventViewModel.TimeMode.PERIOD) {
            getString(R.string.ade_pick_time)
        } else {
            getString(R.string.ade_pick_time_range)
        }
    }

    private fun showExistingEventPicker() {
        val timetable = viewModel.timetableLiveData.value?.data ?: return
        DialogSelectableLiveList<EventItem>().setTitle(R.string.ade_content_existing)
            .setInitValue(viewModel.selectedEventLiveData.value?.data)
            .setDataLoader(object : DialogSelectableLiveList.DataLoader<EventItem> {
                override fun loadData(): LiveData<List<DialogSelectableLiveList.ItemData<EventItem>>> {
                    return TimetableRepository(requireActivity().application)
                        .getEventsOfTimetable(timetable.id)
                        .switchMap { events ->
                            MutableLiveData(
                                events
                                    .filter { it.type != EventItem.TYPE.TAG }
                                    .distinctBy {
                                        listOf(
                                            it.name.trim(),
                                            it.place.orEmpty().trim(),
                                            it.teacher.orEmpty().trim(),
                                            it.subjectId,
                                            it.type.name,
                                        ).joinToString("|")
                                    }
                                    .sortedWith(compareBy<EventItem> { it.name }.thenBy { it.from.time })
                                    .map {
                                        DialogSelectableLiveList.ItemData(formatEventTemplateLabel(it), it)
                                    }
                            )
                        }
                }
            }).setOnConfirmListener(object :
                DialogSelectableLiveList.OnConfirmListener<EventItem> {
                override fun onConfirm(title: String?, key: EventItem) {
                    viewModel.selectExistingEvent(key)
                    binding?.name?.setText(key.name)
                }
            }).show(childFragmentManager, "pick_existing_event")
    }

    private fun formatDateLabel(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    private fun formatWeeklyLabel(courseTime: CourseTime?): String {
        if (courseTime == null) return ""
        val dowArray = resources.getStringArray(R.array.dow1)
        val dowIndex = (courseTime.dow - 1).coerceIn(0, dowArray.size - 1)
        val weeks = formatWeeks(courseTime.weeks)
        return if (weeks.isBlank()) {
            dowArray[dowIndex]
        } else {
            "${weeks}周 ${dowArray[dowIndex]}"
        }
    }

    private fun formatPeriodLabel(courseTime: CourseTime?): String {
        if (courseTime == null) return ""
        val timetable = viewModel.timetableLiveData.value?.data
        val nums = timetable?.transformCourseNumber(courseTime.period)
        return if (nums != null && nums.first > 0) {
            "第${nums.first}-${nums.second}节 ${courseTime.period.from}-${courseTime.period.to}"
        } else {
            "${courseTime.period.from}-${courseTime.period.to}"
        }
    }

    private fun formatWeeks(weeks: List<Int>): String {
        val set = weeks.toSet()
        val frags = mutableListOf<String>()
        for (s in set.sorted()) {
            if (set.contains(s - 1)) continue
            var ts = s
            while (set.contains(ts + 1)) {
                ts++
            }
            when (ts) {
                s -> frags.add("$s")
                s + 1 -> {
                    frags.add("$s")
                    frags.add("$ts")
                }

                else -> frags.add("$s-$ts")
            }
        }
        return frags.joinToString(", ")
    }

    private fun formatEventTemplateLabel(event: EventItem): String {
        return listOf(event.name, event.place.orEmpty(), event.teacher.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" / ")
    }

    private fun formatTimePeriodLabel(period: TimePeriodInDay?): String {
        if (period == null) return getString(R.string.ade_pick_time_range)
        return "${period.from}-${period.to}"
    }
}
