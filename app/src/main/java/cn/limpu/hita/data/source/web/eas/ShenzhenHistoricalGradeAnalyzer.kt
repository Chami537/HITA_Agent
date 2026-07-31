package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenTeacherFailureRate
import cn.limpu.hita.data.model.eas.TermItem
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.round

internal data class ShenzhenHistoricalClassStats(
    val teacher: String,
    val scores: List<Double>,
    val excludedIncompleteStudentCount: Int = 0
)

internal object ShenzhenHistoricalGradeAnalyzer {
    fun termYearsBefore(term: TermItem, years: Int): TermItem? {
        val parts = term.yearCode.split('-')
        if (parts.size != 2) return null
        val start = parts[0].toIntOrNull() ?: return null
        val end = parts[1].toIntOrNull() ?: return null
        val yearCode = "${start - years}-${end - years}"
        return TermItem(yearCode, yearCode, term.termCode, term.termName).apply {
            name = listOf(yearCode, term.termName).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    fun matches(reference: ShenzhenCourseCatalogItem, candidate: ShenzhenCourseCatalogItem): Boolean {
        val referenceName = normalizedName(reference.courseName)
        val candidateName = normalizedName(candidate.courseName)
        if (referenceName.isNotBlank() && candidateName.isNotBlank()) {
            return referenceName == candidateName
        }
        return reference.courseCode.trim().equals(candidate.courseCode.trim(), ignoreCase = true)
    }

    fun aggregate(classes: List<ShenzhenHistoricalClassStats>): List<ShenzhenTeacherFailureRate> {
        return classes.groupBy { it.teacher.trim().ifBlank { "未标注教师" } }
            .map { (teacher, rows) ->
                val scores = rows.flatMap { it.scores }
                val studentCount = scores.size
                val failCount = scores.count { it < 60.0 }
                val topCount = ceil(studentCount * 0.2).toInt().coerceAtLeast(1)
                ShenzhenTeacherFailureRate(
                    teacher = teacher,
                    classCount = rows.size,
                    studentCount = studentCount,
                    failCount = failCount,
                    failureRate = if (studentCount == 0) {
                        0.0
                    } else {
                        rounded(failCount * 100.0 / studentCount)
                    },
                    averageScore = rounded(scores.averageOrZero()),
                    top20AverageScore = rounded(
                        scores.sortedDescending().take(topCount).averageOrZero()
                    ),
                    excludedIncompleteStudentCount = rows.sumOf {
                        it.excludedIncompleteStudentCount
                    }
                )
            }
            .sortedWith(compareBy<ShenzhenTeacherFailureRate> { it.failureRate }.thenBy { it.teacher })
    }

    private fun normalizedName(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s·・•]+"), "")
        .replace('（', '(')
        .replace('）', ')')

    private fun rounded(value: Double): Double = round(value * 100.0) / 100.0

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
