package cn.limpu.hita.ui.main.timetable.compare

import cn.limpu.hita.data.model.timetable.EventItem
import cn.limpu.hita.data.model.timetable.share.FriendTimetableEntity
import cn.limpu.hita.data.repository.ComparisonFriendSource
import cn.limpu.hita.feature.timetablecompare.*
import cn.limpu.hita.feature.timetableshare.protocol.*
import java.time.*
import java.sql.Timestamp
import org.junit.Assert.*
import org.junit.Test

class ComparisonProjectionTest {
    private val date = LocalDate.of(2026, 10, 5)
    private fun at(day: LocalDate = date, minute: Int) = day.atStartOfDay(CAMPUS_ZONE).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    private fun span(a: Int, b: Int) = TimeSpan(at(minute=a), at(minute=b))
    @Test fun bridgePreservesDisplayedMondayDateAcrossZones() {
        val ny=ZoneId.of("America/New_York")
        val monday=date.atStartOfDay(ny).toInstant().toEpochMilli()
        assertEquals(at(minute=0), comparisonMonday(monday,ny))
        val sundayNight=date.minusDays(1).atTime(23,59).atZone(ny).toInstant().toEpochMilli()
        assertEquals(at(date.minusDays(7),0),comparisonMonday(sundayNight,ny))
    }
    @Test fun selectedRawCoursesUseActualWeekAndSplitMidnightWithoutMutation() {
        fun event(id: String, start: Long, end: Long)=EventItem().apply { this.id=id; timetableId="own"; from=Timestamp(start); to=Timestamp(end); name=id }
        val crossing=event("cross",at(date.minusDays(1),1380),at(minute=30))
        val odd=event("odd",at(date.plusDays(7),510),at(date.plusDays(7),615))
        val split=event("split",at(minute=1380),at(date.plusDays(1),60))
        val result=projectComparisonCourses(listOf(crossing,odd,split),at(minute=0))
        assertEquals(3,result.size)
        assertEquals(listOf("cross","split","split"),result.map { it.original.id })
        assertEquals(3,result.map { it.display.id }.distinct().size)
        assertEquals(at(minute=0),result.first().display.from.time)
        assertEquals(at(date.minusDays(1),1380),crossing.from.time)
        assertEquals(result.map { it.display.id },projectComparisonCourses(listOf(crossing,odd,split),at(minute=0)).map { it.display.id })
        assertTrue(result.all { it.display.color != 0 })
    }
    @Test fun friendDetailsNeverReadBusyDictionaryAndRespectExclusions() {
        val poison=object: AbstractList<String>() { override val size:Int get()=error("dictionary accessed"); override fun get(index:Int):String=error("dictionary accessed") }
        fun side(mode: ShareMode, names: List<String>): ComparisonFriendSource {
            val snapshot=SharedTimetable("id","friend",mode,SharedTerm("","",at(minute=0),at(date.plusDays(7),0)),emptyList(),names,listOf(SharedOccurrence(at(minute=570),at(minute=600),0,"private place")))
            val entity=FriendTimetableEntity("id","friend",null,"t",0,0,mode.name,"{}","",0,0)
            return ComparisonFriendSource(entity,snapshot,ComparisonSchedule(TimeSpan(0,1),emptyList(),emptyList()))
        }
        val full=comparisonFriendSlotDetails(span(510,615),side(ShareMode.FULL,listOf("friend course")),emptySet())
        assertEquals("friend course",full.single().name)
        assertEquals("private place",full.single().place)
        assertTrue(comparisonFriendSlotDetails(span(510,615),side(ShareMode.FULL,listOf("friend course")),setOf(0)).isEmpty())
        val busy=comparisonFriendSlotDetails(span(510,615),side(ShareMode.BUSY,poison),emptySet())
        assertNull(busy.single().name)
        assertNull(busy.single().place)
    }
    @Test fun projectionExcludesActivitiesAndExams() {
        val exam=EventItem().apply { type=EventItem.TYPE.EXAM; from=Timestamp(at(minute=510)); to=Timestamp(at(minute=615)) }
        val activity=EventItem().apply { type=EventItem.TYPE.OTHER; from=Timestamp(at(minute=510)); to=Timestamp(at(minute=615)) }
        assertTrue(projectComparisonCourses(listOf(exam,activity),at(minute=0)).isEmpty())
    }
    @Test fun initialCampusWeekIgnoresDeviceSundayAndTimezone() {
        val campusMondayMorning = at(minute = 60)
        val ny = ZoneId.of("America/New_York")
        assertEquals(DayOfWeek.SUNDAY, Instant.ofEpochMilli(campusMondayMorning).atZone(ny).dayOfWeek)
        assertEquals(at(minute = 0), comparisonMonday(campusMondayMorning, CAMPUS_ZONE))
    }
    @Test fun mergedDetailNeverTreatsUnknownOrMissingSlotsAsFree() {
        val result = ComparisonResult(listOf(ComparisonDay(date, listOf(
            ComparedPeriod(0, span(510, 520), PeriodStatus.FREE),
            ComparedPeriod(1, span(520, 530), PeriodStatus.UNKNOWN),
        ))))
        assertEquals(PeriodStatus.UNKNOWN, comparisonDetailSlot(result, span(510, 530)).status)
        assertEquals(PeriodStatus.UNKNOWN, comparisonDetailSlot(result, span(540, 550)).status)
        assertEquals(PeriodStatus.FREE, comparisonDetailSlot(result, span(510, 520)).status)
    }
}
