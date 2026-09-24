package cn.limpu.hita.feature.livecourse.domain

import java.time.Duration
import java.time.Instant

/**
 * Pure lifecycle calculation. Scheduling and notification code must not duplicate these bounds.
 */
object LiveCourseStateResolver {
    val DEFAULT_PRE_CLASS_WINDOW: Duration = Duration.ofMinutes(10)

    fun resolve(
        course: LiveCourse,
        now: Instant,
        preClassWindow: Duration = DEFAULT_PRE_CLASS_WINDOW,
    ): LiveCourseState {
        require(course.endTime.isAfter(course.startTime)) { "Live course must end after it starts." }

        val preClassStart = course.startTime.minus(preClassWindow)
        return when {
            now.isBefore(preClassStart) -> LiveCourseState.Inactive(course)
            now.isBefore(course.startTime) -> LiveCourseState.PreClass(
                course = course,
                remaining = Duration.between(now, course.startTime),
            )
            now.isBefore(course.endTime) -> {
                val elapsed = Duration.between(course.startTime, now)
                val durationMillis = Duration.between(course.startTime, course.endTime).toMillis()
                LiveCourseState.InClass(
                    course = course,
                    elapsed = elapsed,
                    remaining = Duration.between(now, course.endTime),
                    progress = (elapsed.toMillis().toFloat() / durationMillis).coerceIn(0f, 1f),
                )
            }
            else -> LiveCourseState.Ended(course)
        }
    }
}
