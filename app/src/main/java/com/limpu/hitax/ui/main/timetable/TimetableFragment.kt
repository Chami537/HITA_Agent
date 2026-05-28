package com.limpu.hitax.ui.main.timetable

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.data.model.timetable.TimePeriodInDay
import com.limpu.hitax.data.model.timetable.Timetable
import com.limpu.hitax.data.repository.TimetableRepository
import com.limpu.hitax.ui.base.ComposeViewBinding
import com.limpu.hitax.ui.base.HiltBaseFragment
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.event.add.PopupAddEvent
import com.limpu.hitax.ui.main.timetable.views.TimetableOverlapLayout
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.EventsUtils
import com.limpu.hitax.utils.TimeTools
import dagger.hilt.android.AndroidEntryPoint
import tyrantgit.explosionfield.ExplosionField
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class TimetableFragment : HiltBaseFragment<ComposeViewBinding>() {

    protected val viewModel: TimetableViewModel by viewModels()
    private var mainPageController: MainPageController? = null

    companion object {
        const val WINDOW_SIZE: Int = 5
        const val WEEK_MILLS: Long = 1000 * 60 * 60 * 24 * 7
    }

    override fun initViewBinding(): ComposeViewBinding {
        return ComposeViewBinding(ComposeView(requireContext()))
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainPageController) {
            mainPageController = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        mainPageController = null
    }

    override fun onStart() {
        super.onStart()
        viewModel.startRefresh()
    }

    override fun initViews(view: View) {
        (binding?.root as? ComposeView)?.setContent {
            HitaComposeTheme() {
                TimetableScreen(
                    viewModel = viewModel,
                    onTitleState = ::applyTitleState,
                    onEventClick = { EventsUtils.showEventItem(requireActivity(), it) },
                    onEventLongClick = { event -> showEventMenu(event) },
                    onAddClick = { dow, period -> showAddEvent(dow, period) },
                )
            }
        }
    }

    private fun applyTitleState(state: TimetableTitleState) {
        when (state) {
            is TimetableTitleState.Single -> mainPageController?.setSingleTitle(state.title)
            is TimetableTitleState.Week -> {
                mainPageController?.setTitleText(state.title)
                mainPageController?.setTimetableName(state.name)
            }
        }
    }

    private fun getCurrentTimetableAndWeek(): Pair<Timetable?, Int?> {
        viewModel.timetableLiveData.value?.let { tts ->
            viewModel.currentPageStartDate.value?.let {
                val cdCalendar = Calendar.getInstance()
                cdCalendar.timeInMillis = it
                var minTT: Timetable? = null
                var minWk = Int.MAX_VALUE
                for (tt in tts) {
                    val isEASTimetable = !tt.code.isNullOrEmpty()
                    if (!isEASTimetable) continue

                    val wk = tt.getWeekNumber(cdCalendar.timeInMillis)
                    if (wk in 1 until minWk) {
                        minWk = wk
                        minTT = tt
                    }
                }
                return Pair(minTT, minWk)
            }
        }
        return Pair(null, null)
    }

    private fun showAddEvent(dow: Int, period: TimePeriodInDay) {
        viewModel.timetableLiveData.value?.let {
            val cp = getCurrentTimetableAndWeek()
            cp.first?.let { timetable ->
                PopupAddEvent().setInitTimetable(timetable).setInitTime(
                    dow,
                    week = if ((cp.second ?: 1) <= 0) 1 else cp.second!!,
                    period
                ).show(childFragmentManager, "add")
            } ?: run {
                Toast.makeText(context, getString(R.string.add_timetable_first), Toast.LENGTH_SHORT).show()
                activity?.let(ActivityUtils::startTimetableManager)
            }
        }
    }

    private fun showEditEventDialog(eventItem: EventItem) {
        val timetable = viewModel.timetableLiveData.value
            ?.firstOrNull { it.id == eventItem.timetableId }
        if (timetable == null) {
            Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show()
            return
        }
        PopupAddEvent()
            .setInitTimetable(timetable)
            .setEditEvent(eventItem)
            .show(childFragmentManager, "edit_event")
    }

    private fun showEventMenu(eventItem: EventItem) {
        val anchor = view ?: return
        val pm = PopupMenu(requireContext(), anchor, Gravity.NO_GRAVITY)
        pm.menu.add(0, R.id.menu_edit_event, 0, R.string.menu_edit)
        pm.menu.add(0, R.id.menu_delete_event, 1, R.string.menu_delete)
            .setIcon(R.drawable.ic_baseline_delete_24)
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit_event -> showEditEventDialog(eventItem)
                R.id.menu_delete_event -> confirmDeleteEvents(listOf(eventItem), anchor)
            }
            true
        }
        pm.show()
    }

    private fun confirmDeleteEvents(eventItems: List<EventItem>, anchor: View) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_title_sure_delete)
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(R.string.button_confirm) { _, _ ->
                val ef = ExplosionField.attach2Window(requireActivity())
                ef.explode(anchor)
                anchor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                Thread {
                    activity?.application?.let {
                        TimetableRepository(it).actionDeleteEvents(eventItems)
                        activity?.let { act -> WidgetUtils.sendRefreshToAll(act) }
                    }
                }.start()
            }
            .show()
    }

    interface MainPageController {
        fun setTitleText(string: String)
        fun setTimetableName(String: String)
        fun setSingleTitle(string: String)
    }
}

