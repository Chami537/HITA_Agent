package com.limpu.hitax.ui.main.timetable.views

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableCardTextScaleTest {
    @Test
    fun textScale_keepsFullSizeForSingleColumn() {
        assertEquals(1f, TimetableCardTextScale.forColumnCount(1), 0f)
    }

    @Test
    fun textScale_scalesDownForSideBySideCards() {
        assertEquals(0.7f, TimetableCardTextScale.forColumnCount(2), 0f)
        assertEquals(0.7f, TimetableCardTextScale.forColumnCount(3), 0f)
    }

    @Test
    fun marginScale_scalesDownForSideBySideCards() {
        assertEquals(1f, TimetableCardTextScale.marginScaleForColumnCount(1), 0f)
        assertEquals(0.7f, TimetableCardTextScale.marginScaleForColumnCount(2), 0f)
        assertEquals(0.7f, TimetableCardTextScale.marginScaleForColumnCount(3), 0f)
    }
}
