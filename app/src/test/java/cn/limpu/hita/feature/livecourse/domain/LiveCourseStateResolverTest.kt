package cn.limpu.hita.feature.livecourse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class LiveCourseStateResolverTest {
    private val start = Instant.parse("2026-09-21T06:00:00Z")
    private val course = LiveCourse(
        courseId = "event-1",
        courseName = "数据结构",
        classroom = "T3-403",
        startTime = start,
        endTime = start.plus(100, ChronoUnit.MINUTES),
    )

    @Test
    fun `eleven minutes before class is inactive`() {
        assertTrue(LiveCourseStateResolver.resolve(course, start.minus(11, ChronoUnit.MINUTES)) is LiveCourseState.Inactive)
    }

    @Test
    fun `ten minutes before class enters pre class`() {
        val state = LiveCourseStateResolver.resolve(course, start.minus(10, ChronoUnit.MINUTES))
        assertTrue(state is LiveCourseState.PreClass)
        assertEquals(10, (state as LiveCourseState.PreClass).remaining.toMinutes())
    }

    @Test
    fun `one minute before class remains pre class`() {
        assertTrue(LiveCourseStateResolver.resolve(course, start.minus(1, ChronoUnit.MINUTES)) is LiveCourseState.PreClass)
    }

    @Test
    fun `exact start enters class at zero progress`() {
        val state = LiveCourseStateResolver.resolve(course, start) as LiveCourseState.InClass
        assertEquals(0f, state.progress)
        assertEquals(100, state.remaining.toMinutes())
    }

    @Test
    fun `middle of class reports progress and remaining time`() {
        val state = LiveCourseStateResolver.resolve(course, start.plus(48, ChronoUnit.MINUTES)) as LiveCourseState.InClass
        assertEquals(0.48f, state.progress, 0.001f)
        assertEquals(52, state.remaining.toMinutes())
    }

    @Test
    fun `one minute before end remains in class`() {
        assertTrue(LiveCourseStateResolver.resolve(course, course.endTime.minus(1, ChronoUnit.MINUTES)) is LiveCourseState.InClass)
    }

    @Test
    fun `exact end and later are ended`() {
        assertTrue(LiveCourseStateResolver.resolve(course, course.endTime) is LiveCourseState.Ended)
        assertTrue(LiveCourseStateResolver.resolve(course, course.endTime.plus(1, ChronoUnit.MINUTES)) is LiveCourseState.Ended)
    }
}
