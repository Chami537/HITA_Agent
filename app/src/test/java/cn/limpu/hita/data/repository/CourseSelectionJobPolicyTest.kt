package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogSource
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSelectionJobPolicyTest {
    @Test
    fun `build courses preserves order and removes duplicate request ids`() {
        val first = course(id = "task-a", requestId = "request-a")
        val duplicate = course(id = "task-a-copy", requestId = "request-a")
        val second = course(id = "task-b", requestId = "request-b")

        val result = CourseSelectionJobPolicy.buildCourses(
            listOf(first, duplicate, second),
            ShenzhenSelectionPool("xx-b-b", "限选课程池")
        )

        assertEquals(listOf("request-a", "request-b"), result.map { it.requestId })
    }

    @Test
    fun `build courses keeps only the first request for a duplicate course id`() {
        val first = course(id = "task-a", requestId = "request-a", courseId = "course-a")
        val duplicateRequest = course(id = "task-b", requestId = "request-a", courseId = "course-b")
        val laterUniqueRequest = course(id = "task-c", requestId = "request-b", courseId = "course-b")

        val result = CourseSelectionJobPolicy.buildCourses(
            listOf(first, duplicateRequest, laterUniqueRequest),
            ShenzhenSelectionPool("xx-b-b", "限选课程池")
        )

        assertEquals(listOf("request-a", "request-b"), result.map { it.requestId })
        assertEquals(listOf("course-a", "course-b"), result.map { it.courseId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `more than twenty distinct courses is rejected`() {
        CourseSelectionJobPolicy.buildCourses(
            (1..21).map { course("task-$it", "request-$it") },
            ShenzhenSelectionPool("xx-b-b", "限选课程池")
        )
    }

    @Test
    fun `request id falls back from selection request id to task id to catalog id`() {
        val result = CourseSelectionJobPolicy.buildCourses(
            listOf(
                course(id = "catalog-a", taskId = "task-a", requestId = ""),
                course(id = "catalog-b", taskId = "", requestId = "")
            ),
            ShenzhenSelectionPool("xx-b-b", "限选课程池")
        )

        assertEquals(listOf("task-a", "catalog-b"), result.map { it.requestId })
    }

    @Test
    fun `same second and course set produces same fingerprint`() {
        val courses = listOf(jobCourse("b"), jobCourse("a"))
        assertEquals(
            CourseSelectionJobPolicy.fingerprint(1_800_000_000_100, courses),
            CourseSelectionJobPolicy.fingerprint(1_800_000_000_900, courses.reversed())
        )
    }

    @Test
    fun `aggregate status distinguishes completed partial and failed results`() {
        val now = 1_800_000_000_000
        assertEquals(
            CourseSelectionJobStatus.COMPLETED,
            CourseSelectionJobPolicy.aggregateStatus(
                expectedCourseCount = 2,
                results = listOf(
                    result("a", CourseSelectionCourseStatus.CONFIRMED, now),
                    result("b", CourseSelectionCourseStatus.CONFIRMED, now)
                )
            )
        )
        assertEquals(
            CourseSelectionJobStatus.PARTIAL,
            CourseSelectionJobPolicy.aggregateStatus(
                expectedCourseCount = 2,
                results = listOf(
                    result("a", CourseSelectionCourseStatus.CONFIRMED, now),
                    result("b", CourseSelectionCourseStatus.BUSINESS_FAILURE, now)
                )
            )
        )
        assertEquals(
            CourseSelectionJobStatus.FAILED,
            CourseSelectionJobPolicy.aggregateStatus(
                expectedCourseCount = 2,
                results = listOf(
                    result("a", CourseSelectionCourseStatus.AUTH_REQUIRED, now),
                    result("b", CourseSelectionCourseStatus.UNKNOWN, now)
                )
            )
        )
        assertTrue(CourseSelectionJobPolicy.MAX_COURSES == 20)
        assertTrue(CourseSelectionJobPolicy.MAX_CONCURRENCY == 10)
        assertEquals(500L, CourseSelectionJobPolicy.MIN_SCHEDULE_DELAY_MS)
        assertEquals(86_400_000L, CourseSelectionJobPolicy.MAX_SCHEDULE_AHEAD_MS)
    }

    @Test
    fun `aggregate status is partial when confirmed results are missing`() {
        val status = CourseSelectionJobPolicy.aggregateStatus(
            expectedCourseCount = 2,
            results = listOf(result("a", CourseSelectionCourseStatus.CONFIRMED, 1_800_000_000_000))
        )

        assertEquals(CourseSelectionJobStatus.PARTIAL, status)
    }

    private fun course(
        id: String,
        requestId: String,
        taskId: String = "task-$id",
        courseId: String = ""
    ) = ShenzhenCourseCatalogItem(
        id = id,
        taskId = taskId,
        selectionRequestId = requestId,
        courseId = courseId,
        courseCode = "code-$id",
        courseName = "name-$id",
        source = ShenzhenCourseCatalogSource.AVAILABLE
    )

    private fun jobCourse(id: String) = CourseSelectionJobCourse(
        requestId = "request-$id",
        taskId = "task-$id",
        courseId = "course-$id",
        courseCode = "code-$id",
        courseName = "name-$id",
        teacher = "teacher-$id",
        poolCode = "xx-b-b"
    )

    private fun result(
        id: String,
        status: CourseSelectionCourseStatus,
        submittedAtMillis: Long
    ) = cn.limpu.hita.data.model.eas.CourseSelectionCourseResult(
        courseId = id,
        status = status,
        message = "",
        submittedAtMillis = submittedAtMillis
    )
}
