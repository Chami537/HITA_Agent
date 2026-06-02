package cn.limpu.hita.ui.eas.exam

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cn.limpu.hita.R
import cn.limpu.hita.data.AppDatabase
import cn.limpu.hita.data.model.eas.ExamItem
import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.Timetable
import cn.limpu.hita.ui.design.HitaComposeTheme
import cn.limpu.hita.ui.design.HitaTheme
import cn.limpu.hita.utils.LogUtils
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ExamDetailFragment(
    private val exam: ExamItem
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.limpu.style.R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HitaComposeTheme() {
                    ExamDetailSheet(
                        exam = exam,
                        onImport = { importExamToTimetable() }
                    )
                }
            }
        }
    }

    private fun importExamToTimetable() {
        val context = requireContext()
        Toast.makeText(context, "正在导入...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val appDatabase = AppDatabase.getDatabase(context.applicationContext as Application)
                val eventItemDao = appDatabase.eventItemDao()
                val timetableDao = appDatabase.timetableDao()
                val defaultTimetable = timetableDao.getFirstCustomTimetableSync()
                val timetable = defaultTimetable ?: createDefaultTimetable(timetableDao)
                val result = importExamEvent(timetable, eventItemDao)

                requireActivity().runOnUiThread {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    if (result.success) {
                        LogUtils.d("✅ ${result.message}", "ExamDetailFragment")
                        dismiss()
                    } else {
                        LogUtils.e("❌ ${result.message}", null, "ExamDetailFragment")
                    }
                }
            } catch (e: Exception) {
                LogUtils.e("❌ 导入考试失败: ${e.message}", e, "ExamDetailFragment")
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private data class ImportResult(
        val success: Boolean,
        val message: String
    )

    private fun createDefaultTimetable(timetableDao: cn.limpu.hita.data.source.dao.TimetableDao): Timetable {
        val defaultPrefix = requireContext().getString(R.string.default_timetable_name)
        val existingTables = timetableDao.getTimetableNamesWithDefaultSync("$defaultPrefix%")
        var maxNumber = 0
        for (tableName in existingTables) {
            val number = try {
                tableName.replace(defaultPrefix, "").trim().toInt()
            } catch (e: NumberFormatException) {
                0
            }
            if (number > maxNumber) {
                maxNumber = number
            }
        }

        val newTable = Timetable().apply {
            id = UUID.randomUUID().toString()
            name = if (maxNumber > 0) "$defaultPrefix ${maxNumber + 1}" else defaultPrefix
            code = ""
            startTime = Timestamp(System.currentTimeMillis())
            createdAt = Timestamp(System.currentTimeMillis())
        }

        timetableDao.saveTimetableSync(newTable)
        LogUtils.d("✅ 创建默认课表: ${newTable.name}", "ExamDetailFragment")
        return newTable
    }

    private fun importExamEvent(
        timetable: Timetable,
        eventItemDao: cn.limpu.hita.data.source.dao.EventItemDao
    ): ImportResult {
        val existingEvents = eventItemDao.getEventsOfTimetableSync(timetable.id)
        val isDuplicate = existingEvents.any { event ->
            event.type == EventItem.TYPE.EXAM &&
                (event.name == exam.courseName || event.name == "[考试] ${exam.courseName}") &&
                event.place == exam.examLocation
        }

        if (isDuplicate) {
            return ImportResult(false, "该考试已导入默认课表")
        }

        val examEvent = parseExamToEvent(exam, timetable.id)
            ?: return ImportResult(false, "考试时间格式解析失败")

        eventItemDao.insertEventSync(examEvent)
        return ImportResult(true, "已导入到默认课表: ${timetable.name}")
    }

    private fun parseExamToEvent(exam: ExamItem, timetableId: String): EventItem? {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = exam.examDate ?: return null
            val parsedDate = dateFormat.parse(date) ?: return null
            val timeRange = exam.examTime ?: return null
            val times = timeRange.split("-")
            if (times.size != 2) return null

            val startTime = timeFormat.parse(times[0]) ?: return null
            val endTime = timeFormat.parse(times[1]) ?: return null
            val calendarStart = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, startTime.hours)
                set(Calendar.MINUTE, startTime.minutes)
            }
            val calendarEnd = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, endTime.hours)
                set(Calendar.MINUTE, endTime.minutes)
            }

            return EventItem().apply {
                type = EventItem.TYPE.EXAM
                source = EventItem.SOURCE_EAS_IMPORT
                name = "[考试] " + (exam.courseName ?: "考试")
                place = exam.examLocation ?: ""
                teacher = ""
                subjectId = ""
                this.timetableId = timetableId
                from = Timestamp(calendarStart.timeInMillis)
                to = Timestamp(calendarEnd.timeInMillis)
                fromNumber = 0
                lastNumber = 0
            }
        } catch (e: Exception) {
            LogUtils.e("❌ 解析考试时间失败: ${e.message}", e, "ExamDetailFragment")
            return null
        }
    }
}

@Composable
private fun ExamDetailSheet(
    exam: ExamItem,
    onImport: () -> Unit
) {
    val tokens = HitaTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.xl)
        ) {
            Text(
                text = exam.courseName ?: "未知课程",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(tokens.spacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xl)) {
                DetailColumn(
                    label = stringResource(R.string.exam_time),
                    value = exam.examTime.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
                DetailColumn(
                    label = stringResource(R.string.exam_location),
                    value = exam.examLocation.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xl)) {
                DetailColumn(
                    label = stringResource(R.string.exam_campus),
                    value = exam.campusName.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
                DetailColumn(
                    label = stringResource(R.string.exam_type),
                    value = exam.examType.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xl)) {
                DetailColumn(
                    label = stringResource(R.string.exam_term),
                    value = exam.termName.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(46.dp))
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.xl),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = tokens.spacing.sm),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "导入到课表", fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(tokens.spacing.xl))
        }
    }
}

@Composable
private fun DetailColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
        Text(
            text = value.ifBlank { "-" },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
