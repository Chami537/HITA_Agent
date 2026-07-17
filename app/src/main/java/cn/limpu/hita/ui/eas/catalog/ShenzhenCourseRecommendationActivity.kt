package cn.limpu.hita.ui.eas.catalog

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseRecommendationResult
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationCreditProgress
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationPreference
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationTrack
import cn.limpu.hita.data.model.eas.ShenzhenRecommendedPlan
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.data.model.eas.recognizedCrossMajorCourseCodes
import cn.limpu.hita.ui.base.ComposeViewBinding
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import cn.limpu.hita.ui.design.hitaGlassCardBorder
import cn.limpu.hita.ui.design.hitaGlassCardColors
import cn.limpu.hita.ui.design.hitaGlassCardModifier
import cn.limpu.hita.ui.eas.EASActivity
import cn.limpu.hita.utils.TermNameFormatter
import com.limpu.component.data.DataState
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class ShenzhenCourseRecommendationActivity :
    EASActivity<ShenzhenCourseRecommendationViewModel, ComposeViewBinding>() {

    companion object {
        private const val EXTRA_YEAR = "year"
        private const val EXTRA_TERM = "term"
        private const val EXTRA_YEAR_NAME = "year_name"
        private const val EXTRA_TERM_NAME = "term_name"

        fun intent(context: Context, term: TermItem) =
            Intent(context, ShenzhenCourseRecommendationActivity::class.java).apply {
                putExtra(EXTRA_YEAR, term.yearCode)
                putExtra(EXTRA_TERM, term.termCode)
                putExtra(EXTRA_YEAR_NAME, term.yearName)
                putExtra(EXTRA_TERM_NAME, term.termName)
            }
    }

    override val viewModel: ShenzhenCourseRecommendationViewModel by viewModels()
    private val term by lazy {
        val year = intent.getStringExtra(EXTRA_YEAR).orEmpty()
        val termCode = intent.getStringExtra(EXTRA_TERM).orEmpty()
        TermItem(
            yearCode = year,
            yearName = intent.getStringExtra(EXTRA_YEAR_NAME).orEmpty().ifBlank { year },
            termCode = termCode,
            termName = intent.getStringExtra(EXTRA_TERM_NAME).orEmpty()
        )
    }
    private var options = ShenzhenRecommendationOptions(
        targetAdditionalCredits = 4.5,
        minAdditionalCredits = 3.0,
        maxAdditionalCredits = 6.0
    )

    override fun initViewBinding(): ComposeViewBinding = ComposeViewBinding(ComposeView(this))

    override fun initViews() {
        super.initViews()
        viewModel.recommendations.observe(this) { state ->
            if (state.state == DataState.STATE.NOT_LOGGED_IN) {
                handleSessionExpired(viewModel::retry)
            } else if (state.state == DataState.STATE.SUCCESS) {
                resetSessionRetryState()
            }
        }
        (binding.root as ComposeView).setContent {
            HitaComposeTheme {
                RecommendationScreen(
                    viewModel = viewModel,
                    term = term,
                    initialOptions = options,
                    onBack = { finish() },
                    onGenerate = { selected ->
                        options = selected
                        viewModel.generate(term, selected)
                    },
                    onRetry = { viewModel.retry() }
                )
            }
        }
    }

    override fun refresh() {
        if (!viewModel.retry()) viewModel.generate(term, options)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationScreen(
    viewModel: ShenzhenCourseRecommendationViewModel,
    term: TermItem,
    initialOptions: ShenzhenRecommendationOptions,
    onBack: () -> Unit,
    onGenerate: (ShenzhenRecommendationOptions) -> Unit,
    onRetry: () -> Unit
) {
    val state by viewModel.recommendations.observeAsState()
    val trackState by viewModel.tracks.observeAsState()
    val tracks = trackState?.data.orEmpty()
    var minCreditsText by remember {
        mutableStateOf(formatCreditInput(initialOptions.minAdditionalCredits))
    }
    var maxCreditsText by remember {
        mutableStateOf(formatCreditInput(initialOptions.maxAdditionalCredits))
    }
    var preference by remember { mutableStateOf(initialOptions.preference) }
    var excludeFull by remember { mutableStateOf(initialOptions.excludeFull) }
    var excludeConflicts by remember { mutableStateOf(initialOptions.excludeConflicts) }
    var includePracticeInnovation by remember {
        mutableStateOf(initialOptions.includePracticeInnovationCourses)
    }
    var includeMinorPlanCourses by remember {
        mutableStateOf(initialOptions.includeMinorPlanCourses)
    }
    var plannedTrackId by remember { mutableStateOf(initialOptions.plannedTrackId) }
    val loading = state == null || state?.state in listOf(
        DataState.STATE.NOTHING,
        DataState.STATE.LOADING
    )
    val minCredits = minCreditsText.toDoubleOrNull()
    val maxCredits = maxCreditsText.toDoubleOrNull()
    val rangeError = validateCreditRange(minCredits, maxCredits)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TopAppBar(
            title = {
                Column {
                    Text("智能选课", maxLines = 1)
                    Text(
                        TermNameFormatter.fullTermName(term),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                        contentDescription = "返回",
                        modifier = Modifier.rotate(180f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RecommendationSettings(
                    minCreditsText = minCreditsText,
                    maxCreditsText = maxCreditsText,
                    rangeError = rangeError,
                    preference = preference,
                    excludeFull = excludeFull,
                    excludeConflicts = excludeConflicts,
                    includePracticeInnovation = includePracticeInnovation,
                    includeMinorPlanCourses = includeMinorPlanCourses,
                    tracks = tracks,
                    tracksState = trackState?.state,
                    plannedTrackId = plannedTrackId,
                    onMinCreditsText = { value ->
                        if (isValidCreditInput(value)) minCreditsText = value
                    },
                    onMaxCreditsText = { value ->
                        if (isValidCreditInput(value)) maxCreditsText = value
                    },
                    onPreference = { preference = it },
                    onExcludeFull = { excludeFull = it },
                    onExcludeConflicts = { excludeConflicts = it },
                    onIncludePracticeInnovation = { includePracticeInnovation = it },
                    onIncludeMinorPlanCourses = { includeMinorPlanCourses = it },
                    onPlannedTrack = { plannedTrackId = it },
                    loading = loading,
                    onGenerate = {
                        if (minCredits != null && maxCredits != null && rangeError == null) {
                            onGenerate(
                                ShenzhenRecommendationOptions(
                                    targetAdditionalCredits = (minCredits + maxCredits) / 2.0,
                                    minAdditionalCredits = minCredits,
                                    maxAdditionalCredits = maxCredits,
                                    preference = preference,
                                    excludeFull = excludeFull,
                                    excludeConflicts = excludeConflicts,
                                    includePracticeInnovationCourses = includePracticeInnovation,
                                    includeMinorPlanCourses = includeMinorPlanCourses,
                                    plannedTrackId = plannedTrackId,
                                    recognizedCrossMajorCourseCodes = recognizedCrossMajorCourseCodes(
                                        tracks,
                                        plannedTrackId
                                    )
                                )
                            )
                        }
                    }
                )
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state?.state != DataState.STATE.SUCCESS -> item {
                    RecommendationMessage(
                        state?.message ?: "选课方案生成失败",
                        action = "重试",
                        onAction = onRetry
                    )
                }
                else -> {
                    val result = state?.data
                    if (result != null) item { RecommendationSummary(result) }
                    if (result == null || result.plans.isEmpty()) {
                        item {
                            RecommendationMessage("当前条件下没有可行方案", null, null)
                        }
                    } else {
                        items(result.plans.withIndex().toList()) { indexed ->
                            RecommendationPlanCard(indexed.index + 1, indexed.value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationSettings(
    minCreditsText: String,
    maxCreditsText: String,
    rangeError: String?,
    preference: ShenzhenRecommendationPreference,
    excludeFull: Boolean,
    excludeConflicts: Boolean,
    includePracticeInnovation: Boolean,
    includeMinorPlanCourses: Boolean,
    tracks: List<ShenzhenRecommendationTrack>,
    tracksState: DataState.STATE?,
    plannedTrackId: String,
    onMinCreditsText: (String) -> Unit,
    onMaxCreditsText: (String) -> Unit,
    onPreference: (ShenzhenRecommendationPreference) -> Unit,
    onExcludeFull: (Boolean) -> Unit,
    onExcludeConflicts: (Boolean) -> Unit,
    onIncludePracticeInnovation: (Boolean) -> Unit,
    onIncludeMinorPlanCourses: (Boolean) -> Unit,
    onPlannedTrack: (String) -> Unit,
    loading: Boolean,
    onGenerate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("本学期计划加选", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minCreditsText,
                onValueChange = onMinCreditsText,
                label = { Text("最少") },
                suffix = { Text("学分") },
                singleLine = true,
                isError = rangeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxCreditsText,
                onValueChange = onMaxCreditsText,
                label = { Text("最多") },
                suffix = { Text("学分") },
                singleLine = true,
                isError = rangeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        if (rangeError != null) {
            Text(
                text = rangeError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }
        TrackSelector(
            tracks = tracks,
            state = tracksState,
            selectedTrackId = plannedTrackId,
            onSelected = onPlannedTrack
        )
        Text("课表偏好", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ShenzhenRecommendationPreference.BALANCED to "均衡",
                ShenzhenRecommendationPreference.FREE_DAY to "空一天",
                ShenzhenRecommendationPreference.FEWER_EARLY_CLASSES to "少早八"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = preference == value,
                    onClick = { onPreference(value) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = when (preference) {
                ShenzhenRecommendationPreference.BALANCED ->
                    "均衡：综合考虑有课天数、早八数量和同一天的课程空档。"
                ShenzhenRecommendationPreference.FREE_DAY ->
                    "空一天：优先把课程集中到已有上课日，尽量减少一周有课天数。"
                ShenzhenRecommendationPreference.FEWER_EARLY_CLASSES ->
                    "少早八：优先减少第 1–2 节课程，再考虑上课天数和课程空档。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        RecommendationSwitch("排除零余量", excludeFull, onExcludeFull)
        RecommendationSwitch("排除冲突课程", excludeConflicts, onExcludeConflicts)
        RecommendationSwitch(
            "包含辅修方案课程",
            includeMinorPlanCourses,
            onIncludeMinorPlanCourses
        )
        Text(
            if (includeMinorPlanCourses) {
                "已开启：辅修方案课程会参与推荐，请确认自己已选择相应辅修培养方案。"
            } else {
                "默认关闭：未选择辅修培养方案时通常无法选这类课程。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        RecommendationSwitch(
            "考虑创新创业/社会实践类课程",
            includePracticeInnovation,
            onIncludePracticeInnovation
        )
        Text(
            if (includePracticeInnovation) {
                "已开启：创新研修、创新实验、竞赛指导和社会实践等课程会参与推荐。"
            } else {
                "默认关闭：相关学分通常通过竞赛、项目或实践认定，课程不会进入推荐。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            "优先补足跨专业、文理通识及美育、四史、纯英文要求；" +
                "MOOC 总计最多 2.0 学分计入文理通识总学分，但按实际学分满足美育、四史和" +
                "纯英文子要求。只在本地生成建议，不会自动提交选课。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Button(
            onClick = onGenerate,
            enabled = !loading && rangeError == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "正在分析教务课程…" else "生成推荐方案")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSelector(
    tracks: List<ShenzhenRecommendationTrack>,
    state: DataState.STATE?,
    selectedTrackId: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTrack = tracks.firstOrNull { it.id == selectedTrackId }
    Text("计划分流", fontWeight = FontWeight.SemiBold)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (tracks.isNotEmpty()) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedTrack?.name ?: "暂不按轨道认定",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("培养方案轨道") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("暂不按轨道认定") },
                onClick = {
                    onSelected("")
                    expanded = false
                }
            )
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelected(track.id)
                        expanded = false
                    }
                )
            }
        }
    }
    Text(
        text = when {
            selectedTrack != null -> {
                val recognized = recognizedCrossMajorCourseCodes(tracks, selectedTrack.id).size
                "其他轨道中、本轨道没有的 $recognized 门课程将按跨专业课程参与推荐。"
            }
            state == null || state == DataState.STATE.NOTHING ||
                state == DataState.STATE.LOADING -> "正在读取培养方案分流…"
            state == DataState.STATE.SUCCESS && tracks.isEmpty() ->
                "当前培养方案没有返回可选择的轨道。"
            state != DataState.STATE.SUCCESS ->
                "分流信息读取失败，仍可暂不按轨道认定并生成方案。"
            else -> "选择自己计划修读的轨道，用于识别其他轨道的跨专业课程。"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}

@Composable
private fun RecommendationSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChecked
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RecommendationSummary(result: ShenzhenCourseRecommendationResult) {
    val excludedDetails = buildList {
        if (result.excludedPracticeInnovationCount > 0) {
            add("实践创新 ${result.excludedPracticeInnovationCount}")
        }
        if (result.excludedMinorPlanCount > 0) {
            add("辅修 ${result.excludedMinorPlanCount}")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "计划加选 ${formatCredits(result.minAdditionalCredits)}–" +
                "${formatCredits(result.maxAdditionalCredits)} 学分 · " +
                "已选 ${formatCredits(result.selectedCredits)} 学分 · " +
                "候选 ${result.candidateCount} 个 · " +
                "排除满员 ${result.excludedFullCount} / 冲突 ${result.excludedConflictCount}" +
                excludedDetails.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = " / ", separator = " / ")
                    .orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        if (result.currentCreditProgress.isNotEmpty()) {
            Text(
                "当前培养要求：${formatRequirementProgress(result.currentCreditProgress)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                "MOOC 已计入文理通识 " +
                    "${formatCredits(result.currentCountedMoocCredits)} / " +
                    "${formatCredits(result.moocCreditCap)} 学分",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RecommendationPlanCard(index: Int, plan: ShenzhenRecommendedPlan) {
    val tokens = HitaTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    Card(
        modifier = Modifier.fillMaxWidth().hitaGlassCardModifier(shape),
        shape = shape,
        colors = hitaGlassCardColors(),
        border = hitaGlassCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(tokens.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("方案 $index", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("+${formatCredits(plan.additionalCredits)} 学分", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                plan.summary,
                color = if (plan.conflictCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "选后共 ${formatCredits(plan.totalCredits)} 学分",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (plan.creditProgress.isNotEmpty()) {
                Text(
                    "选后培养要求：${formatRequirementProgress(plan.creditProgress)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    "MOOC 计入文理通识 ${formatCredits(plan.countedMoocCredits)} / 2.0 学分",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            plan.courses.forEachIndexed { courseIndex, course ->
                RecommendationCourseRow(course)
                if (courseIndex < plan.courses.lastIndex) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecommendationCourseRow(course: ShenzhenCourseCatalogItem) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                course.courseName,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("${course.credits} 学分", color = MaterialTheme.colorScheme.primary)
        }
        val details = listOf(
            course.teacher,
            course.selectionPoolName,
            course.courseCategory,
            course.teachingLanguage,
            course.remainingSeats?.let { "余量 $it" }.orEmpty()
        ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        if (details.isNotBlank()) {
            Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (course.schedule.isNotBlank()) {
            Text(
                course.schedule,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun RecommendationMessage(message: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
        }
    }
}

private fun formatCredits(value: Double): String = String.format(Locale.CHINA, "%.1f", value)

private fun formatCreditInput(value: Double): String =
    String.format(Locale.CHINA, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value)

private fun isValidCreditInput(value: String): Boolean =
    value.matches(Regex("^\\d{0,2}(?:\\.\\d?)?$"))

private fun validateCreditRange(min: Double?, max: Double?): String? = when {
    min == null || max == null -> "请输入最少和最多学分"
    min < 0.0 || max < 0.0 -> "学分不能为负数"
    min > 30.0 || max > 30.0 -> "单学期计划加选不能超过 30 学分"
    max < min -> "最多学分不能小于最少学分"
    else -> null
}

private fun formatRequirementProgress(
    rows: List<ShenzhenRecommendationCreditProgress>
): String = rows.joinToString(" · ") { row ->
    if (row.remainingCredits > 0.0001) {
        "${row.label}还需 ${formatCredits(row.remainingCredits)}"
    } else {
        "${row.label}已完成"
    }
}
