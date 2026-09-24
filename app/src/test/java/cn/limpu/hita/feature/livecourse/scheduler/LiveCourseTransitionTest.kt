package cn.limpu.hita.feature.livecourse.scheduler

import cn.limpu.hita.feature.livecourse.domain.LiveCourse
import cn.limpu.hita.feature.livecourse.domain.LiveCourseStateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class LiveCourseTransitionTest {
    private val start = Instant.parse("2026-09-24T08:00:00Z")
    private val course = LiveCourse(
        "course-1", "数据结构", null, start, start.plus(90, ChronoUnit.MINUTES),
    )

    @Test fun `inactive schedules pre-class boundary`() {
        val state = LiveCourseStateResolver.resolve(course, start.minus(11, ChronoUnit.MINUTES))
        assertEquals(start.minus(10, ChronoUnit.MINUTES).toEpochMilli(), nextLiveCourseTransition(state))
    }

    @Test fun `pre-class schedules class start`() {
        val state = LiveCourseStateResolver.resolve(course, start.minus(5, ChronoUnit.MINUTES))
        assertEquals(start.toEpochMilli(), nextLiveCourseTransition(state))
    }

    @Test fun `in-class schedules class end`() {
        val state = LiveCourseStateResolver.resolve(course, start.plus(1, ChronoUnit.MINUTES))
        assertEquals(course.endTime.toEpochMilli(), nextLiveCourseTransition(state))
    }

    @Test fun `ended has no next boundary`() {
        val state = LiveCourseStateResolver.resolve(course, course.endTime)
        assertNull(nextLiveCourseTransition(state))
    }
}
