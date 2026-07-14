package cn.limpu.hita.data.model.timetable

import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Timestamp
import java.util.Calendar

class TimetableWeekNumberTest {
    @Test
    fun `week number uses floor weeks from normalized term monday`() {
        val timetable = Timetable().apply {
            startTime = Timestamp(millis(2026, Calendar.FEBRUARY, 23, 9, 30, 45, 250))
            endTime = Timestamp(millis(2026, Calendar.JUNE, 28, 23, 59, 0, 0))
        }

        assertEquals(1, timetable.getWeekNumber(millis(2026, Calendar.FEBRUARY, 23)))
        assertEquals(1, timetable.getWeekNumber(millis(2026, Calendar.MARCH, 1)))
        assertEquals(2, timetable.getWeekNumber(millis(2026, Calendar.MARCH, 2)))
        assertEquals(15, timetable.getWeekNumber(millis(2026, Calendar.JUNE, 1)))
    }

    @Test
    fun `week number returns holiday outside timetable range`() {
        val timetable = Timetable().apply {
            startTime = Timestamp(millis(2026, Calendar.FEBRUARY, 23))
            endTime = Timestamp(millis(2026, Calendar.JUNE, 28, 23, 59, 0, 0))
        }

        assertEquals(-1, timetable.getWeekNumber(millis(2026, Calendar.FEBRUARY, 16)))
        assertEquals(-1, timetable.getWeekNumber(millis(2026, Calendar.JUNE, 29)))
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millisecond: Int = 0,
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, millisecond)
        }.timeInMillis
    }
}
