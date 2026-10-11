package cn.limpu.hita.ui.main.timetable.views

import cn.limpu.hita.data.model.timetable.EventItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Timestamp
import java.util.Calendar

class TimetableOverlapLayoutTest {
    @Test
    fun arrange_keepsFullWidthForNonOverlappingEvents() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)

        val arranged = TimetableOverlapLayout.arrange(listOf(first, second)).associateBy { it.event.id }

        assertEquals(1, arranged.getValue(first.id).overlapCount)
        assertEquals(0, arranged.getValue(first.id).columnIndex)
        assertEquals(1, arranged.getValue(second.id).overlapCount)
        assertEquals(0, arranged.getValue(second.id).columnIndex)
    }

    @Test
    fun arrange_splitsOverlappingEventsIntoColumns() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 9, startMinute = 30, endHour = 10, endMinute = 30)

        val arranged = TimetableOverlapLayout.arrange(listOf(first, second)).associateBy { it.event.id }

        assertEquals(2, arranged.getValue(first.id).overlapCount)
        assertEquals(2, arranged.getValue(second.id).overlapCount)
        assertEquals(0, arranged.getValue(first.id).columnIndex)
        assertEquals(1, arranged.getValue(second.id).columnIndex)
    }

    @Test
    fun arrange_keepsClusterWidthAtPeakConcurrencyForOverlapChain() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 9, startMinute = 30, endHour = 10, endMinute = 30)
        val third = event("third", day = 0, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)

        val arranged = TimetableOverlapLayout.arrange(listOf(first, second, third)).associateBy { it.event.id }

        assertEquals(2, arranged.getValue(first.id).overlapCount)
        assertEquals(2, arranged.getValue(second.id).overlapCount)
        assertEquals(2, arranged.getValue(third.id).overlapCount)
        assertEquals(0, arranged.getValue(first.id).columnIndex)
        assertEquals(1, arranged.getValue(second.id).columnIndex)
        assertEquals(0, arranged.getValue(third.id).columnIndex)
    }

    @Test
    fun conflictCards_mergeOverlappingChainsIntoOneCard() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 9, startMinute = 30, endHour = 10, endMinute = 30)
        val third = event("third", day = 0, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)

        val cards = TimetableOverlapLayout.conflictCards(
            TimetableOverlapLayout.arrange(listOf(first, second, third))
        )

        // 链式重叠传递合并为一张冲突卡，避免尾课与冲突卡互相压盖。
        assertEquals(1, cards.size)
        assertEquals(listOf("first", "second", "third"), cards.first().second?.map { it.id })
    }

    @Test fun conflictCardsKeepNestedChainsButSeparateTouchingTailAndNextDay() {
        val outer = event("outer", 0, 8, 0, 12, 0)
        val nested = event("nested", 0, 9, 0, 10, 0)
        val tail = event("tail", 0, 11, 0, 13, 0)
        val touching = event("touching", 0, 13, 0, 14, 0)
        val nextDay = event("nextDay", 1, 9, 0, 10, 0)
        val cards = TimetableOverlapLayout.conflictCards(
            TimetableOverlapLayout.arrange(listOf(nextDay, touching, tail, nested, outer)))
        assertEquals(3, cards.size)
        assertEquals(listOf("outer", "nested", "tail"), cards[0].second?.map { it.id })
        assertEquals("touching", cards[1].first.event.id)
        assertEquals(null, cards[1].second)
        assertEquals("nextDay", cards[2].first.event.id)
    }

    private fun event(
        id: String,
        day: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): EventItem {
        val event = EventItem()
        event.id = id
        event.from = timestamp(day, startHour, startMinute)
        event.to = timestamp(day, endHour, endMinute)
        return event
    }

    private fun timestamp(day: Int, hour: Int, minute: Int): Timestamp {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 6 + day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(calendar.timeInMillis)
    }
}
