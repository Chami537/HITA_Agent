package com.limpu.hitax.ui.main.timeline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIME_TICK
import android.content.IntentFilter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import com.limpu.hitax.R
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.data.model.timetable.TimeInDay
import com.limpu.hitax.ui.base.ComposeViewBinding
import com.limpu.hitax.ui.base.HiltBaseFragmentWithReceiver
import com.limpu.hitax.ui.design.HitaComposeTheme
import com.limpu.hitax.ui.design.HitaTheme
import com.limpu.hitax.ui.widgets.WidgetUtils
import com.limpu.hitax.utils.EventsUtils
import com.limpu.hitax.utils.HintUtils
import com.limpu.hitax.utils.SpecialEventReminderUtils
import com.limpu.hitax.utils.TimeTools
import com.limpu.hitax.utils.TimeTools.TTY_REPLACE
import com.limpu.hitax.utils.TimeTools.TTY_WK_FOLLOWING
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Collections

@AndroidEntryPoint
class FragmentTimeLine : HiltBaseFragmentWithReceiver<ComposeViewBinding>() {

    protected val viewModel: FragmentTimelineViewModel by viewModels()
    private var mainPageController: MainPageController? = null

    override var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action == ACTION_TIME_TICK ||
                intent?.action == ACTION_DATE_CHANGED ||
                intent?.action == Intent.ACTION_TIME_CHANGED
            ) {
                viewModel.startRefresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        retainInstance = false
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
        mainPageController?.setTimelineTitleText(
            TimeTools.getDateString(
                requireContext(),
                Calendar.getInstance(),
                true,
                TTY_WK_FOLLOWING
            )
        )
    }

    override fun initViewBinding(): ComposeViewBinding {
        return ComposeViewBinding(ComposeView(requireContext()))
    }

    override fun initViews(view: View) {
        (binding?.root as? ComposeView)?.setContent {
            HitaComposeTheme() {
                TimelineScreen(
                    viewModel = viewModel,
                    onTitleChanged = { mainPageController?.setTimelineTitleText(it) },
                    onEventClick = { context?.let { ctx -> EventsUtils.showEventItem(ctx, it) } },
                    onHintConfirmed = { context?.let { ctx -> HintUtils.clickHint(ctx, it) }; viewModel.startRefresh() },
                    onRefreshWidget = { activity?.let(WidgetUtils::sendRefreshToAll) },
                )
            }
        }
    }

    interface MainPageController {
        fun setTimelineTitleText(string: String)
    }

    override fun getIntentFilter(): IntentFilter {
        val inf = IntentFilter()
        inf.addAction(ACTION_DATE_CHANGED)
        inf.addAction(ACTION_TIME_TICK)
        inf.addAction(Intent.ACTION_TIME_CHANGED)
        return inf
    }
}

