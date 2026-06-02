package com.limpu.hitax.ui.main.timetable

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
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
import com.limpu.hitax.ui.main.timetable.views.TimetableCardTextScale
import com.limpu.hitax.ui.main.timetable.views.TimetableOverlapLayout
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.hitax.utils.ActivityUtils
import com.limpu.hitax.utils.EventsUtils
import com.limpu.hitax.utils.TimeTools
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import kotlinx.coroutines.launch
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

    fun navigateToWeek(mondayMillis: Long) {
        viewModel.currentPageStartDate.value = mondayMillis
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
                    onEventLongClick = { event, position -> showEventMenu(event, position) },
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
                mainPageController?.setTimetableOptions(state.timetableOptions)
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

    private fun showEventMenu(eventItem: EventItem, positionInWindow: IntOffset) {
        val root = requireActivity().findViewById<FrameLayout>(android.R.id.content) ?: return
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        val anchorX = (positionInWindow.x - rootLocation[0]).coerceIn(0, (root.width - 1).coerceAtLeast(0))
        val anchorY = (positionInWindow.y - rootLocation[1]).coerceIn(0, (root.height - 1).coerceAtLeast(0))
        val anchor = View(requireContext()).apply {
            alpha = 0f
            isHapticFeedbackEnabled = true
        }
        root.addView(
            anchor,
            FrameLayout.LayoutParams(1, 1).apply {
                leftMargin = anchorX
                topMargin = anchorY
            }
        )
        anchor.post {
            val pm = PopupMenu(requireContext(), anchor, Gravity.NO_GRAVITY)
            pm.menu.add(0, R.id.menu_edit_event, 0, R.string.menu_edit)
            pm.menu.add(0, R.id.menu_delete_event, 1, R.string.menu_delete)
                .setIcon(R.drawable.ic_baseline_delete_24)
            pm.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_edit_event -> showEditEventDialog(eventItem)
                    R.id.menu_delete_event -> confirmDeleteEvents(listOf(eventItem))
                }
                true
            }
            pm.setOnDismissListener {
                (anchor.parent as? ViewGroup)?.removeView(anchor)
            }
            pm.show()
        }
    }

    private fun confirmDeleteEvents(eventItems: List<EventItem>) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_title_sure_delete)
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(R.string.button_confirm) { _, _ ->
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        fun setTimetableOptions(options: List<Timetable>)
    }
}

private sealed interface TimetableTitleState {
    data class Single(val title: String) : TimetableTitleState
    data class Week(val title: String, val name: String, val timetableOptions: List<Timetable> = emptyList()) : TimetableTitleState
}

