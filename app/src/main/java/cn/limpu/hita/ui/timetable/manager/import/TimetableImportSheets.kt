package cn.limpu.hita.ui.timetable.manager.import

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.limpu.hita.R
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.data.model.timetable.TimetableGroupEntity
import cn.limpu.hita.feature.timetableshare.protocol.FriendImportPreview
import cn.limpu.hita.feature.timetableshare.protocol.ImportKind
import cn.limpu.hita.feature.timetableshare.protocol.ShareMode
import cn.limpu.hita.feature.timetableshare.protocol.ShareError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val tokenDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(ZoneId.of("Asia/Shanghai"))

/** 口令解析的可展示错误。 */
internal fun shareErrorText(error: ShareError): Int = when (error) {
    ShareError.NO_TOKEN -> R.string.timetable_share_error_no_token
    ShareError.MULTIPLE_TOKENS -> R.string.timetable_share_error_multiple
    ShareError.UNSUPPORTED_VERSION -> R.string.timetable_share_error_version
    ShareError.TOO_LONG -> R.string.timetable_share_error_too_long
    ShareError.CORRUPT_DATA -> R.string.timetable_share_error_corrupt
    ShareError.DECOMPRESSED_TOO_LARGE -> R.string.timetable_share_error_decompressed
    ShareError.INVALID_FIELDS -> R.string.timetable_share_error_fields
    ShareError.EMPTY_SCHEDULE -> R.string.timetable_share_empty_schedule
}

/**
 * ICS 选完文件后的「导入为」选择：我的课表或好友课表。
 */
@Composable
fun ImportTargetDialog(
    sourceName: String,
    onDismiss: () -> Unit,
    onMine: () -> Unit,
    onFriend: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_target_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sourceName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.import_target_mine_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onMine)
                        .padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.import_target_friend_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onFriend)
                        .padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}

/**
 * 「同屏显示?」选择：把导入的课表与现有课表/组合同屏,或分开显示。
 * 用户要求不设默认选中,必须主动选择后确定才可点。
 */
sealed interface ImportScopeChoice {
    data object Separate : ImportScopeChoice
    data class IntoGroup(val groupId: String, val name: String) : ImportScopeChoice
    data class WithTimetable(val timetableId: String, val name: String) : ImportScopeChoice
}

@Composable
fun ImportScopeDialog(
    timetableName: String,
    groups: List<TimetableGroupEntity>,
    singles: List<Timetable>,
    onDismiss: () -> Unit,
    onConfirm: (ImportScopeChoice) -> Unit,
) {
    var choice by remember { mutableStateOf<ImportScopeChoice?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_scope_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.import_scope_hint, timetableName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = choice is ImportScopeChoice.Separate) {
                            choice = ImportScopeChoice.Separate
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = choice is ImportScopeChoice.Separate, onClick = null)
                    Text(stringResource(R.string.import_scope_separate))
                }
                if (groups.isNotEmpty() || singles.isNotEmpty()) {
                    Text(
                        stringResource(R.string.import_scope_join_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = (choice as? ImportScopeChoice.IntoGroup)?.groupId == group.id) {
                                choice = ImportScopeChoice.IntoGroup(group.id, group.name)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (choice as? ImportScopeChoice.IntoGroup)?.groupId == group.id,
                            onClick = null
                        )
                        Text(stringResource(R.string.import_scope_group_item, group.name))
                    }
                }
                singles.forEach { tt ->
                    val name = tt.name ?: stringResource(R.string.default_timetable_name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = (choice as? ImportScopeChoice.WithTimetable)?.timetableId == tt.id) {
                                choice = ImportScopeChoice.WithTimetable(tt.id, name)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (choice as? ImportScopeChoice.WithTimetable)?.timetableId == tt.id,
                            onClick = null
                        )
                        Text(stringResource(R.string.import_scope_with_item, name))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { choice?.let(onConfirm) }, enabled = choice != null) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}

/**
 * 课表口令导入弹层：粘贴 → 解析预览 → 导入为好友课表 / 我的课表(仅 FULL)。
 * 解析与保存经回调交给外部(Activity/ViewModel)执行,本组件只做交互与状态展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenImportSheet(
    input: String,
    loading: Boolean,
    errorResource: Int?,
    preview: FriendImportPreview?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onInputChange: (String) -> Unit,
    onParse: (String) -> Unit,
    onImportFriend: () -> Unit,
    onImportMine: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.timetable_add_import_token),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !loading && !saving,
                label = { Text(stringResource(R.string.friend_import_input)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp),
                minLines = 3,
                maxLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { onParse(input) },
                    enabled = input.isNotBlank() && !loading && !saving
                ) {
                    Text(stringResource(R.string.friend_import_parse))
                }
                if (loading || saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            errorResource?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
            preview?.let { importPreview ->
                val snapshot = importPreview.snapshot
                if (importPreview.kind == ImportKind.UPDATE) {
                    Text(stringResource(R.string.friend_import_update_notice),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    importPreview.existingRemark?.let { remark ->
                        Text(stringResource(R.string.friend_import_existing_remark, remark))
                    }
                }
                Text(
                    snapshot.term.timetableName.ifBlank { snapshot.nickname },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(
                        R.string.token_import_preview_meta,
                        snapshot.term.termName,
                        tokenDateFormat.format(Instant.ofEpochMilli(snapshot.term.startMillis)),
                        tokenDateFormat.format(Instant.ofEpochMilli(snapshot.term.endMillis)),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (snapshot.mode == ShareMode.FULL) {
                        stringResource(R.string.token_import_preview_full, snapshot.courses.size, snapshot.occurrences.size)
                    } else {
                        stringResource(R.string.token_import_preview_busy, snapshot.occurrences.size)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onImportFriend, enabled = !loading && !saving) {
                        Text(stringResource(if (importPreview.kind == ImportKind.UPDATE)
                            R.string.friend_import_update else R.string.import_target_friend))
                    }
                    TextButton(
                        onClick = onImportMine,
                        enabled = !loading && !saving && snapshot.mode == ShareMode.FULL
                    ) {
                        Text(stringResource(R.string.import_target_mine))
                    }
                }
                if (snapshot.mode == ShareMode.BUSY) {
                    Text(
                        stringResource(R.string.import_target_busy_block),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