private sealed interface TimetableTitleState {
    data class Single(val title: String) : TimetableTitleState
    data class Week(val title: String, val name: String) : TimetableTitleState
}

@Composable
private fun TimetableScreen(
    viewModel: TimetableViewModel,
    onTitleState: (TimetableTitleState) -> Unit,
    onEventClick: (EventItem) -> Unit,
    onEventLongClick: (EventItem) -> Unit,
    onAddClick: (Int, TimePeriodInDay) -> Unit,
) {
    val context = LocalContext.current
    val currentPageStart by viewModel.currentPageStartDate.observeAsState(
        initial = viewModel.currentPageStartDate.value ?: mondayOf(System.currentTimeMillis())
    )
    val timetables by viewModel.timetableLiveData.observeAsState(emptyList())
    val startTime by viewModel.startTimeLiveData.observeAsState(800)
    val periodLabel by viewModel.periodLabelLiveData.observeAsState(false)
    val wallpaperPath by viewModel.wallpaperPathLiveData.observeAsState("")
    val dateColorInt by viewModel.wallpaperDateColorLiveData.observeAsState(AndroidColor.WHITE)
    val labelColorInt by viewModel.wallpaperLabelColorLiveData.observeAsState(AndroidColor.WHITE)
    val windowEvents by viewModel.windowEventsData[viewModel.startIndex].observeAsState()

    LaunchedEffect(currentPageStart) {
        viewModel.windowStartData[viewModel.startIndex].value = currentPageStart
    }
    LaunchedEffect(currentPageStart, timetables) {
        onTitleState(buildTitleState(context, currentPageStart, timetables))
    }

    val style = remember(windowEvents, startTime, periodLabel) {
        (windowEvents?.second ?: TimetableStyleSheet()).also {
            it.startTime = startTime
            it.usePeriodLabel = periodLabel
        }
    }
    val events = windowEvents?.first.orEmpty()
    val showTodayFab = currentPageStart > System.currentTimeMillis() ||
            System.currentTimeMillis() >= currentPageStart + TimetableFragment.WEEK_MILLS

    Box(modifier = Modifier.fillMaxSize()) {
        TimetableWeekContent(
            startDate = currentPageStart,
            events = events,
            style = style,
            dateColor = if (wallpaperPath.isBlank()) MaterialTheme.colorScheme.onSurface else Color(dateColorInt),
            labelColor = if (wallpaperPath.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color(labelColorInt),
            onPrevWeek = {
                viewModel.currentPageStartDate.value = currentPageStart - TimetableFragment.WEEK_MILLS
            },
            onNextWeek = {
                viewModel.currentPageStartDate.value = currentPageStart + TimetableFragment.WEEK_MILLS
            },
            onEventClick = onEventClick,
            onEventLongClick = onEventLongClick,
            onAddClick = onAddClick,
        )

        if (showTodayFab) {
            FloatingActionButton(
                onClick = { viewModel.currentPageStartDate.value = mondayOf(System.currentTimeMillis()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(HitaTheme.tokens.spacing.lg)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_settings_backup_restore_24),
                    contentDescription = null
                )
            }
        }
    }
}

private fun buildTitleState(
    context: Context,
    currentPageStart: Long,
    timetables: List<Timetable>,
): TimetableTitleState {
    if (timetables.isEmpty()) {
        return TimetableTitleState.Single(context.getString(R.string.no_timetable))
    }
    val cdCalendar = Calendar.getInstance().apply { timeInMillis = currentPageStart }
    var minTT: Timetable? = null
    var minWk = Int.MAX_VALUE
    for (tt in timetables) {
        val isEASTimetable = !tt.code.isNullOrEmpty()
        if (!isEASTimetable) continue

        val wk = tt.getWeekNumber(cdCalendar.timeInMillis)
        if (wk in 1 until minWk) {
            minWk = wk
            minTT = tt
        }
    }
    return minTT?.let {
        TimetableTitleState.Week(
            context.getString(R.string.week_title, minWk),
            it.name.orEmpty()
        )
    } ?: TimetableTitleState.Single(context.getString(R.string.holiday))
}

@Composable
private fun TimetableWeekContent(
    startDate: Long,
    events: List<EventItem>,
    style: TimetableStyleSheet,
    dateColor: Color,
    labelColor: Color,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onEventClick: (EventItem) -> Unit,
    onEventLongClick: (EventItem) -> Unit,
    onAddClick: (Int, TimePeriodInDay) -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val tableHeight = with(density) { timetableHeight(style).toDp() }
    var tableWidthPx by remember { mutableStateOf(0) }
    var dragAmount by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(startDate) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAmount > 120f -> onPrevWeek()
                            dragAmount < -120f -> onNextWeek()
                        }
                        dragAmount = 0f
                    },
                    onHorizontalDrag = { _, drag -> dragAmount += drag }
                )
            }
    ) {
        TimetableDowHeader(startDate = startDate, monthColor = dateColor)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            TimetableLeftLabels(
                style = style,
                color = labelColor,
                modifier = Modifier
                    .width(HitaTheme.tokens.componentSize.timetableLabelWidth)
                    .height(tableHeight)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(tableHeight)
                    .onSizeChanged { tableWidthPx = it.width }
                    .pointerInput(startDate, style, tableWidthPx) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = tableWidthPx.takeIf { it > 0 } ?: return@detectTapGestures
                                val dow = ((offset.x / (width / 7f)).toInt() + 1).coerceIn(1, 7)
                                val period = pickPeriodFromOffset(offset.y, style) ?: return@detectTapGestures
                                onAddClick(dow, period)
                            }
                        )
                    }
            ) {
                TimetableGrid(startDate = startDate, style = style)
                TimetableEventLayer(
                    events = events,
                    style = style,
                    onEventClick = onEventClick,
                    onEventLongClick = onEventLongClick,
                )
            }
        }
    }
}