@Composable
private fun TimelineScreen(
    viewModel: FragmentTimelineViewModel,
    onTitleChanged: (String) -> Unit,
    onEventClick: (EventItem) -> Unit,
    onHintConfirmed: (EventItem) -> Unit,
    onRefreshWidget: () -> Unit,
) {
    val context = LocalContext.current
    val todayEventsRaw by viewModel.todayEventsLiveData.observeAsState(emptyList())
    val weekEventsRaw by viewModel.weekEventsLiveData.observeAsState(emptyList())
    val upcomingExamsRaw by viewModel.upcomingExamLiveData.observeAsState(emptyList())
    var expanded by remember { mutableStateOf(false) }

    val todayEvents = remember(todayEventsRaw) {
        todayEventsRaw.sortedBy { it.from.time }
    }
    val weekEvents = remember(weekEventsRaw) {
        weekEventsRaw.sortedBy { it.from.time }
    }
    val upcomingExams = remember(upcomingExamsRaw) {
        upcomingExamsRaw.sortedBy { it.from.time }
    }
    val hintEvents = remember(todayEventsRaw) { HintUtils.getHints(context) }
    val displayEvents = remember(hintEvents, todayEvents) {
        hintEvents + todayEvents
    }
    val headerState = remember(todayEvents, upcomingExams) {
        buildHeaderState(context, todayEvents, upcomingExams)
    }

    LaunchedEffect(todayEventsRaw) {
        onRefreshWidget()
    }
    LaunchedEffect(expanded) {
        onTitleChanged(
            if (expanded) {
                context.getString(R.string.events_incoming)
            } else {
                TimeTools.getDateString(context, Calendar.getInstance(), true, TTY_WK_FOLLOWING)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = HitaTheme.tokens.spacing.xxl)
    ) {
        item(key = "timeline_header") {
            TimelineHeaderCard(
                state = headerState,
                expanded = expanded,
                weekEvents = weekEvents,
                onToggleExpanded = { expanded = !expanded },
                onEventClick = onEventClick,
            )
        }

        items(displayEvents, key = { "timeline_${it.id}_${it.from.time}" }) { event ->
            if (event.type == EventItem.TYPE.TAG) {
                TimelineHintCard(event = event, onConfirmed = onHintConfirmed)
            } else {
                TimelineEventRow(
                    event = event,
                    isPassed = TimeTools.passed(event.to),
                    isUpcomingExam = SpecialEventReminderUtils.isExamEvent(event) &&
                            event.from.time > System.currentTimeMillis(),
                    isNow = event.containsTimeStamp(System.currentTimeMillis()),
                    onClick = { onEventClick(event) },
                )
            }
        }

        if (todayEvents.isEmpty()) {
            item(key = "timeline_empty") {
                TimelineEmpty()
            }
        }

        item(key = "timeline_footer") {
            Spacer(modifier = Modifier.height(HitaTheme.tokens.spacing.xxl))
        }
    }
}

private data class TimelineHeaderState(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val nowProgress: Float?,
    val goNowPlace: String?,
    val nextEventName: String,
    val nextEventTime: String,
    val nextIconRes: Int,
    val examCountdown: String?,
    val examName: String?,
)

private fun buildHeaderState(
    context: Context,
    todayEvents: List<EventItem>,
    upcomingExams: List<EventItem>,
): TimelineHeaderState {
    val now = System.currentTimeMillis()
    val currentTime = Calendar.getInstance()
    val courseNum = todayEvents.count { it.type == EventItem.TYPE.CLASS }
    val nowEvent = todayEvents
        .asReversed()
        .firstOrNull { !SpecialEventReminderUtils.isExamEvent(it) && it.containsTimeStamp(now) }
    val nextEvent = todayEvents
        .firstOrNull { !SpecialEventReminderUtils.isExamEvent(it) && it.happensAfterTimeStamp(now) }

    val title: String
    val subtitle: String
    val iconRes: Int
    val progress: Float?
    val goNowPlace: String?

    when {
        todayEvents.isEmpty() -> {
            title = context.getString(R.string.timeline_head_free_title)
            subtitle = context.getString(R.string.timeline_head_free_subtitle)
            iconRes = R.drawable.ic_timeline_head_free
            progress = null
            goNowPlace = null
        }
        nowEvent != null -> {
            title = nowEvent.name
            subtitle = context.getString(R.string.timeline_head_ongoing_subtitle)
            iconRes = R.drawable.ic_timelapse
            progress = nowEvent.getProgress(now)
            goNowPlace = null
        }
        isBetween(currentTime, TimeInDay(0, 0), TimeInDay(5, 0)) -> {
            title = context.getString(R.string.timeline_head_goodnight_title)
            subtitle = context.getString(R.string.timeline_head_goodnight_subtitle)
            iconRes = R.drawable.ic_moon
            progress = null
            goNowPlace = null
        }
        isBetween(currentTime, TimeInDay(5, 0), TimeInDay(8, 15)) -> {
            title = context.getString(R.string.timeline_head_goodmorning_title)
            subtitle = context.getString(R.string.timelinr_goodmorning_subtitle, courseNum)
            iconRes = R.drawable.ic_sunny
            progress = null
            goNowPlace = null
        }
        isBetween(currentTime, TimeInDay(12, 15), TimeInDay(13, 0)) -> {
            title = context.getString(R.string.timeline_head_lunch_title)
            subtitle = context.getString(R.string.timeline_head_lunch_subtitle)
            iconRes = R.drawable.ic_lunch
            progress = null
            goNowPlace = null
        }
        isBetween(currentTime, TimeInDay(17, 10), TimeInDay(18, 10)) -> {
            title = context.getString(R.string.timeline_head_dinner_title)
            subtitle = context.getString(R.string.timeline_head_dinner_subtitle)
            iconRes = R.drawable.ic_lunch
            progress = null
            goNowPlace = null
        }
        nextEvent != null && nextEvent.getFromTimeDistance() <= 15 &&
                (nextEvent.type == EventItem.TYPE.CLASS || nextEvent.type == EventItem.TYPE.OTHER) -> {
            title = nextEvent.name
            subtitle = context.getString(R.string.timeline_gonow_subtitle, nextEvent.getFromTimeDistance())
            iconRes = R.drawable.ic_run
            progress = null
            goNowPlace = nextEvent.place?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.unknown_location)
        }
        nextEvent != null -> {
            title = context.getString(R.string.timeline_head_normal_title)
            subtitle = context.getString(R.string.timeline_head_normal_subtitle)
            iconRes = R.drawable.ic_sunglasses
            progress = null
            goNowPlace = null
        }
        isBetween(currentTime, TimeInDay(23, 0), TimeInDay(23, 59)) ||
                isBetween(currentTime, TimeInDay(0, 0), TimeInDay(5, 0)) -> {
            title = context.getString(R.string.timeline_head_goodnight_title)
            subtitle = context.getString(R.string.timeline_head_goodnight_subtitle)
            iconRes = R.drawable.ic_moon
            progress = null
            goNowPlace = null
        }
        else -> {
            title = context.getString(R.string.timeline_head_finish_title)
            subtitle = context.getString(R.string.timeline_head_finish_subtitle)
            iconRes = R.drawable.ic_finish
            progress = null
            goNowPlace = null
        }
    }

    val nextEventTime = nextEvent?.let {
        val distance = it.getFromTimeDistance()
        val distanceText = if (distance >= 60) {
            context.getString(R.string.time_format_1, distance / 60, distance % 60)
        } else {
            context.getString(R.string.time_format_2, distance)
        }
        distanceText + context.getString(R.string.timeline_counting_middle)
    } ?: context.getString(R.string.timeline_counting_free)

    val exam = upcomingExams.firstOrNull()
    return TimelineHeaderState(
        title = title,
        subtitle = subtitle,
        iconRes = iconRes,
        nowProgress = progress,
        goNowPlace = goNowPlace,
        nextEventName = nextEvent?.name ?: "see you",
        nextEventTime = nextEventTime,
        nextIconRes = if (nextEvent != null) R.drawable.ic_baseline_access_alarm_24 else R.drawable.ic_empty,
        examCountdown = exam?.let(SpecialEventReminderUtils::formatExamCountdown),
        examName = if (exam != null) formatExamReminderName(upcomingExams) else null,
    )
}

