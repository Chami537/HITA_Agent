package cn.limpu.hita.data.model.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleCodecTest {
    private val schedule = listOf(
        BusySlot(1, 1, 2),
        BusySlot(1, 5, 6),
        BusySlot(3, 3, 4)
    )

    @Test
    fun `encodes the example in the agreed compact format`() {
        assertEquals("1,1,2;1,5,6;3,3,4", encodeSchedule(schedule))
    }

    @Test
    fun `decodes the example back to occupied slots`() {
        assertEquals(schedule, decodeSchedule("1,1,2;1,5,6;3,3,4"))
    }

    @Test
    fun `round trip preserves slot values order and duplicates`() {
        val original = listOf(
            BusySlot(7, 10, 12),
            BusySlot(1, 1, 1),
            BusySlot(7, 10, 12),
            BusySlot(3, 3, 4)
        )

        assertEquals(original, decodeSchedule(encodeSchedule(original)))
        assertEquals(schedule, decodeSchedule(encodeSchedule(schedule)))
    }

    @Test
    fun `empty schedule encodes to empty text and round trips`() {
        assertEquals("", encodeSchedule(emptyList()))
        assertEquals(emptyList<BusySlot>(), decodeSchedule(encodeSchedule(emptyList())))
        assertEquals(emptyList<BusySlot>(), decodeSchedule(" \n\t"))
    }

    @Test
    fun `rejects malformed rows instead of dropping fields or slots`() {
        for (text in listOf("1,2", "1,2,3,4", "1,a,2", "1,1,2;", "1,1,2;;3,3,4")) {
            assertThrows(IllegalArgumentException::class.java) {
                decodeSchedule(text)
            }
        }
    }
}
