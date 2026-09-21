package cn.limpu.hita.ui.main.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import cn.limpu.hita.R
import cn.limpu.hita.data.repository.CourseChange
import cn.limpu.hita.data.repository.CourseVetoReason
import cn.limpu.hita.data.repository.TimetableChangeState
import cn.limpu.hita.data.repository.TimetableDecisionItem
import cn.limpu.hita.data.repository.TimetableHeldBatch
import cn.limpu.hita.ui.design.HitaTheme

/**
 * 课表变更详情弹窗。
 *
 * 分区展示一次刷新带来的全部变化，并承载需要用户决策的事项：
 * - 已更新：时间/地点/教师调整、课次增加的课；
 * - 新增：课表源里新出现的课；
 * - 已保留：源端未返回或课次减少、被本地缓存保住的课；
 * - 待确认：持续缺失（3 次且 48 小时）的课，逐门"采用课表源 / 继续保留"；
 * - 课表源数据异常：源端与本地匹配率过低时整批挂起，"采用课表源数据 / 保留当前课表"。
 *
 * 全部文本走 strings.xml，配色走 MaterialTheme.colorScheme，间距走 HitaTheme.tokens，
 * 天然适配七种界面风格与明暗主题。
 */
@Composable
internal fun TimetableChangeDialog(
    state: TimetableChangeState,
    onDismiss: () -> Unit,
    onAdoptCourse: (TimetableDecisionItem) -> Unit,
    onDismissCourse: (TimetableDecisionItem) -> Unit,
    onAdoptBatch: (TimetableHeldBatch) -> Unit,
    onDismissBatch: (TimetableHeldBatch) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.timetable_change_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HitaTheme.tokens.spacing.md)
            ) {
                val info = state.info
                if (info != null && info.updated.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.timetable_change_section_updated, info.updated.size))
                    info.updated.forEach { change -> UpdatedCourseRow(change) }
                }
                if (info != null && info.added.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.timetable_change_section_added, info.added.size))
                    info.added.forEach { name -> CourseNameText(name) }
                }
                if (info != null && info.kept.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.timetable_change_section_kept, info.kept.size))
                    info.kept.forEach { name -> CourseNameText(name) }
                }
                if (state.decisions.isNotEmpty()) {
                    SectionHeader(
                        stringResource(R.string.timetable_change_section_decisions, state.decisions.size)
                    )
                    state.decisions.forEach { item ->
                        DecisionCourseRow(
                            item = item,
                            onAdopt = { onAdoptCourse(item) },
                            onKeep = { onDismissCourse(item) },
                        )
                    }
                }
                val batch = state.heldBatch
                if (batch != null) {
                    HeldBatchRow(
                        batch = batch,
                        onAdopt = { onAdoptBatch(batch) },
                        onKeep = { onDismissBatch(batch) },
                    )
                }
                if (state.info == null && state.decisions.isEmpty() && state.heldBatch == null) {
                    Text(
                        text = stringResource(R.string.timetable_change_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.timetable_change_close))
            }
        }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun CourseNameText(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun UpdatedCourseRow(change: CourseChange) {
    val tags = listOfNotNull(
        if (change.timeAdjusted) stringResource(R.string.timetable_change_time_adjusted) else null,
        if (change.placeAdjusted) stringResource(R.string.timetable_change_place_adjusted) else null,
        if (change.teacherAdjusted) stringResource(R.string.timetable_change_teacher_adjusted) else null,
        if (change.lessonCountDelta > 0) {
            stringResource(R.string.timetable_change_lessons_added, change.lessonCountDelta)
        } else {
            null
        }
    )
    Column {
        CourseNameText(change.name)
        if (tags.isNotEmpty()) {
            Text(
                text = tags.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DecisionCourseRow(
    item: TimetableDecisionItem,
    onAdopt: () -> Unit,
    onKeep: () -> Unit,
) {
    val days = remember(item.firstSeenMillis) {
        ((System.currentTimeMillis() - item.firstSeenMillis) / (24L * 60 * 60 * 1000)).toInt()
    }
    val detail = when (item.reason) {
        CourseVetoReason.MISSING_FROM_SOURCE ->
            stringResource(R.string.timetable_change_decision_missing, days)
        CourseVetoReason.LESSON_COUNT_REDUCED ->
            stringResource(
                R.string.timetable_change_decision_reduced,
                item.localLessonCount,
                item.incoming?.lessonCount ?: 0
            )
    }
    Column {
        CourseNameText(item.name)
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DecisionButtons(onAdopt = onAdopt, onKeep = onKeep)
    }
}

@Composable
private fun HeldBatchRow(
    batch: TimetableHeldBatch,
    onAdopt: () -> Unit,
    onKeep: () -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.timetable_change_batch_title))
        Text(
            text = stringResource(
                R.string.timetable_change_batch_message,
                batch.localCount,
                batch.incomingCount,
                batch.matchedCount
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DecisionButtons(
            onAdopt = onAdopt,
            onKeep = onKeep,
            adoptLabel = stringResource(R.string.timetable_change_batch_adopt),
            keepLabel = stringResource(R.string.timetable_change_batch_keep)
        )
    }
}

@Composable
private fun DecisionButtons(
    onAdopt: () -> Unit,
    onKeep: () -> Unit,
    adoptLabel: String = stringResource(R.string.timetable_change_action_adopt),
    keepLabel: String = stringResource(R.string.timetable_change_action_keep),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HitaTheme.tokens.spacing.sm)
    ) {
        TextButton(onClick = onAdopt) { Text(adoptLabel) }
        TextButton(onClick = onKeep) { Text(keepLabel) }
    }
}