private fun isBetween(calendar: Calendar, from: TimeInDay, to: TimeInDay): Boolean {
    val now = TimeInDay(calendar)
    return now >= from && now < to
}

private fun formatExamReminderName(exams: List<EventItem>): String {
    val exam = exams.firstOrNull() ?: return ""
    return buildString {
        append(SpecialEventReminderUtils.formatExamName(exam))
        if (exams.size > 1) {
            append(" 等 ")
            append(exams.size)
            append(" 场考试")
        }
    }
}

@Composable
private fun TimelineHeaderCard(
    state: TimelineHeaderState,
    expanded: Boolean,
    weekEvents: List<EventItem>,
    onToggleExpanded: () -> Unit,
    onEventClick: (EventItem) -> Unit,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HitaTheme.tokens.spacing.lg,
                top = HitaTheme.tokens.spacing.lg,
                end = HitaTheme.tokens.spacing.lg,
                bottom = HitaTheme.tokens.spacing.xl
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggleExpanded()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(HitaTheme.tokens.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = state.subtitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(0.8f)
                    )
                }

                HeaderVisual(state)

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_arrow_drop_down_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = HitaTheme.tokens.spacing.lg)
                        .size(44.dp)
                        .rotate(if (expanded) 180f else 0f)
                )

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = HitaTheme.tokens.spacing.lg,
                                end = HitaTheme.tokens.spacing.lg,
                                bottom = HitaTheme.tokens.spacing.lg
                            )
                    ) {
                        HeaderInfoPill(
                            title = state.nextEventTime,
                            subtitle = state.nextEventName,
                            icon = state.nextIconRes
                        )
                        state.examCountdown?.let { countdown ->
                            Spacer(modifier = Modifier.height(HitaTheme.tokens.spacing.sm))
                            HeaderInfoPill(
                                title = countdown,
                                subtitle = state.examName.orEmpty(),
                                icon = R.drawable.ic_baseline_error_24
                            )
                        }
                        Spacer(modifier = Modifier.height(HitaTheme.tokens.spacing.sm))
                        if (weekEvents.isEmpty()) {
                            HeaderWeekEmpty()
                        } else {
                            weekEvents.forEach { event ->
                                TimelineTopEventRow(event = event, onClick = { onEventClick(event) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderVisual(state: TimelineHeaderState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state.nowProgress != null) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(HitaTheme.tokens.spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(state.nowProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (state.goNowPlace == null) 110.dp else 80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(if (state.goNowPlace == null) HitaTheme.tokens.spacing.xl else HitaTheme.tokens.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(state.iconRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.goNowPlace?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = HitaTheme.tokens.spacing.xs)
                            .alpha(0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderInfoPill(
    title: String,
    subtitle: String,
    icon: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(HitaTheme.tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = HitaTheme.tokens.spacing.xs)
                    .alpha(0.8f)
            )
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun HeaderWeekEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(80.dp)
        )
    }
}

@Composable
private fun TimelineTopEventRow(event: EventItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val timeText = if (SpecialEventReminderUtils.isToday(event.from.time)) {
        val minutes = event.getFromTimeDistance()
        if (minutes > 60) {
            context.getString(R.string.timeline_countdown_template_hour, minutes.toFloat() / 60.0f)
        } else {
            context.getString(R.string.timeline_countdown_template_minute, minutes)
        }
    } else {
        TimeTools.getDateString(context, event.from.time, true, TTY_REPLACE)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = HitaTheme.tokens.spacing.sm)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(
                horizontal = HitaTheme.tokens.spacing.md,
                vertical = HitaTheme.tokens.spacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = event.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = HitaTheme.tokens.spacing.sm)
        )
        Icon(
            painter = painterResource(R.drawable.ic_baseline_access_time_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = timeText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = HitaTheme.tokens.spacing.xs)
        )
    }
}

@Composable
private fun TimelineHintCard(event: EventItem, onConfirmed: (EventItem) -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HitaTheme.tokens.spacing.lg),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HitaTheme.tokens.spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_hint),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = event.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = HitaTheme.tokens.spacing.sm)
                )
            }
            Text(
                text = stringResource(R.string.navi_hint_button),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onConfirmed(event)
                    }
                    .padding(HitaTheme.tokens.spacing.lg),
            )
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: EventItem,
    isPassed: Boolean,
    isUpcomingExam: Boolean,
    isNow: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val primary = if (isUpcomingExam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val secondary = if (isUpcomingExam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HitaTheme.tokens.spacing.xl),
    ) {
        TimelineRail(isPassed = isPassed)
        val cardShape = RoundedCornerShape(if (isPassed) 24.dp else 16.dp)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = HitaTheme.tokens.spacing.lg,
                    top = if (isPassed) HitaTheme.tokens.spacing.sm else HitaTheme.tokens.spacing.lg,
                    end = HitaTheme.tokens.spacing.xl,
                    bottom = if (isPassed) HitaTheme.tokens.spacing.sm else HitaTheme.tokens.spacing.lg
                )
                .clip(cardShape)
                .clickable(onClick = onClick),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isPassed) 0.dp else 8.dp)
        ) {
            if (isPassed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = HitaTheme.tokens.spacing.lg,
                            vertical = HitaTheme.tokens.spacing.sm
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(0.8f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(HitaTheme.tokens.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.name,
                            color = primary,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isUpcomingExam) {
                            Text(
                                text = stringResource(R.string.timeline_exam_reminder_tag),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .padding(top = HitaTheme.tokens.spacing.sm)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(
                                        horizontal = HitaTheme.tokens.spacing.sm,
                                        vertical = HitaTheme.tokens.spacing.xs
                                    )
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = HitaTheme.tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_access_time_24),
                                contentDescription = null,
                                tint = secondary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isUpcomingExam) {
                                    SpecialEventReminderUtils.formatExamDateTime(event)
                                } else {
                                    "${TimeTools.printTime(event.from.time)}-${TimeTools.printTime(event.to.time)}"
                                },
                                color = secondary.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = HitaTheme.tokens.spacing.sm)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(HitaTheme.tokens.spacing.md))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(HitaTheme.tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_location_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = event.place?.takeIf { it.isNotBlank() }
                                    ?: context.getString(R.string.unknown_location),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(56.dp)
                                    .padding(start = HitaTheme.tokens.spacing.xs)
                            )
                        }
                        if (isNow) {
                            LinearProgressIndicator(
                                progress = { event.getProgress(System.currentTimeMillis()) },
                                modifier = Modifier
                                    .padding(top = HitaTheme.tokens.spacing.sm)
                                    .width(72.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(isPassed: Boolean) {
    Column(
        modifier = Modifier
            .width(24.dp)
            .height(if (isPassed) 56.dp else 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(HitaTheme.tokens.componentSize.timelineWidth)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(
            modifier = Modifier
                .size(if (isPassed) 18.dp else 10.dp)
                .clip(CircleShape)
                .background(
                    if (isPassed) MaterialTheme.colorScheme.outlineVariant
                    else MaterialTheme.colorScheme.primary
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isPassed) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_check_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .width(HitaTheme.tokens.componentSize.timelineWidth)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun TimelineEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(HitaTheme.tokens.spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_timeline),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.size(160.dp)
        )
    }
}
