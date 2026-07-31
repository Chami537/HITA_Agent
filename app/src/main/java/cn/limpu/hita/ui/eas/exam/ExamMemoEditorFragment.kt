package cn.limpu.hita.ui.eas.exam

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cn.limpu.hita.data.model.eas.ExamItem
import cn.limpu.hita.data.model.eas.TermItem
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import cn.limpu.hita.utils.TermNameFormatter
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class ExamMemoEditorFragment(
    private val editingItem: ExamItem?,
    private val defaultTerm: TermItem?,
    private val availableTerms: List<TermItem>,
    private val defaultCampusName: String,
    private val onSave: (ExamItem) -> String?
) : BottomSheetDialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.limpu.style.R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            HitaComposeTheme {
                ExamMemoEditorSheet(
                    editingItem = editingItem,
                    defaultTerm = defaultTerm,
                    availableTerms = availableTerms,
                    defaultCampusName = defaultCampusName,
                    onSave = { item ->
                        val error = onSave(item)
                        if (error == null) dismiss()
                        error
                    }
                )
            }
        }
    }
}

@Composable
private fun ExamMemoEditorSheet(
    editingItem: ExamItem?,
    defaultTerm: TermItem?,
    availableTerms: List<TermItem>,
    defaultCampusName: String,
    onSave: (ExamItem) -> String?
) {
    val tokens = HitaTheme.tokens
    val initialDate = editingItem?.examDate?.takeIf { ExamMemoDraftValidator.validDate(it) }
        ?: LocalDate.now().toString()
    val initialTimes = ExamMemoDraftValidator.parseTimeRange(editingItem?.examTime)
    var courseName by rememberSaveable { mutableStateOf(editingItem?.courseName.orEmpty()) }
    var examDate by rememberSaveable { mutableStateOf(initialDate) }
    var startTime by rememberSaveable { mutableStateOf(initialTimes?.first ?: "09:00") }
    var endTime by rememberSaveable { mutableStateOf(initialTimes?.second ?: "11:00") }
    var examType by rememberSaveable { mutableStateOf(editingItem?.examType?.ifBlank { null } ?: "期末") }
    var examLocation by rememberSaveable { mutableStateOf(editingItem?.examLocation.orEmpty()) }
    var campusName by rememberSaveable {
        mutableStateOf(editingItem?.campusName?.ifBlank { null } ?: defaultCampusName)
    }
    val initialTerm = remember(editingItem?.termId, defaultTerm?.id, availableTerms) {
        availableTerms.firstOrNull { it.id == editingItem?.termId }
            ?: defaultTerm
    }
    var selectedTermId by rememberSaveable { mutableStateOf(initialTerm?.id) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var termMenuExpanded by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val selectedTerm = availableTerms.firstOrNull { it.id == selectedTermId }
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.xl)
        ) {
            Text(
                text = if (editingItem == null) "添加考试备忘录" else "编辑考试备忘录",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "仅保存在本机，不会提交到教务系统",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = tokens.spacing.xs)
            )

            OutlinedTextField(
                value = courseName,
                onValueChange = { courseName = it; validationMessage = null },
                label = { Text("考试或课程名称") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.lg)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(examType) }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        listOf("期中", "期末", "补考", "其他").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { examType = type; typeMenuExpanded = false }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { termMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(selectedTerm?.let(::memoTermName) ?: "不指定学期", maxLines = 1) }
                    DropdownMenu(
                        expanded = termMenuExpanded,
                        onDismissRequest = { termMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("不指定学期") },
                            onClick = { selectedTermId = null; termMenuExpanded = false }
                        )
                        availableTerms.forEach { term ->
                            DropdownMenuItem(
                                text = { Text(memoTermName(term)) },
                                onClick = { selectedTermId = term.id; termMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            Text(
                text = "考试安排",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = tokens.spacing.lg)
            )
            OutlinedButton(
                onClick = {
                    val parsed = runCatching { LocalDate.parse(examDate) }.getOrElse { LocalDate.now() }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            examDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                            validationMessage = null
                        },
                        parsed.year,
                        parsed.monthValue - 1,
                        parsed.dayOfMonth
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.sm)
            ) { Text("日期  $examDate") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                TimeButton(
                    label = "开始",
                    value = startTime,
                    modifier = Modifier.weight(1f),
                    onValue = { startTime = it; validationMessage = null }
                )
                TimeButton(
                    label = "结束",
                    value = endTime,
                    modifier = Modifier.weight(1f),
                    onValue = { endTime = it; validationMessage = null }
                )
            }

            OutlinedTextField(
                value = examLocation,
                onValueChange = { examLocation = it },
                label = { Text("地点（选填）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.md)
            )
            OutlinedTextField(
                value = campusName,
                onValueChange = { campusName = it },
                label = { Text("校区（选填）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.sm)
            )

            validationMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = tokens.spacing.sm)
                )
            }

            Button(
                onClick = {
                    val timeRange = "$startTime-$endTime"
                    val validation = ExamMemoDraftValidator.validate(courseName, examDate, timeRange)
                    if (validation != null) {
                        validationMessage = validation
                        return@Button
                    }
                    val item = ExamItem().apply {
                        memoId = editingItem?.memoId ?: UUID.randomUUID().toString()
                        this.courseName = courseName.trim()
                        this.examDate = examDate
                        examTime = timeRange
                        this.examType = examType.trim()
                        this.examLocation = examLocation.trim().ifBlank { null }
                        termId = selectedTerm?.id
                        termName = selectedTerm?.let(::memoTermName)
                        this.campusName = campusName.trim().ifBlank { null }
                    }
                    validationMessage = onSave(item)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xl),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (editingItem == null) "保存备忘录" else "保存修改")
            }
            Spacer(modifier = Modifier.height(tokens.spacing.lg))
        }
    }
}

private fun memoTermName(term: TermItem): String =
    TermNameFormatter.fullTermName(term)

@Composable
private fun TimeButton(
    label: String,
    value: String,
    modifier: Modifier,
    onValue: (String) -> Unit
) {
    val context = LocalContext.current
    val parts = value.split(':').mapNotNull(String::toIntOrNull)
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onValue(String.format(Locale.US, "%02d:%02d", hour, minute)) },
                parts.getOrNull(0) ?: 9,
                parts.getOrNull(1) ?: 0,
                true
            ).show()
        },
        modifier = modifier
    ) {
        Text("$label  $value")
    }
}

internal object ExamMemoDraftValidator {
    fun validate(courseName: String, date: String, timeRange: String): String? {
        if (courseName.trim().isEmpty()) return "请填写考试或课程名称"
        if (!validDate(date)) return "考试日期格式无效"
        val times = parseTimeRange(timeRange) ?: return "考试时间格式无效"
        if (toMinutes(times.second) <= toMinutes(times.first)) return "结束时间需晚于开始时间"
        return null
    }

    fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

    fun parseTimeRange(value: String?): Pair<String, String>? {
        val parts = value?.split('-', limit = 2) ?: return null
        if (parts.size != 2 || !validTime(parts[0]) || !validTime(parts[1])) return null
        return parts[0] to parts[1]
    }

    private fun validTime(value: String): Boolean {
        val parts = value.split(':').mapNotNull(String::toIntOrNull)
        return parts.size == 2 && parts[0] in 0..23 && parts[1] in 0..59
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(':').map(String::toInt)
        return parts[0] * 60 + parts[1]
    }
}
