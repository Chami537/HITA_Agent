package cn.limpu.hita.utils

import cn.limpu.hita.data.model.resource.CourseResourceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseResourceLinkerTest {
    @Test
    fun `unique exact code matches HOA course after teaching class suffix is removed`() {
        val code = CourseResourceLinker.uniqueExactCourseCodeForName(
            items = listOf(
                CourseResourceItem(
                    repoName = "HITSZ-OpenAuto/COMP2012",
                    courseCode = "COMP2012",
                    courseName = "计算机设计与实践"
                )
            ),
            courseNameRaw = "计算机设计与实践 1/E班"
        )

        assertEquals("COMP2012", code)
    }

    @Test
    fun `ambiguous exact course names are not persisted`() {
        val code = CourseResourceLinker.uniqueExactCourseCodeForName(
            items = listOf(
                CourseResourceItem(courseCode = "COMP2012", courseName = "课程设计"),
                CourseResourceItem(courseCode = "COMP3012", courseName = "课程设计")
            ),
            courseNameRaw = "课程设计 1/A班"
        )

        assertNull(code)
    }
}