@Composable
private fun TimetableDowHeader(startDate: Long, monthColor: Color) {
    val months = stringArrayResource(R.array.months)
    val dows = listOf(
        stringResource(R.string.tt_monday),
        stringResource(R.string.tt_tuesday),
        stringResource(R.string.tt_wednesday),
        stringResource(R.string.tt_thursday),
        stringResource(R.string.tt_friday),
        stringResource(R.string.tt_saturday),
        stringResource(R.string.tt_sunday),
    )
    val days = remember(startDate) {
        (0..6).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = startDate
                add(Calendar.DATE, offset)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HitaTheme.tokens.componentSize.timetableDateHeight)
            .padding(bottom = HitaTheme.tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = months[days.first()[Calendar.MONTH]],
            color = monthColor.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(HitaTheme.tokens.componentSize.timetableLabelWidth)
        )
        dows.forEach { dow ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = dow,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TimetableLeftLabels(
    style: TimetableStyleSheet,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val start = style.getStartTimeObject()
    val labelColor = color.copy(alpha = 0.91f)
    Column(modifier = modifier) {
        if (style.usePeriodLabel) {
            styleStructure().forEachIndexed { index, period ->
                val topMinutes = start.getDistanceInMinutes(period.from).coerceAtLeast(0)
                val top = (topMinutes / 60f * style.cardHeight).roundToInt()
                Text(
                    text = "第\n${index + 1}\n节",
                    color = labelColor,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, top) }
                )
            }
        } else {
            for (hour in start.hour..23) {
                Text(
                    text = TimeInDay(hour, 0).toString(),
                    color = labelColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { style.cardHeight.toDp() })
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(startDate: Long, style: TimetableStyleSheet) {
    val currentDow = TimeTools.currentDOW()
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val isCurrentWeek = remember(startDate) {
        val start = Calendar.getInstance().apply { timeInMillis = startDate }
        TimeTools.isSameWeekWithStartDate(start, System.currentTimeMillis())
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sectionWidth = size.width / 7f
        if (isCurrentWeek) {
            drawRect(
                color = Color(style.todayBGColor),
                topLeft = Offset(sectionWidth * (currentDow - 1), 0f),
                size = androidx.compose.ui.geometry.Size(sectionWidth, size.height)
            )
        }
        if (style.drawBGLine) {
            val start = style.getStartTimeObject()
            val effect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
            for (hour in start.hour..23) {
                val y = (hour - start.hour) * style.cardHeight.toFloat()
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y + 1f),
                    strokeWidth = 1f,
                    pathEffect = effect
                )
            }
        }
    }
}

@Composable
private fun TimetableEventLayer(
    events: List<EventItem>,
    style: TimetableStyleSheet,
    onEventClick: (EventItem) -> Unit,
    onEventLongClick: (EventItem) -> Unit,
) {
    val arranged = remember(events) { TimetableOverlapLayout.arrange(events) }
    BoxWithConstraintsCompat {
        val sectionWidth = maxWidth / 7f
        arranged.forEach { positioned ->
            val event = positioned.event
            val startMinutes = style.getStartTimeObject().getDistanceInMinutes(event.from.time)
            val duration = event.getDurationInMinutes().coerceAtLeast(15)
            val top = with(LocalDensity.current) {
                (startMinutes / 60f * style.cardHeight).toDp()
            }
            val height = with(LocalDensity.current) {
                (duration / 60f * style.cardHeight).toDp()
            }
            val dayLeft = sectionWidth * (event.getDow() - 1)
            val columnWidth = sectionWidth / positioned.columnCount
            TimetableEventCard(
                event = event,
                style = style,
                modifier = Modifier
                    .offset(x = dayLeft + columnWidth * positioned.columnIndex, y = top)
                    .width(columnWidth)
                    .height(height),
                columnCount = positioned.columnCount,
                onClick = { onEventClick(event) },
                onLongClick = { onEventLongClick(event) }
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsCompat(content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = content)
}

@Composable
private fun TimetableEventCard(
    event: EventItem,
    style: TimetableStyleSheet,
    modifier: Modifier = Modifier,
    columnCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val view = LocalView.current
    val background = if (style.isColorEnabled) {
        Color(event.color)
    } else {
        MaterialTheme.colorScheme.primary
    }.copy(alpha = style.cardOpacity.coerceIn(20, 100) / 100f)
    val effectiveBg = background.toArgb()
    val titleColor = resolveCardTextColor(style.cardTitleColor, style.isColorEnabled, event.color, effectiveBg)
    val subtitleColor = resolveCardTextColor(style.subTitleColor, style.isColorEnabled, event.color, effectiveBg)
    val textScale = when {
        columnCount >= 3 -> 0.78f
        columnCount == 2 -> 0.88f
        else -> 1f
    }
    Card(
        modifier = modifier
            .padding(start = 1.dp, top = 1.dp, end = 1.dp, bottom = 2.dp)
            .pointerInput(event.id) {
                detectTapGestures(
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onLongClick()
                    },
                    onTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (4 * textScale).dp, vertical = (3 * textScale).dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (style.cardIconEnabled) {
                Box(
                    modifier = Modifier
                        .size((8 * textScale).dp)
                        .clip(CircleShape)
                        .background(titleColor)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = event.name,
                color = titleColor,
                fontSize = (12 * textScale).sp,
                fontWeight = if (style.isBoldText) FontWeight.Bold else FontWeight.Normal,
                textAlign = textAlignFromGravity(style.titleGravity),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(style.titleAlpha / 100f)
            )
            if (!event.place.isNullOrBlank()) {
                Text(
                    text = event.place.orEmpty(),
                    color = subtitleColor,
                    fontSize = (11 * textScale).sp,
                    fontWeight = if (style.isBoldText) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(style.subtitleAlpha / 100f)
                )
            }
        }
    }
}

@Composable
private fun resolveCardTextColor(
    mode: String,
    colorEnabled: Boolean,
    eventColor: Int,
    effectiveBg: Int,
): Color {
    return when (mode) {
        "auto" -> Color(ColorContrast.contrastText(effectiveBg))
        "subject" -> if (colorEnabled) Color(eventColor) else MaterialTheme.colorScheme.primary
        "white" -> Color.White
        "black" -> Color.Black
        "primary" -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
}

private fun textAlignFromGravity(gravity: Int): TextAlign {
    return when (gravity) {
        Gravity.START, Gravity.LEFT -> TextAlign.Start
        Gravity.END, Gravity.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
}

private fun timetableHeight(style: TimetableStyleSheet): Int {
    val start = style.getStartTimeObject()
    val totalMinutes = (24 - start.hour) * 60 - start.minute
    return (totalMinutes / 60f * style.cardHeight).roundToInt()
}

private fun pickPeriodFromOffset(y: Float, style: TimetableStyleSheet): TimePeriodInDay? {
    val start = style.getStartTimeObject()
    val time = start.getAdded((y / style.cardHeight * 60f).toInt())
    val structure = styleStructure()
    for (i in structure.indices) {
        val period = structure[i]
        if (i == 0 && period.after(time)) {
            return TimePeriodInDay(start, period.from)
        }
        if (period.contains(time)) {
            return period.clone()
        }
        if (i + 1 < structure.size && structure[i + 1].after(time)) {
            return TimePeriodInDay(period.to, structure[i + 1].from)
        }
        if (i == structure.size - 1 && period.before(time)) {
            return TimePeriodInDay(period.to, TimeInDay(23, 59))
        }
    }
    return null
}

private fun styleStructure(): List<TimePeriodInDay> {
    return Timetable().scheduleStructure
}

private fun mondayOf(ts: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = ts
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis
}
