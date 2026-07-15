package cn.limpu.hita.ui.eas.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamMemoDraftValidatorTest {
    @Test
    fun `valid memo draft passes`() {
        assertNull(
            ExamMemoDraftValidator.validate(
                courseName = "编译原理",
                date = "2026-07-21",
                timeRange = "14:00-16:00"
            )
        )
    }

    @Test
    fun `end time must be later than start time`() {
        assertEquals(
            "结束时间需晚于开始时间",
            ExamMemoDraftValidator.validate("编译原理", "2026-07-21", "16:00-14:00")
        )
    }

    @Test
    fun `invalid calendar date is rejected`() {
        assertEquals(
            "考试日期格式无效",
            ExamMemoDraftValidator.validate("编译原理", "2026-02-30", "09:00-11:00")
        )
    }
}
