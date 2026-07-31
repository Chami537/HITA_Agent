package cn.limpu.hita.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseNameUtilsTest {
    @Test
    fun `normalize removes Shenzhen slash teaching class suffix`() {
        assertEquals(
            "计算机设计与实践",
            CourseNameUtils.normalize("计算机设计与实践 1/E班")
        )
        assertEquals(
            "计算机设计与实践",
            CourseNameUtils.normalize("计算机设计与实践1／E班")
        )
    }

    @Test
    fun `normalize removes standalone teaching class suffix`() {
        assertEquals("大学英语", CourseNameUtils.normalize("大学英语 A班"))
        assertEquals("高等数学", CourseNameUtils.normalize("高等数学 1班"))
    }

    @Test
    fun `normalize keeps Chinese course names ending with class character`() {
        assertEquals("班班通技术", CourseNameUtils.normalize("班班通技术"))
    }
}
