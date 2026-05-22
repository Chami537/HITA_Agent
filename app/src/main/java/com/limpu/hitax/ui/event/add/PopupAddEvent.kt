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
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.SubjectRepository
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

        binding?.modeBatch?.setOnClickListener {
            viewModel.setAddMode(AddEventViewModel.AddMode.BATCH_PERIOD)
        }
        binding?.modeFree?.setOnClickListener {
            viewModel.setAddMode(AddEventViewModel.AddMode.FREE_RANGE)
        }
        binding?.pickSubject?.setOnClickListener {
            showSubjectPicker()
        }

        viewModel.doneLiveData.observe(this) {
            if (it) binding?.adeBtDone?.show() else binding?.adeBtDone?.hide()
        }

        viewModel.addModeLiveData.observe(this) { mode ->
            applyModeUi(mode)
            refreshSubjectChip(viewModel.subjectLiveData.value)
            refreshTeacherVisibility(viewModel.teacherLiveData.value)
            refreshTimeTextByMode(mode)
        }

        viewModel.contentModeLiveData.observe(this) {
            refreshSubjectChip(viewModel.subjectLiveData.value)
            refreshNameInputByContentMode(it)
        }

        viewModel.customDateLiveData.observe(this) { state ->
            if (state.state == DataState.STATE.SUCCESS) {
                binding?.pickDate?.setCardBackgroundColor(getColorPrimary())
                binding?.pickDateIcon?.setColorFilter(getColorPrimary())
                binding?.dateShow?.setTextColor(getColorPrimary())
                binding?.dateShow?.text = formatDateLabel(state.data ?: 0L)
            } else {
                binding?.pickDate?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickDateIcon?.clearColorFilter()
                binding?.dateShow?.setTextColor(getTextColorSecondary())
                binding?.dateShow?.text = getString(R.string.ade_set_date)
            }
        }

        viewModel.customTimePeriodLiveData.observe(this) {
            if (viewModel.addModeLiveData.value == AddEventViewModel.AddMode.FREE_RANGE) {
                if (it.state == DataState.STATE.SUCCESS) {
                    binding?.pickTime?.setCardBackgroundColor(getColorPrimary())
                    binding?.pickTimeIcon?.setColorFilter(getColorPrimary())
                    binding?.timeShow?.setTextColor(getColorPrimary())
                    binding?.timeShow?.text = formatTimePeriodLabel(it.data)
                } else {
                    binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                    binding?.pickTimeIcon?.clearColorFilter()
                    binding?.timeShow?.setTextColor(getTextColorSecondary())
                    binding?.timeShow?.text = getString(R.string.ade_pick_time_range)
                }
            }
        }

        viewModel.teacherLiveData.observe(this) {
            refreshTeacherVisibility(it)
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTeacherIcon?.setColorFilter(getColorPrimary())
                binding?.pickTeacherText?.setTextColor(getColorPrimary())
                binding?.pickTeacher?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTeacherText?.text = it.data
                binding?.pickTeacherCancel?.visibility = if (viewModel.addModeLiveData.value == AddEventViewModel.AddMode.FREE_RANGE) View.GONE else View.VISIBLE
            } else {
                binding?.pickTeacherText?.text = getString(R.string.ade_pick_teacher)
                binding?.pickTeacher?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTeacherText?.setTextColor(getTextColorSecondary())
                binding?.pickTeacherCancel?.visibility = View.GONE
                binding?.pickTeacherIcon?.clearColorFilter()
            }
        }

        viewModel.locationLiveData.observe(this) {
            binding?.pickLocation?.visibility =
                if (it.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
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

        viewModel.subjectLiveData.observe(this) {
            if (it.state == DataState.STATE.SUCCESS) {
                if (viewModel.contentModeLiveData.value == AddEventViewModel.ContentMode.SUBJECT) {
                    binding?.name?.setText(it.data?.name)
                }
            }
            refreshSubjectChip(it)
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

        viewModel.timeRangeLiveDate.observe(this) {
            if (viewModel.addModeLiveData.value != AddEventViewModel.AddMode.BATCH_PERIOD) return@observe
            binding?.pickTime?.visibility =
                if (it.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
            if (it.state == DataState.STATE.SUCCESS) {
                binding?.pickTime?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTimeIcon?.setColorFilter(getColorPrimary())
                binding?.timeShow?.setTextColor(getColorPrimary())
                if (viewModel.timetableLiveData.value?.data != null) {
                    it.data?.let { ct ->
                        val dowArray = resources.getStringArray(R.array.dow1)
                        val dowIndex = (ct.dow - 1).coerceIn(0, dowArray.size - 1)
                        val t1 = dowArray[dowIndex].toString() +
                                " " + ct.period.from.toString() + "-" + ct.period.to.toString()
                        val set = HashSet<Int>()
                        val frags = mutableListOf<String>()
                        for (i in ct.weeks) {
                            set.add(i)
                        }
                        for (s in set) {
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
                        binding?.timeShow?.text = "${frags.joinToString(", ")}周 $t1"
                    }
                }
            } else {
                binding?.timeShow?.text = getString(R.string.ade_pick_time)
                binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                binding?.timeShow?.setTextColor(getTextColorSecondary())
                binding?.pickTimeIcon?.clearColorFilter()
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

        binding?.pickTime?.setOnClickListener {
            when (viewModel.addModeLiveData.value ?: AddEventViewModel.AddMode.BATCH_PERIOD) {
                AddEventViewModel.AddMode.BATCH_PERIOD -> {
                    viewModel.timetableLiveData.value?.data?.let { tt ->
                        PopUpPickCourseTime(tt)
                            .setInitialValue(tt, viewModel.timeRangeLiveDate.value?.data)
                            .setSelectListener(object : PopUpPickCourseTime.OnTimeSelectedListener {
                                override fun onSelected(data: CourseTime) {
                                    if (data.weeks.isEmpty()) {
                                        viewModel.timeRangeLiveDate.value = DataState(DataState.STATE.NOTHING)
                                    } else {
                                        viewModel.timeRangeLiveDate.value = DataState(data)
                                    }
                                }
                            }).show(childFragmentManager, "pick_course_time")
                    }
                }

                AddEventViewModel.AddMode.FREE_RANGE -> {
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

    private fun applyModeUi(mode: AddEventViewModel.AddMode) {
        val isBatch = mode == AddEventViewModel.AddMode.BATCH_PERIOD
        binding?.pickDate?.visibility = if (isBatch) View.GONE else View.VISIBLE
        if (isBatch) {
            binding?.pickTime?.visibility =
                if (viewModel.timeRangeLiveDate.value?.state == DataState.STATE.FETCH_FAILED) View.GONE else View.VISIBLE
        } else {
            binding?.pickTime?.visibility = View.VISIBLE
        }
        binding?.modeBatch?.setCardBackgroundColor(if (isBatch) getColorPrimary() else getTextColorSecondary())
        binding?.modeBatchText?.setTextColor(if (isBatch) getColorPrimary() else getTextColorSecondary())
        binding?.modeFree?.setCardBackgroundColor(if (isBatch) getTextColorSecondary() else getColorPrimary())
        binding?.modeFreeText?.setTextColor(if (isBatch) getTextColorSecondary() else getColorPrimary())
        binding?.agentTraceContainer?.visibility = View.GONE
    }

    private fun refreshTeacherVisibility(state: DataState<String>?) {
        val hiddenByState = state?.state == DataState.STATE.FETCH_FAILED
        binding?.pickTeacher?.visibility = if (hiddenByState) View.GONE else View.VISIBLE
    }

    private fun refreshTimeTextByMode(mode: AddEventViewModel.AddMode) {
        if (mode == AddEventViewModel.AddMode.BATCH_PERIOD) {
            if (viewModel.timeRangeLiveDate.value?.state != DataState.STATE.SUCCESS) {
                binding?.timeShow?.text = getString(R.string.ade_pick_time)
                binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                binding?.timeShow?.setTextColor(getTextColorSecondary())
                binding?.pickTimeIcon?.clearColorFilter()
            }
        } else {
            val periodState = viewModel.customTimePeriodLiveData.value
            if (periodState?.state == DataState.STATE.SUCCESS) {
                binding?.timeShow?.text = formatTimePeriodLabel(periodState.data)
                binding?.pickTime?.setCardBackgroundColor(getColorPrimary())
                binding?.pickTimeIcon?.setColorFilter(getColorPrimary())
                binding?.timeShow?.setTextColor(getColorPrimary())
            } else {
                binding?.timeShow?.text = getString(R.string.ade_pick_time_range)
                binding?.pickTime?.setCardBackgroundColor(getTextColorSecondary())
                binding?.pickTimeIcon?.clearColorFilter()
                binding?.timeShow?.setTextColor(getTextColorSecondary())
            }
        }
    }

    private fun refreshSubjectChip(state: DataState<TermSubject>?) {
        if (addSubjectMode) {
            binding?.pickSubject?.visibility = View.GONE
            return
        }
        binding?.pickSubject?.visibility = View.VISIBLE
        if (viewModel.contentModeLiveData.value == AddEventViewModel.ContentMode.SUBJECT &&
            state?.state == DataState.STATE.SUCCESS
        ) {
            binding?.pickSubjectIcon?.setColorFilter(getColorPrimary())
            binding?.pickSubjectText?.setTextColor(getColorPrimary())
            binding?.pickSubject?.setCardBackgroundColor(getColorPrimary())
            binding?.pickSubjectText?.text = state.data?.name ?: getString(R.string.ade_pick_subject)
        } else {
            binding?.pickSubjectText?.text = getString(R.string.ade_content_custom)
            binding?.pickSubject?.setCardBackgroundColor(getTextColorSecondary())
            binding?.pickSubjectText?.setTextColor(getTextColorSecondary())
            binding?.pickSubjectIcon?.clearColorFilter()
        }
    }

    private fun refreshNameInputByContentMode(mode: AddEventViewModel.ContentMode) {
        val wasSubjectMode = binding?.name?.isEnabled == false
        binding?.name?.isEnabled = mode == AddEventViewModel.ContentMode.CUSTOM
        binding?.name?.hint = if (mode == AddEventViewModel.ContentMode.SUBJECT) {
            getString(R.string.ade_pick_subject)
        } else {
            getString(R.string.ade_namehint)
        }
        if (mode == AddEventViewModel.ContentMode.CUSTOM && wasSubjectMode) {
            binding?.name?.setText("")
        }
    }

    private fun showSubjectPicker() {
        val timetable = viewModel.timetableLiveData.value?.data ?: return
        val customSubject = TermSubject().apply {
            id = "__custom_schedule__"
            name = getString(R.string.ade_content_custom)
            timetableId = timetable.id
            type = TermSubject.TYPE.TAG
        }
        DialogSelectableLiveList<TermSubject>().setTitle(R.string.ade_pick_subject)
            .setInitValue(viewModel.subjectLiveData.value?.data ?: customSubject)
            .setDataLoader(object : DialogSelectableLiveList.DataLoader<TermSubject> {
                override fun loadData(): LiveData<List<DialogSelectableLiveList.ItemData<TermSubject>>> {
                    return SubjectRepository(requireActivity().application)
                        .getSubjects(timetable.id)
                        .switchMap { subjects ->
                            val res = mutableListOf(
                                DialogSelectableLiveList.ItemData(
                                    getString(R.string.ade_content_custom),
                                    customSubject,
                                )
                            )
                            for (subject in subjects) {
                                if (subject.type != TermSubject.TYPE.TAG) {
                                    res.add(DialogSelectableLiveList.ItemData(subject.name, subject))
                                }
                            }
                            MutableLiveData(res)
                        }
                }
            }).setOnConfirmListener(object :
                DialogSelectableLiveList.OnConfirmListener<TermSubject> {
                override fun onConfirm(title: String?, key: TermSubject) {
                    if (key.id == customSubject.id) {
                        viewModel.setContentMode(AddEventViewModel.ContentMode.CUSTOM)
                    } else {
                        viewModel.selectSubject(key)
                    }
                }
            }).show(childFragmentManager, "pick_subject")
    }

    private fun formatDateLabel(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    private fun formatTimePeriodLabel(period: TimePeriodInDay?): String {
        if (period == null) return getString(R.string.ade_pick_time_range)
        return "${period.from}-${period.to}"
    }
}
