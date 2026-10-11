package cn.limpu.hita.ui.main.timetable.compare

import cn.limpu.hita.feature.timetablecompare.ComparedPeriod
import cn.limpu.hita.feature.timetablecompare.ComparisonDay
import cn.limpu.hita.feature.timetablecompare.PeriodStatus
import cn.limpu.hita.feature.timetablecompare.TimeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComparisonGridLogicTest {
    private val date = LocalDate.of(2026, 10, 5)
    private fun at(minute: Int) = date.atStartOfDay(CAMPUS_ZONE).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    private fun span(a: Int, b: Int) = TimeSpan(at(a), at(b))
    private fun slot(i: Int, a: Int, b: Int, status: PeriodStatus) = ComparedPeriod(i, span(a, b), status)

    @Test fun consecutiveFreeMergeIntoOneBlockAcrossBreaks() {
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 615, PeriodStatus.FREE),
            slot(1, 615, 735, PeriodStatus.FREE),
            slot(2, 750, 870, PeriodStatus.FREE),
        ))
        // 与课表连排课一致：课间间隔不打断合并，连续空闲合成一个大框。
        val blocks = mergedBlocks(day, showFree = true, showBusy = true)
        assertEquals(1, blocks.size)
        assertEquals(span(510, 870), blocks[0].span)
        assertTrue(blocks.none { it.busy })
    }

    @Test fun mealBreakSplitsMergedBlocks() {
        // 午休 12:15–14:00（105 分钟）大间隔打断合并：上午、下午各成一框，不覆盖饭点。
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 615, PeriodStatus.FREE),
            slot(1, 615, 735, PeriodStatus.FREE),
            slot(2, 840, 945, PeriodStatus.FREE),
            slot(3, 960, 1065, PeriodStatus.CONFLICT),
        ))
        val blocks = mergedBlocks(day, showFree = true, showBusy = true)
        assertEquals(3, blocks.size)
        assertEquals(span(510, 735), blocks[0].span)
        assertEquals(span(840, 945), blocks[1].span)
        assertEquals(span(960, 1065), blocks[2].span)
    }

    @Test fun ownFriendAndConflictAllMergeIntoBusyBlocks() {
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 615, PeriodStatus.OWN_ONLY),
            slot(1, 615, 735, PeriodStatus.FRIEND_ONLY),
            slot(2, 750, 870, PeriodStatus.CONFLICT),
            slot(3, 870, 975, PeriodStatus.FREE),
        ))
        val blocks = mergedBlocks(day, showFree = true, showBusy = true)
        assertEquals(2, blocks.size)
        assertEquals(span(510, 870), blocks[0].span); assertTrue(blocks[0].busy)
        assertEquals(span(870, 975), blocks[1].span); assertTrue(!blocks[1].busy)
    }

    @Test fun unknownSlotsAreSkippedAndTogglesFilterKinds() {
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 615, PeriodStatus.UNKNOWN),
            slot(1, 615, 735, PeriodStatus.FREE),
            slot(2, 735, 855, PeriodStatus.CONFLICT),
        ))
        assertEquals(2, mergedBlocks(day, showFree = true, showBusy = true).size)
        val freeOnly = mergedBlocks(day, showFree = true, showBusy = false)
        assertEquals(1, freeOnly.size)
        assertTrue(!freeOnly[0].busy)
        val busyOnly = mergedBlocks(day, showFree = false, showBusy = true)
        assertEquals(1, busyOnly.size)
        assertTrue(busyOnly[0].busy)
    }

    @Test fun unknownShortSlotIsNeverBridgedAsFree() {
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 520, PeriodStatus.FREE),
            slot(1, 520, 525, PeriodStatus.UNKNOWN),
            slot(2, 525, 540, PeriodStatus.FREE),
        ))
        assertEquals(listOf(span(510, 520), span(525, 540)),
            mergedBlocks(day, true, true).map { it.span })
    }

    @Test fun hiddenBusyShortSlotIsNeverBridgedAsFree() {
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 520, PeriodStatus.FREE),
            slot(1, 520, 525, PeriodStatus.CONFLICT),
            slot(2, 525, 540, PeriodStatus.FREE),
        ))
        assertEquals(2, mergedBlocks(day, true, false).size)
    }

    @Test fun onlyBreaksOfAtMostTwentyMinutesAreMerged() {
        val day = ComparisonDay(date, listOf(
            slot(0, 510, 520, PeriodStatus.FREE),
            slot(1, 540, 550, PeriodStatus.FREE),
            slot(2, 575, 585, PeriodStatus.FREE),
        ))
        assertEquals(listOf(span(510, 550), span(575, 585)),
            mergedBlocks(day, true, true).map { it.span })
    }

    @Test fun defaultViewportPreservesMidnightAsMinute1440() {
        val periods = listOf(cn.limpu.hita.feature.timetablecompare.ComparisonPeriod(1380, 1440))
        assertEquals(1380 to 1440, comparisonViewport(ComparisonConfig("own", "friend"), periods))
    }

    @Test fun customizedViewportStaysNarrowAndClampsToChangedStructure() {
        val periods = listOf(cn.limpu.hita.feature.timetablecompare.ComparisonPeriod(510, 615))
        assertEquals(520 to 525, comparisonViewport(
            ComparisonConfig("own", "friend", 520, 525, rangeCustomized = true), periods))
        assertEquals(510 to 615, comparisonViewport(
            ComparisonConfig("own", "friend", 480, 700, rangeCustomized = true), periods))
    }
}
