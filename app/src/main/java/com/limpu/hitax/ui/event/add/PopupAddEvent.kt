package com.limpu.hitax.ui.event.add

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.limpu.component.data.DataState
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TermSubject
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.TeacherInfoRepository
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.widgets.PopUpCalendarPicker
import com.limpu.hitax.ui.widgets.PopUpPickCourseTime
import com.limpu.hitax.ui.widgets.PopUpTimePeriodPicker
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.style.widgets.DialogAutoEditText
import com.limpu.style.widgets.DialogSelectableLiveList
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class PopupAddEvent(private val addSubjectMode: Boolean = false) :
    BottomSheetDialogFragment() {

    var initTimetable: Timetable? = null
    var initSubject: TermSubject? = null
    var initCourseTime: CourseTime? = null
    var editEvent: EventItem? = null
    private lateinit var viewModel: AddEventViewModel

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

    fun setEditEvent(event: EventItem): PopupAddEvent {
        editEvent = event
        val ct = CourseTime()
        ct.dow = event.getDow()
        event.fromNumber.takeIf { it > 0 }?.let { from ->
            val to = from + event.lastNumber - 1
            initTimetable?.let { timetable ->
                ct.period = timetable.transformTimePeriod(from, to)
            }
        } ?: run {
            ct.period = TimePeriodInDay(TimeInDay(event.from), TimeInDay(event.to))
        }
        initCourseTime = ct
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = androidx.lifecycle.ViewModelProvider(this)[AddEventViewModel::class.java]
        return ComposeView(requireContext()).apply {
            setContent {
                HitaComposeTheme() {
                    PopupAddEventScreen(
                        viewModel = viewModel,
                        addSubjectMode = addSubjectMode,
                        editEvent = editEvent,
                        onDismiss = { dismiss() },
                        onShowTimetablePicker = { showTimetablePicker() },
                        onShowExistingEventPicker = { showExistingEventPicker() },
                        onShowDatePicker = { showDatePicker() },
                        onShowTimePicker = { showTimePicker() },
                        onShowTeacherPicker = { showTeacherPicker() },
                        onShowLocationPicker = { showLocationPicker() },
                        onShowCourseTimePicker = { mode -> showCourseTimePicker(mode) }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.init(addSubjectMode, initTimetable, initSubject, initCourseTime, editEvent)
    }

    private fun showTimetablePicker() {
        DialogSelectableLiveList<Timetable>().setTitle(R.string.ade_pick_timetable)
            .setInitValue(viewModel.timetableLiveData.value?.data)
            .setDataLoader(object : DialogSelectableLiveList.DataLoader<Timetable> {
                override fun loadData(): LiveData<List<DialogSelectableLiveList.ItemData<Timetable>>> {
                    return TimetableRepository(requireActivity().application).getTimetables()
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
                }
            }).show(childFragmentManager, "pick_existing_event")
    }

    private fun showDatePicker() {
        if (viewModel.dateModeLiveData.value == AddEventViewModel.DateMode.WEEKLY) {
            showCourseTimePicker(PopUpPickCourseTime.Mode.DATE_ONLY)
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

    private fun showTimePicker() {
        if (viewModel.timeModeLiveData.value == AddEventViewModel.TimeMode.PERIOD) {
            showCourseTimePicker(PopUpPickCourseTime.Mode.PERIOD_ONLY)
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

    private fun showTeacherPicker() {
        DialogAutoEditText().setTitle(getString(R.string.ade_optional_teacher_note))
            .setOnConfirmListener(object : DialogAutoEditText.OnConfirmListener {
                override fun OnConfirm(content: String) {
                    viewModel.teacherLiveData.value = DataState(content)
                }
            }).setInitValue(viewModel.teacherLiveData.value?.data ?: "")
            .setDataLoader(object : DialogAutoEditText.DataLoader {
                override fun loadData(str: String): LiveData<List<String>> {
                    return TeacherInfoRepository(requireActivity().application)
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

    private fun showLocationPicker() {
        DialogAutoEditText().setTitle(getString(R.string.ade_optional_location))
            .setOnConfirmListener(object : DialogAutoEditText.OnConfirmListener {
                override fun OnConfirm(content: String) {
                    viewModel.locationLiveData.value = DataState(content)
                }
            }).setInitValue(viewModel.locationLiveData.value?.data ?: "")
            .setDataLoader(object : DialogAutoEditText.DataLoader {
                override fun loadData(str: String): LiveData<List<String>> {
                    return TimetableRepository(requireActivity().application)
                        .searchLocation(str)
                }
            }).show(childFragmentManager, "pick_location")
    }

    private fun showCourseTimePicker(mode: PopUpPickCourseTime.Mode) {
        viewModel.timetableLiveData.value?.data?.let { tt ->
            PopUpPickCourseTime(tt)
                .setMode(mode)
                .setInitialValue(tt, viewModel.timeRangeLiveDate.value?.data)
                .setSelectListener(object : PopUpPickCourseTime.OnTimeSelectedListener {
                    override fun onSelected(data: CourseTime) {
                        viewModel.mergeCourseTimeSelection(data, mode == PopUpPickCourseTime.Mode.DATE_ONLY)
                    }
                }).show(childFragmentManager, "pick_course_time")
        }
    }

    private fun formatEventTemplateLabel(event: EventItem): String {
        return listOf(event.name, event.place.orEmpty(), event.teacher.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" / ")
    }
}

@SuppressLint("SetTextI18n")
@Composable
private fun PopupAddEventScreen(
    viewModel: AddEventViewModel,
    addSubjectMode: Boolean,
    editEvent: EventItem?,
    onDismiss: () -> Unit,
    onShowTimetablePicker: () -> Unit,
    onShowExistingEventPicker: () -> Unit,
    onShowDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit,
    onShowTeacherPicker: () -> Unit,
    onShowLocationPicker: () -> Unit,
    onShowCourseTimePicker: (PopUpPickCourseTime.Mode) -> Unit
) {
    val tokens = HitaTheme.tokens

    val doneLiveData by viewModel.doneLiveData.observeAsState()
    val contentMode by viewModel.contentModeLiveData.observeAsState()
    val selectedEvent by viewModel.selectedEventLiveData.observeAsState()
    val dateMode by viewModel.dateModeLiveData.observeAsState()
    val timeMode by viewModel.timeModeLiveData.observeAsState()
    val customDate by viewModel.customDateLiveData.observeAsState()
    val customTimePeriod by viewModel.customTimePeriodLiveData.observeAsState()
    val timeRange by viewModel.timeRangeLiveDate.observeAsState()
    val teacherState by viewModel.teacherLiveData.observeAsState()
    val locationState by viewModel.locationLiveData.observeAsState()
    val timetableState by viewModel.timetableLiveData.observeAsState()

    var nameText by remember { mutableStateOf(editEvent?.name.orEmpty()) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg)
        ) {
            // Title
            val titleText = when {
                editEvent != null -> stringResource(R.string.ade_title_edit, editEvent.name.orEmpty())
                addSubjectMode -> stringResource(R.string.add_subject)
                else -> stringResource(R.string.ade_title)
            }
            Text(
                text = titleText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = tokens.spacing.xs)
            )

            // Timetable picker
            val ttState = timetableState
            if (ttState?.state != DataState.STATE.FETCH_FAILED) {
                val ttSelected = ttState?.state == DataState.STATE.SUCCESS
                PickerCard(
                    iconRes = R.drawable.ic_timetable,
                    text = ttState?.data?.name ?: stringResource(R.string.ade_pick_timetable),
                    isSelected = ttSelected,
                    onClick = onShowTimetablePicker
                )
            }

            // Content section
            Text(
                text = stringResource(R.string.ade_section_content),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spacing.md)
            )

            if (addSubjectMode || editEvent == null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val isCustom = contentMode == AddEventViewModel.ContentMode.CUSTOM
                    PickerCard(
                        iconRes = R.drawable.ic_baseline_timetable_24,
                        text = selectedEvent?.data?.name ?: stringResource(R.string.ade_content_existing),
                        isSelected = !isCustom,
                        onClick = onShowExistingEventPicker,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.xs))
                    PickerCard(
                        text = stringResource(R.string.ade_content_custom),
                        isSelected = isCustom,
                        onClick = { viewModel.setContentMode(AddEventViewModel.ContentMode.CUSTOM) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val isCustomContent = contentMode == AddEventViewModel.ContentMode.CUSTOM || addSubjectMode || editEvent != null
            if (isCustomContent) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        viewModel.nameLiveData.value = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.xs),
                    label = { Text(stringResource(R.string.ade_namehint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
            }

            // Date section
            Text(
                text = stringResource(R.string.ade_section_date),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spacing.md)
            )

            if (editEvent == null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val isSingle = dateMode == AddEventViewModel.DateMode.SINGLE
                    PickerCard(
                        text = stringResource(R.string.ade_date_single),
                        isSelected = isSingle,
                        onClick = { viewModel.setDateMode(AddEventViewModel.DateMode.SINGLE) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.xs))
                    PickerCard(
                        text = stringResource(R.string.ade_date_weekly),
                        isSelected = !isSingle,
                        onClick = { viewModel.setDateMode(AddEventViewModel.DateMode.WEEKLY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val dateLabel = remember(dateMode, customDate, timeRange) {
                when (dateMode) {
                    AddEventViewModel.DateMode.SINGLE -> {
                        val state = customDate
                        if (state?.state == DataState.STATE.SUCCESS) {
                            val c = Calendar.getInstance()
                            c.timeInMillis = state.data ?: 0L
                            "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
                        } else null
                    }
                    AddEventViewModel.DateMode.WEEKLY -> {
                        val state = timeRange
                        if (state?.state == DataState.STATE.SUCCESS) {
                            formatWeeklyLabel(state.data)
                        } else null
                    }
                    else -> null
                }
            }
            PickerCard(
                iconRes = R.drawable.ic_baseline_date_range_24,
                text = dateLabel ?: if (dateMode == AddEventViewModel.DateMode.WEEKLY) {
                    stringResource(R.string.ade_pick_weekly_date)
                } else {
                    stringResource(R.string.ade_set_date)
                },
                isSelected = dateLabel != null,
                onClick = onShowDatePicker
            )

            // Time section
            Text(
                text = stringResource(R.string.ade_section_time),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spacing.md)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                val isPeriod = timeMode == AddEventViewModel.TimeMode.PERIOD
                PickerCard(
                    text = stringResource(R.string.ade_time_clock),
                    isSelected = !isPeriod,
                    onClick = { viewModel.setTimeMode(AddEventViewModel.TimeMode.CLOCK) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(tokens.spacing.xs))
                PickerCard(
                    text = stringResource(R.string.ade_time_period),
                    isSelected = isPeriod,
                    onClick = { viewModel.setTimeMode(AddEventViewModel.TimeMode.PERIOD) },
                    modifier = Modifier.weight(1f)
                )
            }

            val timeLabel = remember(timeMode, customTimePeriod, timeRange) {
                when (timeMode) {
                    AddEventViewModel.TimeMode.CLOCK -> {
                        val state = customTimePeriod
                        if (state?.state == DataState.STATE.SUCCESS) {
                            val p = state.data
                            if (p != null) "${p.from}-${p.to}" else null
                        } else null
                    }
                    AddEventViewModel.TimeMode.PERIOD -> {
                        val state = timeRange
                        if (state?.state == DataState.STATE.SUCCESS) {
                            val ct = state.data
                            if (ct != null) "第${ct.period.from}-${ct.period.to}节" else null
                        } else null
                    }
                    else -> null
                }
            }
            PickerCard(
                iconRes = R.drawable.ic_baseline_access_time_24,
                text = timeLabel ?: if (timeMode == AddEventViewModel.TimeMode.PERIOD) {
                    stringResource(R.string.ade_pick_time)
                } else {
                    stringResource(R.string.ade_pick_time_range)
                },
                isSelected = timeLabel != null,
                onClick = onShowTimePicker
            )

            // Location picker
            val locSelected = locationState?.state == DataState.STATE.SUCCESS
            PickerCard(
                iconRes = R.drawable.ic_baseline_location_24,
                text = locationState?.data ?: stringResource(R.string.ade_optional_location),
                isSelected = locSelected,
                onClick = onShowLocationPicker,
                showCancel = locSelected,
                onCancel = { viewModel.locationLiveData.value = DataState(DataState.STATE.NOTHING) }
            )

            // Teacher picker
            val teacherSelected = teacherState?.state == DataState.STATE.SUCCESS
            PickerCard(
                iconRes = R.drawable.ic_teacher,
                text = teacherState?.data ?: stringResource(R.string.ade_optional_teacher_note),
                isSelected = teacherSelected,
                onClick = onShowTeacherPicker,
                showCancel = teacherSelected,
                onCancel = { viewModel.teacherLiveData.value = DataState(DataState.STATE.NOTHING) }
            )
        }

        // Done FAB
        if (doneLiveData == true) {
            val context = LocalContext.current
            FloatingActionButton(
                onClick = {
                    viewModel.createEvent()
                    WidgetUtils.sendRefreshToAll(context)
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(tokens.spacing.lg),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_done_white_48dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PickerCard(
    iconRes: Int? = null,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCancel: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    val tokens = HitaTheme.tokens

    Card(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = tokens.spacing.xs)
            .height(40.dp),
        shape = RoundedCornerShape(tokens.radius.sm),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(tokens.spacing.xs))
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showCancel && onCancel != null) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_cancel_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatWeeklyLabel(courseTime: CourseTime?): String {
    if (courseTime == null) return ""
    val weeks = formatWeeks(courseTime.weeks)
    return if (weeks.isBlank()) {
        "周${courseTime.dow}"
    } else {
        "${weeks}周 周${courseTime.dow}"
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