@Composable
private fun TimetableScreen(
    viewModel: TimetableViewModel,
    onTitleState: (TimetableTitleState) -> Unit,
    onEventClick: (EventItem) -> Unit,
    onEventLongClick: (EventItem, IntOffset) -> Unit,
    onAddClick: (Int, TimePeriodInDay) -> Unit,
) {
    val context = LocalContext.current
    val currentPageStart by viewModel.currentPageStartDate.observeAsState(
        initial = viewModel.currentPageStartDate.value ?: mondayOf(System.currentTimeMillis())
    )
    val timetables by viewModel.timetableLiveData.observeAsState(emptyList())
    val startTime by viewModel.startTimeLiveData.observeAsState(830)
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
        (windowEvents?.style ?: TimetableStyleSheet()).also {
            it.startTime = startTime
            it.usePeriodLabel = periodLabel
        }
    }
    val events = windowEvents?.events.orEmpty()
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
                    .padding(
                        bottom = dimensionResource(R.dimen.bottom_navigation_height) + HitaTheme.tokens.spacing.lg,
                        start = HitaTheme.tokens.spacing.lg,
                        top = HitaTheme.tokens.spacing.lg,
                        end = HitaTheme.tokens.spacing.lg,
                    )
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
            title = context.getString(R.string.week_title, minWk),
            name = it.name.orEmpty(),
            timetableOptions = timetables.filter { tt -> !tt.code.isNullOrEmpty() }
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
    onEventLongClick: (EventItem, IntOffset) -> Unit,
    onAddClick: (Int, TimePeriodInDay) -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val effectiveStart = style.getStartTimeObject()
    val tableHeight = with(density) { timetableHeight(effectiveStart, style.cardHeight).toDp() }
    var tableWidthPx by remember { mutableStateOf(0) }
    var contentWidthPx by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    var dragAccum by remember { mutableStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    val displayOffset = if (isAnimating) animOffset.value else dragAccum
    val displayAlpha = if (contentWidthPx > 0f) {
        (1f - (abs(displayOffset) / contentWidthPx).coerceIn(0f, 1f))
    } else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = displayOffset
                alpha = displayAlpha
            }
            .onSizeChanged { contentWidthPx = it.width.toFloat() }
            .pointerInput(startDate) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (isAnimating) return@detectHorizontalDragGestures
                        coroutineScope.launch {
                            val threshold = contentWidthPx * 0.15f
                            val cur = dragAccum
                            dragAccum = 0f
                            when {
                                cur > threshold -> {
                                    isAnimating = true
                                    animOffset.snapTo(cur)
                                    animOffset.animateTo(contentWidthPx, tween(150))
                                    onPrevWeek()
                                    animOffset.snapTo(-contentWidthPx)
                                    animOffset.animateTo(0f, tween(150))
                                    isAnimating = false
                                }
                                cur < -threshold -> {
                                    isAnimating = true
                                    animOffset.snapTo(cur)
                                    animOffset.animateTo(-contentWidthPx, tween(150))
                                    onNextWeek()
                                    animOffset.snapTo(contentWidthPx)
                                    animOffset.animateTo(0f, tween(150))
                                    isAnimating = false
                                }
                                cur != 0f -> {
                                    isAnimating = true
                                    animOffset.snapTo(cur)
                                    animOffset.animateTo(0f, spring())
                                    isAnimating = false
                                }
                            }
                        }
                    },
                    onHorizontalDrag = { _, drag ->
                        dragAccum = (dragAccum + drag).coerceIn(-contentWidthPx, contentWidthPx)
                    }
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
                effectiveStart = effectiveStart,
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
                                val period = pickPeriodFromOffset(offset.y, effectiveStart, style) ?: return@detectTapGestures
                                onAddClick(dow, period)
                            }
                        )
                    }
            ) {
                TimetableGrid(startDate = startDate, effectiveStart = effectiveStart, style = style)
                TimetableEventLayer(
                    events = events,
                    effectiveStart = effectiveStart,
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
            .padding(bottom = HitaTheme.tokens.spacing.xs),
        verticalAlignment = Alignment.Bottom
    ) {
        // Month label
        Column(
            modifier = Modifier.width(HitaTheme.tokens.componentSize.timetableLabelWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = months[days.first()[Calendar.MONTH]],
                color = monthColor.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        // Day columns with dow circle + date
        days.forEachIndexed { index, day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dows[index],
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = "${day[Calendar.DATE]}",
                    color = monthColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TimetableLeftLabels(
    effectiveStart: TimeInDay,
    style: TimetableStyleSheet,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val labelColor = color.copy(alpha = 0.91f)
    Column(modifier = modifier) {
        if (style.usePeriodLabel) {
            val density = LocalDensity.current
            val periods = styleStructure()
            if (periods.isEmpty()) return@Column
            // Spacer if grid starts before first period
            val firstGap = effectiveStart.getDistanceInMinutes(periods.first().from).coerceAtLeast(0)
            if (firstGap > 0) {
                Spacer(Modifier.height(with(density) { (firstGap / 60f * style.cardHeight).toDp() }))
            }
            for (i in periods.indices) {
                val period = periods[i]
                val nextStart = periods.getOrElse(i + 1) { TimePeriodInDay(TimeInDay(24, 0), TimeInDay(24, 0)) }.from
                val segmentMinutes = period.from.getDistanceInMinutes(nextStart)
                Text(
                    text = "第\n${i + 1}\n节",
                    color = labelColor,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (segmentMinutes / 60f * style.cardHeight).toDp() })
                )
            }
        } else {
            val density = LocalDensity.current
            val labelTimes = hourLabelTimes(effectiveStart)
            // Spacer if grid starts before first label
            val firstGap = effectiveStart.getDistanceInMinutes(labelTimes.first()).coerceAtLeast(0)
            if (firstGap > 0) {
                Spacer(Modifier.height(with(density) { (firstGap / 60f * style.cardHeight).toDp() }))
            }
            for (i in labelTimes.indices) {
                val labelTime = labelTimes[i]
                val nextTime = labelTimes.getOrElse(i + 1) { TimeInDay(24, 0) }
                val segmentMinutes = labelTime.getDistanceInMinutes(nextTime)
                Text(
                    text = labelTime.toString(),
                    color = labelColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (segmentMinutes / 60f * style.cardHeight).toDp() })
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(startDate: Long, effectiveStart: TimeInDay, style: TimetableStyleSheet) {
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
            val effect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
            val labelTimes = hourLabelTimes(effectiveStart)
            for (labelTime in labelTimes) {
                val offsetMinutes = effectiveStart.getDistanceInMinutes(labelTime).coerceAtLeast(0)
                val y = (offsetMinutes / 60f * style.cardHeight)
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
    effectiveStart: TimeInDay,
    style: TimetableStyleSheet,
    onEventClick: (EventItem) -> Unit,
    onEventLongClick: (EventItem, IntOffset) -> Unit,
) {
    val arranged = remember(events) { TimetableOverlapLayout.arrange(events) }
    BoxWithConstraintsCompat {
        val sectionWidth = maxWidth / 7f
        arranged.forEach { positioned ->
            val event = positioned.event
            val startMinutes = effectiveStart.getDistanceInMinutes(event.from.time)
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
                    .offset(x = dayLeft + columnWidth * positioned.columnIndex + 2.dp, y = top)
                    .width((columnWidth - 4.dp).coerceAtLeast(0.dp))
                    .height(height),
                cardHeight = height,
                columnCount = positioned.columnCount,
                onClick = { onEventClick(event) },
                onLongClick = { position -> onEventLongClick(event, position) }
            )
        }
    }
}


@Composable
private fun BoxWithConstraintsCompat(content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize(), content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableEventCard(
    event: EventItem,
    style: TimetableStyleSheet,
    modifier: Modifier = Modifier,
    cardHeight: Dp,
    columnCount: Int,
    onClick: () -> Unit,
    onLongClick: (IntOffset) -> Unit,
) {
    val view = LocalView.current
    var cardPositionInWindow by remember { mutableStateOf(IntOffset.Zero) }
    val background = if (style.isColorEnabled) {
        Color(event.color)
    } else {
        MaterialTheme.colorScheme.primary
    }.copy(alpha = (120 - style.cardOpacity.coerceIn(20, 100)) / 100f)
    val borderColor = background.copy(alpha = 0.3f)
    val cardShape = RoundedCornerShape(HitaTheme.tokens.radius.md)
    val effectiveBg = background.toArgb()
    val titleColor = resolveCardTextColor(style.cardTitleColor, style.isColorEnabled, event.color, effectiveBg)
    val subtitleColor = resolveCardTextColor(style.subTitleColor, style.isColorEnabled, event.color, effectiveBg)
    val textScale = TimetableCardTextScale.forColumnCount(columnCount)
    val marginScale = TimetableCardTextScale.marginScaleForColumnCount(columnCount)
    val horizontalPadding = (5 * marginScale).dp
    val verticalPadding = (3 * marginScale).dp
    val minTitleSize = if (columnCount > 1) 5f else 6f
    val hasPlace = !event.place.isNullOrBlank()
    val nameLength = event.name.length
    val lengthScale = when {
        nameLength <= 4 -> 1.15f
        nameLength <= 6 -> 0.92f
        nameLength <= 8 -> 0.75f
        else -> 0.62f
    }
    val titleAvailableDp = cardHeight.value
        .minus(verticalPadding.value * 2f)
        .minus(if (hasPlace) 14f * textScale else 0f)
        .minus(if (style.cardIconEnabled) 10f * textScale else 0f)
    val maxTitleFromSpace = (titleAvailableDp / 1.2f).coerceAtMost(16f)
    val titleFontSize = (13f * textScale * lengthScale)
        .coerceIn(minTitleSize, maxTitleFromSpace)
        .sp
    val subtitleFontSize = (10f * textScale).sp
    val maxTitleLines = when {
        cardHeight < 40.dp -> 1
        cardHeight < 60.dp -> 2
        else -> 3
    }
    Card(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val windowPosition = coordinates.localToWindow(Offset.Zero)
                cardPositionInWindow = IntOffset(
                    windowPosition.x.roundToInt(),
                    windowPosition.y.roundToInt()
                )
            }
            .border(0.5.dp, borderColor, cardShape)
            .pointerInput(event.id) {
                detectTapGestures(
                    onLongPress = { localOffset ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onLongClick(
                            IntOffset(
                                cardPositionInWindow.x + localOffset.x.roundToInt(),
                                cardPositionInWindow.y + localOffset.y.roundToInt()
                            )
                        )
                    },
                    onTap = { onClick() }
                )
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            if (hasPlace) {
                Text(
                    text = event.place ?: "",
                    color = subtitleColor,
                    fontSize = subtitleFontSize,
                    lineHeight = (subtitleFontSize.value * 1.2f).sp,
                    fontWeight = if (style.isBoldText) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                        .alpha(style.subtitleAlpha / 100f)
                )
            }
            if (style.cardIconEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .size((8 * textScale).dp)
                        .clip(CircleShape)
                        .background(titleColor)
                )
            }
            val titleBottomPad = if (hasPlace) (14f * textScale).dp else 0.dp
            val titleTopPad = if (style.cardIconEnabled) (10f * textScale).dp else 0.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = titleBottomPad, top = titleTopPad),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = event.name,
                    color = titleColor,
                    fontSize = titleFontSize,
                    lineHeight = (titleFontSize.value * 1.2f).sp,
                    fontWeight = if (style.isBoldText) FontWeight.Bold else FontWeight.Normal,
                    textAlign = textAlignFromGravity(style.titleGravity),
                    maxLines = maxTitleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(style.titleAlpha / 100f)
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

private fun timetableHeight(start: TimeInDay, cardHeight: Int): Int {
    val totalMinutes = (24 - start.hour) * 60 - start.minute
    return (totalMinutes / 60f * cardHeight).roundToInt()
}

private fun pickPeriodFromOffset(y: Float, effectiveStart: TimeInDay, style: TimetableStyleSheet): TimePeriodInDay? {
    val time = effectiveStart.getAdded((y / style.cardHeight * 60f).toInt())
    val structure = styleStructure()
    for (i in structure.indices) {
        val period = structure[i]
        if (i == 0 && period.after(time)) {
            return TimePeriodInDay(effectiveStart, period.from)
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

/** 上午半小时间隔(8:30-12:30) + 下午整点(14:00-23:00)，如有更早课程则从 effectiveStart 向上取整扩展 */
private fun hourLabelTimes(effectiveStart: TimeInDay? = null): List<TimeInDay> {
    val times = mutableListOf<TimeInDay>()
    for (h in 8..12) times.add(TimeInDay(h, 30))
    for (h in 14..23) times.add(TimeInDay(h, 0))

    if (effectiveStart != null) {
        // 向下取整到最近的半小时
        val roundMin = if (effectiveStart.minute <= 30) 0 else 30
        val early = mutableListOf<TimeInDay>()
        var h = effectiveStart.hour
        var m = roundMin
        while (h < 8 || (h == 8 && m < 30)) {
            val t = TimeInDay(h, m)
            if (t !in times) early.add(t)
            if (m == 0) m = 30
            else { m = 0; h++ }
        }
        times.addAll(0, early)
    }

    return times
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
