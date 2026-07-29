package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenCourseMeeting
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePlanConflictEngineTest {
    @Test
    fun `projection keeps only courses with meetings inside schedule periods`() {
        val complete = course("A", "线性代数", listOf(1, 2), 2, 3, 4)
        val outsideSchedule = course("B", "大学物理", listOf(1, 2), 2, 13, 14)
        val withoutMeeting = complete.copy(id = "C", taskId = "C", meetings = emptyList())

        val result = CoursePlanProjectionPolicy.projectableCourses(
            listOf(complete, outsideSchedule, withoutMeeting),
            schedulePeriodCount = 12
        )

        assertEquals(listOf(complete), result)
    }

    @Test
    fun `projection deduplicates the same teaching task`() {
        val course = course("A", "线性代数", listOf(1, 2), 2, 3, 4)

        assertEquals(
            1,
            CoursePlanProjectionPolicy.projectableCourses(listOf(course, course), 12).size
        )
    }

    @Test
    fun `reports exact intersecting week weekday and periods`() {
        val left = course("A", "线性代数", listOf(1, 2, 3), 2, 3, 4)
        val right = course("B", "大学物理", listOf(2, 4), 2, 4, 5)

        val message = CoursePlanConflictEngine.conflictDescription(left, listOf(right))

        requireNotNull(message)
        assertTrue(message.contains("大学物理"))
        assertTrue(message.contains("第2周"))
        assertTrue(message.contains("周二"))
        assertTrue(message.contains("第4-4节"))
    }

    @Test
    fun `different weeks do not conflict`() {
        val left = course("A", "线性代数", listOf(1, 3), 2, 3, 4)
        val right = course("B", "大学物理", listOf(2, 4), 2, 3, 4)

        assertNull(CoursePlanConflictEngine.conflictDescription(left, listOf(right)))
    }

    private fun course(
        id: String,
        name: String,
        weeks: List<Int>,
        weekday: Int,
        begin: Int,
        end: Int
    ) = ShenzhenCourseCatalogItem(
        id = id,
        taskId = id,
        courseCode = id,
        courseName = name,
        meetings = listOf(ShenzhenCourseMeeting(weeks, weekday, begin, end)),
        source = ShenzhenCourseCatalogSource.AVAILABLE
    )
}
