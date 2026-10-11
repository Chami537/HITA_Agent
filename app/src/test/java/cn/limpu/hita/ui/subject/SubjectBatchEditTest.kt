package cn.limpu.hita.ui.subject

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.TimeInDay
import cn.limpu.hita.data.model.timetable.TimePeriodInDay
import cn.limpu.hita.data.model.timetable.Timetable
import org.junit.Assert.*
import org.junit.Test
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class SubjectBatchEditTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, minute: Int): Long = monday.plusDays(day.toLong())
        .atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    private fun table(startDay: Int = 0) = Timetable().apply {
        id = "table"; startTime = Timestamp(at(startDay, 0)); endTime = Timestamp(at(28, 0))
        scheduleStructure = listOf(
            TimePeriodInDay(TimeInDay(8, 30), TimeInDay(10, 15)),
            TimePeriodInDay(TimeInDay(10, 30), TimeInDay(12, 15)),
        )
    }
    private fun course(id: String, day: Int, period: Int) = EventItem().apply {
        this.id = id; timetableId = "table"; subjectId = "subject"
        from = Timestamp(at(day, if (period == 1) 510 else 630))
        to = Timestamp(at(day, if (period == 1) 615 else 735))
        fromNumber = period; lastNumber = 1
    }

    @Test fun onlyEnabledSlotGroupMovesAcrossEveryOccurrence() {
        val first = course("first", 0, 1)
        val secondWeek = course("next", 7, 1)
        val unchanged = course("other", 2, 2)
        val unchangedTime = unchanged.from.time
        val (edited, skipped) = applySubjectBatchEdit(listOf(first, secondWeek, unchanged), table(),
            "new place", null, null, mapOf(Triple(1, 1, 1) to Triple(2, 2, 2)))
        assertFalse(skipped)
        assertEquals(listOf(at(1, 630), at(8, 630), unchangedTime), edited.map { it.from.time })
        assertTrue(edited.all { it.place == "new place" })
        assertEquals(2, edited.first().fromNumber)
        assertEquals(1, edited.first().lastNumber)
    }

    @Test fun changingTwoGroupsDoesNotCascadeIntoAnotherGroupsTarget() {
        val first = course("first", 0, 1)
        val second = course("second", 1, 2)
        applySubjectBatchEdit(listOf(first, second), table(), null, null, null, mapOf(
            Triple(1, 1, 1) to Triple(2, 2, 2), Triple(2, 2, 2) to Triple(3, 1, 1)))
        assertEquals(at(1, 630), first.from.time)
        assertEquals(at(2, 510), second.from.time)
    }

    @Test fun nonMondayTermBoundaryDoesNotShiftEditedWeek() {
        val event = course("friday", 4, 1)
        assertTrue(event.applyPeriodTime(table(startDay = 2), 4, 2, 2))
        assertEquals(at(3, 630), event.from.time)
        assertEquals(at(3, 735), event.to.time)
    }

    @Test fun systemTimezoneDoesNotChangeSlotGroupsOrMoveCampusWeek() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val event = course("monday", 0, 1)
            assertEquals(Triple(1, 1, 1), event.slotTriple())
            assertTrue(event.applyPeriodTime(table(), 3, 2, 2))
            assertEquals(at(2, 630), event.from.time)
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun invalidTargetSkipsTimeWithoutDroppingOtherEdits() {
        val event = course("one", 0, 1)
        val before = event.from.time
        val (edited, skipped) = applySubjectBatchEdit(listOf(event), table(), "room", "teacher",
            Triple(8, 1, 1))
        assertTrue(skipped)
        assertEquals(before, edited.single().from.time)
        assertEquals("room", event.place)
        assertEquals("teacher", event.teacher)
    }
}
