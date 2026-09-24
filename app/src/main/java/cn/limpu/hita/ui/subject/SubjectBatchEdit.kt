package cn.limpu.hita.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.limpu.hita.R
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.ui.design.HitaTheme
import java.sql.Timestamp

internal enum class SubjectBatchEditScope { SELECTED, ALL, THIS, SLOT }

internal enum class SubjectBatchDeleteScope { SELECTED, ALL, THIS, SLOT }

internal fun EventItem.applyPeriodTime(
    timetable: Timetable,
    dow: Int,
    startPeriod: Int,
    endPeriod: Int,
): Boolean {
    val week = timetable.getWeekNumber(from.time)
    if (week < 1) return false
    if (startPeriod < 1 || endPeriod < startPeriod) return false
    if (endPeriod > timetable.scheduleStructure.size) return false
    val times = timetable.getTimestamps(week, dow, startPeriod, endPeriod)
    if (times.size < 2 || times[1] <= times[0]) return false
    from = Timestamp(times[0])
    to = Timestamp(times[1])
    fromNumber = startPeriod
    lastNumber = endPeriod - startPeriod + 1
    return true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubjectBatchEditDialog(
    scope: SubjectBatchEditScope,
    events: List<EventItem>,
    timetable: Timetable?,
    onDismiss: () -> Unit,
    onApply: (place: String?, teacher: String?, time: Triple<Int, Int, Int>?) -> Unit,
) {
    val tokens = HitaTheme.tokens
    val sharedPlace = events.map { it.place.orEmpty() }.distinct().singleOrNull().orEmpty()
    val sharedTeacher = events.map { it.teacher.orEmpty() }.distinct().singleOrNull().orEmpty()
    var place by remember(scope, events.size) { mutableStateOf(sharedPlace) }
    var teacher by remember(scope, events.size) { mutableStateOf(sharedTeacher) }
    val allowTime = scope != SubjectBatchEditScope.ALL && timetable != null
    var changeTime by remember { mutableStateOf(false) }
    val sample = events.firstOrNull()
    val periodCount = timetable?.scheduleStructure?.size?.coerceAtLeast(1) ?: 1
    var dow by remember { mutableIntStateOf(sample?.getDow()?.coerceIn(1, 7) ?: 1) }
    var startPeriod by remember {
        mutableIntStateOf(sample?.fromNumber?.takeIf { it in 1..periodCount } ?: 1)
    }
    var endPeriod by remember {
        val inferred = sample?.let { it.fromNumber + it.lastNumber - 1 } ?: startPeriod
        mutableIntStateOf(inferred.coerceIn(1, periodCount))
    }
    val weekdays = stringArrayResource(R.array.dow2)
    val timeInvalid = changeTime && endPeriod < startPeriod

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    when (scope) {
                        SubjectBatchEditScope.ALL -> R.string.subject_batch_edit_title_all
                        SubjectBatchEditScope.THIS -> R.string.subject_batch_edit_title_this
                        SubjectBatchEditScope.SLOT -> R.string.subject_batch_edit_title_slot
                        SubjectBatchEditScope.SELECTED -> R.string.subject_batch_edit_title_selected
                    }
                ),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.subject_batch_keep_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_classroom)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_teacher)) },
                    singleLine = true,
                )
                if (allowTime) {
                    FilterChip(
                        selected = changeTime,
                        onClick = { changeTime = !changeTime },
                        label = { Text(stringResource(R.string.subject_batch_change_time)) },
                    )
                    if (changeTime) {
                        Text(
                            text = stringResource(R.string.subject_batch_weekday),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            weekdays.forEachIndexed { index, label ->
                                val value = index + 1
                                FilterChip(
                                    selected = dow == value,
                                    onClick = { dow = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.subject_batch_period_start),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = tokens.spacing.xs),
                        )
                        PeriodChipRow(
                            count = periodCount,
                            selected = startPeriod,
                            onSelect = { startPeriod = it },
                        )
                        Text(
                            text = stringResource(R.string.subject_batch_period_end),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        PeriodChipRow(
                            count = periodCount,
                            selected = endPeriod,
                            onSelect = { endPeriod = it },
                        )
                        if (timeInvalid) {
                            Text(
                                text = stringResource(R.string.subject_batch_time_invalid),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !timeInvalid,
                onClick = {
                    val nextPlace = place.trim().takeIf { it.isNotEmpty() }
                    val nextTeacher = teacher.trim().takeIf { it.isNotEmpty() }
                    val time = if (allowTime && changeTime) Triple(dow, startPeriod, endPeriod) else null
                    onApply(nextPlace, nextTeacher, time)
                },
            ) {
                Text(stringResource(R.string.subject_batch_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodChipRow(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (period in 1..count) {
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(stringResource(R.string.period, period)) },
            )
        }
    }
}

@Composable
internal fun SubjectBatchDeleteDialog(
    scope: SubjectBatchDeleteScope,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_sure_delete), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = stringResource(
                    when (scope) {
                        SubjectBatchDeleteScope.ALL -> R.string.subject_batch_delete_all_message
                        SubjectBatchDeleteScope.THIS -> R.string.subject_batch_delete_this_message
                        SubjectBatchDeleteScope.SLOT -> R.string.subject_batch_delete_slot_message
                        SubjectBatchDeleteScope.SELECTED -> R.string.subject_batch_delete_selected_message
                    },
                    count,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.button_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

internal fun applySubjectBatchEdit(
    events: List<EventItem>,
    timetable: Timetable?,
    place: String?,
    teacher: String?,
    time: Triple<Int, Int, Int>?,
): Pair<List<EventItem>, Boolean> {
    var skippedTime = false
    val edited = events.map { event ->
        event.apply {
            place?.let { this.place = it }
            teacher?.let { this.teacher = it }
            if (time != null && timetable != null) {
                val (dow, start, end) = time
                if (!applyPeriodTime(timetable, dow, start, end)) {
                    skippedTime = true
                }
            }
        }
    }
    return edited to skippedTime
}
