package com.limpu.hitax.ui.main.timetable.views

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableCardTextScaleTest {
    @Test
    fun textScale_keepsFullSizeForSingleColumn() {
        assertEquals(1f, TimetableCardTextScale.forColumnCount(1), 0f)
    }

    @Test
    fun textScale_scalesDownForOverlappingCards() {
        assertEquals(0.9f, TimetableCardTextScale.forColumnCount(2), 0f)
        assertEquals(0.9f, TimetableCardTextScale.forColumnCount(3), 0f)
    }

    @Test
    fun marginScale_scalesDownForOverlappingCards() {
        assertEquals(1f, TimetableCardTextScale.marginScaleForColumnCount(1), 0f)
        assertEquals(0.85f, TimetableCardTextScale.marginScaleForColumnCount(2), 0f)
        assertEquals(0.85f, TimetableCardTextScale.marginScaleForColumnCount(3), 0f)
    }
}
