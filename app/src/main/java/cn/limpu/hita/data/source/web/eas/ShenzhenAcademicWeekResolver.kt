package cn.limpu.hita.data.source.web.eas

import cn.limpu.hita.data.model.eas.TermItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

internal object ShenzhenAcademicWeekResolver {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun approximateAnchor(term: TermItem): LocalDate {
        val years = term.yearCode.split("-").mapNotNull(String::toIntOrNull)
        val startYear = years.firstOrNull() ?: LocalDate.now().year
        val endYear = years.getOrNull(1) ?: startYear + 1
        return when (term.termCode.trim()) {
            "1" -> LocalDate.of(startYear, 9, 1)
            "2" -> LocalDate.of(endYear, 2, 1)
            "3" -> LocalDate.of(endYear, 6, 1)
            else -> LocalDate.of(startYear, 1, 1)
        }
    }

    /**
     * 以总览接口中该学期最早出现的日期所在周为教学第 1 周，生成目标周完整 7 天。
     */
    fun resolveWeekDates(termDates: Collection<String>, week: Int): List<String> {
        val firstDate = termDates.asSequence()
            .mapNotNull(::parseDate)
            .minOrNull()
            ?: return emptyList()
        val firstMonday = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val targetMonday = firstMonday.plusWeeks((week.coerceAtLeast(1) - 1).toLong())
        return (0L..6L).map { targetMonday.plusDays(it).format(dateFormatter) }
    }

    fun resolveQueryDates(
        matrixDates: Collection<String>,
        overviewTermDates: Collection<String>,
        requestedWeek: Int
    ): List<String> {
        return if (matrixDates.isNotEmpty()) {
            // matrixDates 已来自指定周，所以只需把稀疏日期补全到其所在周。
            resolveWeekDates(matrixDates, week = 1)
        } else {
            resolveWeekDates(overviewTermDates, week = requestedWeek)
        }
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.trim(), dateFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}
