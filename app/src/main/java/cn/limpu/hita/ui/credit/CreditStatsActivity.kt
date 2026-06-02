package cn.limpu.hita.ui.credit

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.limpu.hita.R
import cn.limpu.hita.data.model.timetable.TermSubject
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import com.limpu.style.ThemeTools
import com.limpu.style.widgets.PopUpFloatPicker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreditStatsActivity : AppCompatActivity() {
    private val viewModel: CreditStatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (ThemeTools.getThemeMode(this)) {
            ThemeTools.MODE.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeTools.MODE.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)

        setContent {
            HitaComposeTheme() {
                CreditStatsScreen(
                    viewModel = viewModel,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onEditGoal = ::showGoalPicker
                )
            }
        }
    }

    private fun showGoalPicker(category: CreditCategorySummary) {
        val picker = PopUpFloatPicker()
            .setDialogTitle(R.string.credit_set_goal)
            .setInitialValue(category.goalCredits ?: category.totalCredits)
        picker.setOnDialogConformListener(object : PopUpFloatPicker.OnDialogConformListener {
            override fun onClick(result: Float) {
                if (result <= 0f) {
                    viewModel.removeGoal(category.type)
                } else {
                    viewModel.setGoal(category.type, result)
                }
            }
        })
        picker.show(supportFragmentManager, "goal_picker")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditStatsScreen(
    viewModel: CreditStatsViewModel,
    onBack: () -> Unit,
    onEditGoal: (CreditCategorySummary) -> Unit
) {
    val tokens = HitaTheme.tokens
    val state by viewModel.creditStats.observeAsState(CreditStatsState())
    val expanded = remember { mutableStateMapOf<TermSubject.TYPE, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.credit_stats_title),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_keyboard_arrow_right_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.rotate(180f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (state.isEmpty) {
            EmptyCreditView()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                CreditSummaryCard(state = state)
                state.categories.forEach { category ->
                    val isExpanded = expanded[category.type] ?: category.expanded
                    CreditCategoryCard(
                        category = category,
                        expanded = isExpanded,
                        onToggleExpanded = {
                            expanded[category.type] = !isExpanded
                        },
                        onEditGoal = { onEditGoal(category) },
                        modifier = Modifier.padding(
                            start = tokens.spacing.lg,
                            top = tokens.spacing.sm,
                            end = tokens.spacing.lg
                        )
                    )
                }
                Text(
                    text = stringResource(R.string.credit_data_from_timetable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .alpha(0.5f)
                        .padding(
                            start = tokens.spacing.xl,
                            top = tokens.spacing.xs,
                            end = tokens.spacing.xl,
                            bottom = tokens.spacing.xl
                        )
                )
            }
        }
    }
}

@Composable
private fun EmptyCreditView() {
    val tokens = HitaTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.xl,
                top = tokens.spacing.xxxxl,
                end = tokens.spacing.xl
            )
            .alpha(0.8f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_empty),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
        }
        Text(
            text = stringResource(R.string.credit_no_data),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = tokens.spacing.md)
        )
    }
}

@Composable
private fun CreditSummaryCard(state: CreditStatsState) {
    val tokens = HitaTheme.tokens
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(tokens.radius.xl),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.lg,
                top = tokens.spacing.md,
                end = tokens.spacing.lg
            )
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetric(
                    label = stringResource(R.string.credit_total),
                    value = String.format("%.1f", state.totalCredits),
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = stringResource(R.string.credit_total_subjects),
                    value = state.totalSubjects.toString(),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = tokens.spacing.md)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetric(
                    label = stringResource(R.string.credit_spa_subtotal),
                    value = String.format("%.1f", state.spaCredits),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    labelSize = 12,
                    valueSize = 14,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = stringResource(R.string.credit_non_spa_subtotal),
                    value = String.format("%.1f", state.nonSpaCredits),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    labelSize = 12,
                    valueSize = 14,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    labelSize: Int = 14,
    valueSize: Int = 24
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = labelSize.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = valueSize.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = if (valueSize >= 20) 4.dp else 2.dp)
        )
    }
}

@Composable
private fun CreditCategoryCard(
    category: CreditCategorySummary,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = HitaTheme.tokens
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(typeColor(category.type))
                )
                Text(
                    text = typeLabel(category.type),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                Text(
                    text = stringResource(R.string.credit_format, category.totalCredits),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onEditGoal,
                    modifier = Modifier
                        .padding(start = tokens.spacing.sm)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_edit_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (category.goalCredits != null && category.goalCredits > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = {
                            (category.totalCredits / category.goalCredits).coerceIn(0f, 1f)
                        },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.credit_goal_progress_format,
                            category.totalCredits,
                            category.goalCredits
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = tokens.spacing.sm)
                    )
                }
            }

            Text(
                text = pluralStringResource(
                    R.plurals.credit_subject_count,
                    category.subjectCount,
                    category.subjectCount
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = tokens.spacing.xs)
            )

            if (category.fieldBreakdown.size > 1) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.credit_collapse_detail
                        else R.string.credit_expand_detail
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = tokens.spacing.xs)
                        .clickable(onClick = onToggleExpanded)
                        .padding(tokens.spacing.xs)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = tokens.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                ) {
                    category.fieldBreakdown.forEach { field ->
                        Text(
                            text = stringResource(
                                R.string.credit_field_row,
                                field.fieldName,
                                field.totalCredits
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun typeLabel(type: TermSubject.TYPE): String {
    return stringResource(
        when (type) {
            TermSubject.TYPE.COM_A -> R.string.credit_type_required
            TermSubject.TYPE.COM_B -> R.string.credit_type_required_check
            TermSubject.TYPE.OPT_A -> R.string.credit_type_limited
            TermSubject.TYPE.OPT_B -> R.string.credit_type_elective
            TermSubject.TYPE.MOOC -> R.string.credit_type_mooc
            else -> R.string.credit_type_unknown
        }
    )
}

private fun typeColor(type: TermSubject.TYPE): Color {
    return when (type) {
        TermSubject.TYPE.COM_A -> Color(0xFF304FFE)
        TermSubject.TYPE.COM_B -> Color(0xFF5C6BC0)
        TermSubject.TYPE.OPT_A -> Color(0xFF26A69A)
        TermSubject.TYPE.OPT_B -> Color(0xFF66BB6A)
        TermSubject.TYPE.MOOC -> Color(0xFFFF7043)
        else -> Color(0xFF9E9E9E)
    }
}
