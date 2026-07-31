package cn.limpu.hita.data.model.resource

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseContributionOpsTest {
    private val author = mapOf("name" to "Tester", "link" to "", "date" to "2026-07")

    @Test
    fun `new child course is created before its first section item`() {
        val ops = CourseContributionOps.courseSection(
            courseName = "田径",
            sectionTitle = "课程概况",
            content = "田径课程内容",
            author = author,
            createCourse = true,
        )

        assertEquals(2, ops.size)
        assertEquals("create_course", ops[0]["op"])
        assertEquals("田径", ops[0]["course_name"])
        val append = ops[1]
        assertEquals("append_course_section_item", append["op"])
        assertEquals("田径", append["course_name"])
        assertEquals("课程概况", append["section_title"])
        @Suppress("UNCHECKED_CAST")
        val item = append["item"] as Map<String, Any>
        assertEquals("田径课程内容", item["content"])
    }

    @Test
    fun `existing child course append does not create duplicate course`() {
        val ops = CourseContributionOps.courseSection(
            courseName = "游泳",
            sectionTitle = "学习建议",
            content = "带好泳镜",
            author = author,
            createCourse = false,
        )

        assertEquals(1, ops.size)
        assertEquals("append_course_section_item", ops[0]["op"])
    }

    @Test
    fun `new child teacher review creates course first`() {
        val ops = CourseContributionOps.courseTeacherReview(
            courseName = "田径",
            teacherName = "张老师",
            content = "讲解清楚",
            author = author,
            createCourse = true,
        )

        assertEquals("create_course", ops[0]["op"])
        assertEquals("add_course_teacher_review", ops[1]["op"])
        assertEquals("田径", ops[1]["course_name"])
    }
}
