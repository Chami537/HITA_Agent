package cn.limpu.hita.data.repository

import cn.limpu.hita.data.model.eas.CourseSelectionCourseResult
import cn.limpu.hita.data.model.eas.CourseSelectionCourseStatus
import cn.limpu.hita.data.model.eas.CourseSelectionJobCourse
import cn.limpu.hita.data.model.eas.CourseSelectionJobStatus
import cn.limpu.hita.data.model.eas.ShenzhenCourseCatalogItem
import cn.limpu.hita.data.model.eas.ShenzhenSelectionPool
import java.security.MessageDigest

object CourseSelectionJobPolicy {
    const val MAX_COURSES = 20
    const val MAX_CONCURRENCY = 10
    const val MIN_SCHEDULE_DELAY_MS = 500L
    const val MAX_SCHEDULE_AHEAD_MS = 86_400_000L

    fun buildCourses(
        catalogItems: List<ShenzhenCourseCatalogItem>,
        pool: ShenzhenSelectionPool
    ): List<CourseSelectionJobCourse> {
        val requestIds = mutableSetOf<String>()
        val courseIds = mutableSetOf<String>()
        val courses = catalogItems.mapNotNull { item ->
            val requestId = item.selectionRequestId.ifBlank { item.taskId.ifBlank { item.id } }
            if (requestId.isBlank()) return@mapNotNull null
            if (!requestIds.add(requestId)) return@mapNotNull null
            val courseId = item.courseId.ifBlank { item.id }
            if (!courseIds.add(courseId)) return@mapNotNull null
            CourseSelectionJobCourse(
                requestId = requestId,
                taskId = item.taskId.ifBlank { item.id },
                courseId = courseId,
                courseCode = item.courseCode,
                courseName = item.courseName,
                teacher = item.teacher,
                poolCode = pool.code
            )
        }

        require(courses.size <= MAX_COURSES) {
            "At most $MAX_COURSES distinct courses may be selected"
        }
        return courses
    }

    fun fingerprint(scheduledAtMillis: Long, courses: List<CourseSelectionJobCourse>): String {
        val second = scheduledAtMillis / 1_000L
        val canonicalCourses = courses
            .sortedBy { it.requestId }
            .joinToString("|") {
                listOf(it.requestId, it.taskId, it.courseId, it.poolCode).joinToString("\u001f")
            }
        val input = "$second\u001e$canonicalCourses"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun aggregateStatus(
        expectedCourseCount: Int,
        results: List<CourseSelectionCourseResult>
    ): CourseSelectionJobStatus {
        require(expectedCourseCount >= 0) { "Expected course count cannot be negative" }
        if (results.isEmpty()) return CourseSelectionJobStatus.FAILED
        val confirmed = results.count { it.status == CourseSelectionCourseStatus.CONFIRMED }
        return when {
            expectedCourseCount > 0 && results.size == expectedCourseCount && confirmed == expectedCourseCount ->
                CourseSelectionJobStatus.COMPLETED
            confirmed > 0 -> CourseSelectionJobStatus.PARTIAL
            else -> CourseSelectionJobStatus.FAILED
        }
    }
}
