package cn.limpu.hita.ui.main.timetable.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.limpu.hita.R
import cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity
import cn.limpu.hita.feature.timetablecompare.ComparisonCourseChoice
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

internal const val COMPARISON_SELECTION_SEPARATOR = "\u001f"

/**
 * 对比配置：我的课表（组合整体）与好友课表都可多选。
 * 课程筛选默认收起且全选，展开后可取消个别课程。节次结构不一致的不能加入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonConfigSheet(
    personals: List<ComparisonPersonalItem>,
    friends: List<FriendTimetableEntity>,
    initial: ComparisonConfig?,
    loadOwnCourses: (String) -> Flow<List<ComparisonCourseChoice>>,
    onDismiss: () -> Unit,
    onConfirm: (ComparisonConfig) -> Unit,
    onManage: () -> Unit,
    onImport: () -> Unit,
    onFriends: () -> Unit,
) {
    var personalRaw by rememberSaveable {
        mutableStateOf(initial?.selectedPersonalIds.orEmpty().joinToString(COMPARISON_SELECTION_SEPARATOR))
    }
    var friendRaw by rememberSaveable {
        mutableStateOf(initial?.selectedFriendIds.orEmpty().joinToString(COMPARISON_SELECTION_SEPARATOR))
    }
    var groupRaw by rememberSaveable {
        mutableStateOf(initial?.personalGroupIds.orEmpty().joinToString(COMPARISON_SELECTION_SEPARATOR))
    }
    var excludedOwn by rememberSaveable(stateSaver = Saver(
        save = { encodeComparisonSets(it) }, restore = { decodeComparisonSets(it) },
    )) { mutableStateOf(initial?.excludedOwnCourses.orEmpty()) }
    var excludedFriend by rememberSaveable(stateSaver = Saver(
        save = { encodeComparisonSets(it) }, restore = { decodeComparisonSets(it) },
    )) { mutableStateOf(initial?.excludedFriendCourseKeys.orEmpty()) }
    var startMinute by rememberSaveable { mutableStateOf(initial?.startMinute ?: 510) }
    var endMinute by rememberSaveable { mutableStateOf(initial?.endMinute ?: 1350) }
    var rangeInitialized by rememberSaveable { mutableStateOf(initial?.rangeCustomized == true) }
    var showFree by rememberSaveable { mutableStateOf(initial?.showFree ?: true) }
    var showConflicts by rememberSaveable { mutableStateOf(initial?.showConflicts ?: true) }
    // 课程筛选展开状态：默认收起；key 为课表/好友 id。
    var filtersExpanded by remember { mutableStateOf(setOf<String>()) }

    val groupIds = groupRaw.split(COMPARISON_SELECTION_SEPARATOR).filter { it.isNotBlank() }
    val standaloneIds = personalRaw.split(COMPARISON_SELECTION_SEPARATOR).filter { it.isNotBlank() }
    val personalIds = (standaloneIds + personals.filterIsInstance<ComparisonPersonalItem.Group>()
        .filter { it.groupId in groupIds }.flatMap { it.members.map { member -> member.id } }).distinct()
    val friendIds = friendRaw.split(COMPARISON_SELECTION_SEPARATOR).filter { it.isNotBlank() }
    val allChoices = personals.flatMap {
        when (it) {
            is ComparisonPersonalItem.Single -> listOf(it.choice)
            is ComparisonPersonalItem.Group -> it.members
        }
    }
    var ownCourses by remember { mutableStateOf<Map<String, List<ComparisonCourseChoice>>>(emptyMap()) }
    var friendMenus by remember { mutableStateOf<Map<FriendTimetableEntity, FriendCourseMenu>>(emptyMap()) }

    LaunchedEffect(personalIds.toList()) {
        ownCourses = emptyMap()
        if (personalIds.isNotEmpty()) {
            combine(personalIds.map { id -> loadOwnCourses(id).map { id to it } }) { it.toMap() }
                .catch { ownCourses = emptyMap() }
                .collect { ownCourses = it }
        }
    }
    LaunchedEffect(friends) {
        friendMenus = emptyMap()
        friends.forEach { friend ->
            val menu = readFriendCourseMenu(friend)
            if (menu.valid && friend.shareId !in excludedFriend) {
                excludedFriend = excludedFriend + (friend.shareId to menu.courses.mapIndexedNotNull { index, course ->
                    course.key.takeIf { index in initial?.excludedFriendCourses?.get(friend.shareId).orEmpty() }
                }.toSet())
            }
            friendMenus = friendMenus + (friend to menu)
        }
    }

    val anchor = allChoices.firstOrNull { it.id == personalIds.firstOrNull() }?.periods
    val rangePeriods = anchor?.takeIf(cn.limpu.hita.feature.timetablecompare.PeriodStructure::isValid)
    // 展示视口跟随课表作息：选定我的课表后自动覆盖完整的一天，避免旧配置把晚节裁掉。
    LaunchedEffect(anchor) {
        if (rangePeriods != null &&
            (!rangeInitialized || startMinute < rangePeriods.first().startMinute ||
                endMinute > rangePeriods.last().endMinute)) {
            startMinute = rangePeriods.first().startMinute
            endMinute = rangePeriods.last().endMinute
            rangeInitialized = true
        }
    }
    fun choiceEnabled(choice: ComparisonPersonalChoice): Boolean {
        if (!choice.valid) return false
        if (choice.id in personalIds) return true
        return anchor == null || choice.periods == anchor
    }

    fun togglePersonal(id: String) {
        val next = if (id in standaloneIds) standaloneIds - id else standaloneIds + id
        personalRaw = next.joinToString(COMPARISON_SELECTION_SEPARATOR)
    }

    fun toggleFriend(id: String) {
        val next = if (id in friendIds) friendIds - id else friendIds + id
        friendRaw = next.joinToString(COMPARISON_SELECTION_SEPARATOR)
    }

    val structures = buildList {
        personalIds.mapNotNull { id -> allChoices.firstOrNull { it.id == id }?.periods }.forEach { add(it) }
        friendIds.mapNotNull { id -> friends.firstOrNull { it.shareId == id }?.let { friendMenus[it]?.periods } }
            .forEach { add(it) }
    }
    val draft = if (personalIds.isEmpty() || friendIds.isEmpty()) null else ComparisonConfig(
        personalId = standaloneIds.firstOrNull().orEmpty(),
        friendId = friendIds.first(),
        startMinute = startMinute,
        endMinute = endMinute,
        showFree = showFree,
        showConflicts = showConflicts,
        personalIds = standaloneIds,
        friendIds = friendIds,
        excludedOwnCourses = excludedOwn.filterKeys { it in personalIds },
        excludedFriendCourseKeys = excludedFriend.filterKeys { it in friendIds },
        // 面板里确认过的范围才是有效视口；滑块已自动吸附到所选课表作息边界。
        rangeCustomized = true,
        personalGroupIds = groupIds,
    )
    val menusReady = friendIds.all { id -> friends.firstOrNull { it.shareId == id }?.let { friendMenus[it]?.valid } == true } &&
        personalIds.all { it in ownCourses } && groupIds.all { id ->
            personals.filterIsInstance<ComparisonPersonalItem.Group>().any { it.groupId == id }
        }
    val problem = if (draft != null && menusReady && structures.size == personalIds.size + friendIds.size) {
        comparisonGroupError(draft, structures)
    } else if (draft != null && menusReady) {
        ConfigProblem.INVALID_STRUCTURE
    } else {
        null
    }
    val canConfirm = draft != null && problem == null && menusReady

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.comparison_config_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.comparison_multi_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            ConfigSectionHeader(stringResource(R.string.comparison_personal))
            if (personals.isEmpty()) SheetGuideRow(stringResource(R.string.comparison_manage_personal), onManage)
            personals.forEach { item ->
                when (item) {
                    is ComparisonPersonalItem.Single -> {
                        val choice = item.choice
                        val enabled = choiceEnabled(choice)
                        ParticipantCard(
                            title = choice.name.ifBlank { stringResource(R.string.default_timetable_name) },
                            checked = choice.id in personalIds,
                            enabled = enabled,
                            disabledReason = when {
                                !choice.valid -> stringResource(R.string.comparison_invalid_structure)
                                !enabled -> stringResource(R.string.comparison_incompatible_short)
                                else -> null
                            },
                            onToggle = { togglePersonal(choice.id) },
                        ) {
                            if (choice.id in personalIds) {
                                CourseFilterSection(
                                    participantKey = choice.id,
                                    courses = ownCourses[choice.id].orEmpty(),
                                    excluded = excludedOwn[choice.id].orEmpty(),
                                    busy = false,
                                    expanded = choice.id in filtersExpanded,
                                    onExpandChange = {
                                        filtersExpanded = if (it) filtersExpanded + choice.id else filtersExpanded - choice.id
                                    },
                                    onToggle = { key ->
                                        val current = excludedOwn[choice.id].orEmpty()
                                        excludedOwn = excludedOwn + (choice.id to
                                            if (key in current) current - key else current + key)
                                    },
                                )
                            }
                        }
                    }
                    is ComparisonPersonalItem.Group -> {
                        val memberIds = item.members.map { it.id }
                        val selectedCount = if (item.groupId in groupIds) memberIds.size else 0
                        val groupState = if (selectedCount > 0) ToggleableState.On else ToggleableState.Off
                        val allValid = item.members.all { it.valid }
                        val compatible = item.members.map { it.periods }.distinct().size == 1 &&
                            (anchor == null || item.members.all { it.periods == anchor })
                        val enabled = (allValid && compatible) || selectedCount > 0
                        ParticipantCard(
                            title = item.name,
                            subtitle = stringResource(R.string.timetable_group_member_count, item.members.size),
                            triState = groupState,
                            checked = groupState != ToggleableState.Off,
                            enabled = enabled,
                            disabledReason = when {
                                !allValid -> stringResource(R.string.comparison_invalid_structure)
                                !compatible -> stringResource(R.string.comparison_incompatible_short)
                                else -> null
                            },
                            onToggle = {
                                val next = if (item.groupId in groupIds) groupIds - item.groupId
                                    else groupIds + item.groupId
                                groupRaw = next.joinToString(COMPARISON_SELECTION_SEPARATOR)
                            },
                        ) {
                            // 组合整体参与：课程筛选按成员逐个展开。
                            memberIds.filter { it in personalIds }.forEach { memberId ->
                                val member = item.members.first { it.id == memberId }
                                CourseFilterSection(
                                    participantKey = memberId,
                                    title = member.name,
                                    courses = ownCourses[memberId].orEmpty(),
                                    excluded = excludedOwn[memberId].orEmpty(),
                                    busy = false,
                                    expanded = memberId in filtersExpanded,
                                    onExpandChange = {
                                        filtersExpanded = if (it) filtersExpanded + memberId else filtersExpanded - memberId
                                    },
                                    onToggle = { key ->
                                        val current = excludedOwn[memberId].orEmpty()
                                        excludedOwn = excludedOwn + (memberId to
                                            if (key in current) current - key else current + key)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            ConfigSectionHeader(stringResource(R.string.comparison_friend))
            if (friends.isEmpty()) SheetGuideRow(stringResource(R.string.timetable_social_import), onImport)
            friends.forEach { friend ->
                val menu = friendMenus[friend]
                val enabled = friend.shareId in friendIds ||
                    (menu != null && menu.valid && anchor != null && menu.periods == anchor)
                val name = friend.remark?.takeIf { it.isNotBlank() } ?: friend.nickname
                ParticipantCard(
                    title = name,
                    checked = friend.shareId in friendIds,
                    enabled = enabled,
                    disabledReason = when {
                        menu == null -> null
                        !menu.valid -> stringResource(R.string.comparison_invalid_structure)
                        anchor == null -> stringResource(R.string.comparison_pick_personal_first)
                        menu.periods != anchor -> stringResource(R.string.comparison_incompatible_short)
                        else -> null
                    },
                    onToggle = { toggleFriend(friend.shareId) },
                ) {
                    if (friend.shareId in friendIds) {
                        CourseFilterSection(
                            participantKey = friend.shareId,
                            courses = menu?.courses.orEmpty(),
                            excluded = excludedFriend[friend.shareId].orEmpty(),
                            busy = menu?.busy == true,
                            expanded = friend.shareId in filtersExpanded,
                            onExpandChange = {
                                filtersExpanded = if (it) filtersExpanded + friend.shareId else filtersExpanded - friend.shareId
                            },
                            onToggle = { key ->
                                val current = excludedFriend[friend.shareId].orEmpty()
                                excludedFriend = excludedFriend + (friend.shareId to
                                    if (key in current) current - key else current + key)
                            },
                        )
                    }
                }
            }
            if (friends.isNotEmpty()) SheetGuideRow(stringResource(R.string.comparison_view_friends), onFriends)

            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.comparison_range_display, comparisonTime(startMinute), comparisonTime(endMinute)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    rangePeriods?.let { periods ->
                        Text(
                            stringResource(R.string.comparison_range_included,
                                periods.count { it.startMinute >= startMinute && it.endMinute <= endMinute }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        startMinute = rangePeriods?.firstOrNull()?.startMinute ?: 510
                        endMinute = rangePeriods?.lastOrNull()?.endMinute ?: 1350
                    }) {
                        Text(stringResource(R.string.comparison_range_reset),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                RangeSlider(
                    value = startMinute.toFloat()..endMinute.toFloat(),
                    onValueChange = { range ->
                        val lower = rangePeriods?.firstOrNull()?.startMinute ?: 0
                        val upper = rangePeriods?.lastOrNull()?.endMinute ?: 1440
                        val newStart = ((range.start / 5f).roundToInt() * 5).coerceIn(lower, upper)
                        val newEnd = ((range.endInclusive / 5f).roundToInt() * 5).coerceIn(lower, upper)
                        if (newStart < newEnd) {
                            startMinute = newStart
                            endMinute = newEnd
                        }
                    },
                    valueRange = (rangePeriods?.firstOrNull()?.startMinute ?: 0).toFloat()..
                        (rangePeriods?.lastOrNull()?.endMinute ?: 1440).toFloat(),
                )
            }
            problem?.let {
                Text(stringResource(when (it) {
                    ConfigProblem.INVALID_RANGE -> R.string.comparison_invalid_range
                    ConfigProblem.INVALID_STRUCTURE -> R.string.comparison_invalid_structure
                    ConfigProblem.INCOMPATIBLE -> R.string.comparison_incompatible_periods
                }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SwitchRow(stringResource(R.string.comparison_show_free), MaterialTheme.colorScheme.tertiary,
                outlined = false, checked = showFree) { showFree = it }
            SwitchRow(stringResource(R.string.comparison_show_conflicts), MaterialTheme.colorScheme.error,
                outlined = true, checked = showConflicts) { showConflicts = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(enabled = canConfirm, onClick = { draft?.let(onConfirm) }) {
                    Text(stringResource(R.string.button_confirm))
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp))
}

/** 参与者卡片：圆角弱底容器 + 复选行；组合课表用三态复选。 */
@Composable
private fun ParticipantCard(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    disabledReason: String?,
    onToggle: () -> Unit,
    subtitle: String? = null,
    triState: ToggleableState? = null,
    content: @Composable () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (checked) 0.55f else 0.30f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (triState != null) {
                    TriStateCheckbox(state = triState, onClick = null, enabled = enabled)
                } else {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                }
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    disabledReason?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

/**
 * 课程筛选：默认收起且全选；展开后逐项勾选。
 * 忙闲课表没有课程详情，只能整表参与。
 */
@Composable
private fun CourseFilterSection(
    participantKey: String,
    courses: List<ComparisonCourseChoice>,
    excluded: Set<String>,
    busy: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    title: String? = null,
) {
    if (busy) {
        Text(stringResource(R.string.comparison_busy_no_course_filter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, bottom = 6.dp))
        return
    }
    if (courses.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onExpandChange(!expanded) }
                .padding(start = 36.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (title?.let { "$it · " } ?: "") + stringResource(R.string.comparison_course_filter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (excluded.isEmpty()) stringResource(R.string.comparison_course_filter_all)
                    else stringResource(R.string.comparison_course_filter_excluded, excluded.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (excluded.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(if (expanded) R.string.comparison_collapse else R.string.comparison_expand),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 36.dp)) {
                courses.forEach { course ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(course.key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = course.key !in excluded, onCheckedChange = null)
                        Text(course.label, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetGuideRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** 显示开关行：语义色样块 + 文字 + 开关；形态与对比网格的填充/边框编码一致。 */
@Composable
private fun SwitchRow(label: String, color: androidx.compose.ui.graphics.Color,
    outlined: Boolean, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.padding(start = 4.dp).size(10.dp).then(
                if (outlined) Modifier.border(1.5.dp, color, RoundedCornerShape(3.dp))
                else Modifier.background(color.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
            )
        )
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
