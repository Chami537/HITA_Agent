package cn.limpu.hita.ui.main.timetable.compare

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.limpu.hita.R
import cn.limpu.hita.feature.timetablecompare.*
import java.time.Instant
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val actualFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
internal fun range(span: TimeSpan, actual: Boolean = false): String {
    val format = if (actual) actualFormat else clockFormat
    return format.format(Instant.ofEpochMilli(span.startMillis).atZone(CAMPUS_ZONE)) + "–" +
        format.format(Instant.ofEpochMilli(span.endMillis).atZone(CAMPUS_ZONE))
}

/** 某段时间内双方的课程明细；弹层内容由调用方包裹。 */
@Composable
internal fun ComparisonSlotCourseDetails(ready: ComparisonUiState.Ready, slot: ComparedPeriod) {
    if (slot.status == PeriodStatus.UNKNOWN) {
        Text(stringResource(R.string.comparison_unknown))
        return
    }
    val ownDetails = ready.source.ownCourses.filter {
        intersectsSlot(slot.span, TimeSpan(it.from.time, it.to.time))
    }
    Text(
        stringResource(R.string.comparison_own_details),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (ownDetails.isEmpty()) {
        Text(stringResource(R.string.comparison_no_class_in_slot),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ownDetails.forEach {
        Text(stringResource(R.string.comparison_actual_course, it.name, range(TimeSpan(it.from.time, it.to.time), true), it.place.orEmpty()),
            style = MaterialTheme.typography.bodySmall)
    }
    ready.source.participatingFriends().forEach { side ->
        val name = side.row.remark?.takeIf { it.isNotBlank() } ?: side.row.nickname
        val courses = comparisonFriendSlotDetails(
            slot.span, side, side.excludedCourseIndexes,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.comparison_friend_named, name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface)
        if (courses.isEmpty()) {
            Text(stringResource(R.string.comparison_no_class_in_slot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        courses.forEach {
            if (it.name == null) {
                Text(stringResource(R.string.comparison_busy_label), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.comparison_actual_course, it.name, range(it.span, true), it.place.orEmpty()),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun intersectsSlot(a: TimeSpan, b: TimeSpan) =
    a.startMillis < b.endMillis && a.endMillis > b.startMillis

@Composable
internal fun comparisonTitle(state: ComparisonUiState.Ready): String {
    val own = state.source.participatingTimetables()
    val friends = state.source.participatingFriends()
    return if (own.size <= 1 && friends.size <= 1) {
        val friendName = friends.firstOrNull()?.let { it.row.remark?.takeIf { remark -> remark.isNotBlank() } ?: it.row.nickname }
            ?: state.source.friendRow.remark?.takeIf { it.isNotBlank() } ?: state.source.friendRow.nickname
        stringResource(R.string.comparison_active_title, own.firstOrNull()?.name.orEmpty(), friendName)
    } else {
        stringResource(R.string.comparison_active_many, own.size, friends.size)
    }
}
