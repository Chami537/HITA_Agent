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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.limpu.hita.R
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseRecommendationResult
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationOptions
import cn.limpu.hita.data.model.eas.ShenzhenRecommendationPreference
import cn.limpu.hita.data.model.eas.ShenzhenRecommendedPlan
import cn.limpu.hita.data.model.eas.TermItem
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
    private var options = ShenzhenRecommendationOptions()

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
    var targetCredits by remember { mutableStateOf(initialOptions.targetAdditionalCredits) }
    var preference by remember { mutableStateOf(initialOptions.preference) }
    var excludeFull by remember { mutableStateOf(initialOptions.excludeFull) }
    var excludeConflicts by remember { mutableStateOf(initialOptions.excludeConflicts) }
    val loading = state == null || state?.state in listOf(
        DataState.STATE.NOTHING,
        DataState.STATE.LOADING
    )

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
                    targetCredits = targetCredits,
                    preference = preference,
                    excludeFull = excludeFull,
                    excludeConflicts = excludeConflicts,
                    onTargetCredits = { targetCredits = it },
                    onPreference = { preference = it },
                    onExcludeFull = { excludeFull = it },
                    onExcludeConflicts = { excludeConflicts = it },
                    loading = loading,
                    onGenerate = {
                        onGenerate(
                            ShenzhenRecommendationOptions(
                                targetAdditionalCredits = targetCredits,
                                preference = preference,
                                excludeFull = excludeFull,
                                excludeConflicts = excludeConflicts
                            )
                        )
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
    targetCredits: Double,
    preference: ShenzhenRecommendationPreference,
    excludeFull: Boolean,
    excludeConflicts: Boolean,
    onTargetCredits: (Double) -> Unit,
    onPreference: (ShenzhenRecommendationPreference) -> Unit,
    onExcludeFull: (Boolean) -> Unit,
    onExcludeConflicts: (Boolean) -> Unit,
    loading: Boolean,
    onGenerate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("还需学分", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3.0, 6.0, 9.0, 12.0).forEach { credits ->
                FilterChip(
                    selected = targetCredits == credits,
                    onClick = { onTargetCredits(credits) },
                    label = { Text(credits.toInt().toString()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
        RecommendationSwitch("排除零余量", excludeFull, onExcludeFull)
        RecommendationSwitch("排除冲突课程", excludeConflicts, onExcludeConflicts)
        Text(
            "只读取教务数据并在本地生成建议，不会自动提交选课。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Button(
            onClick = onGenerate,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "正在分析教务课程…" else "生成推荐方案")
        }
    }
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
    Text(
        "已选 ${formatCredits(result.selectedCredits)} 学分 · " +
            "候选 ${result.candidateCount} 个 · " +
            "排除满员 ${result.excludedFullCount} / 冲突 ${result.excludedConflictCount}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
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
            course.remainingSeats?.let { "余量 $it" }.orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" · ")
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
