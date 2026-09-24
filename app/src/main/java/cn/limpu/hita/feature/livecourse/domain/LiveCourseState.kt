package cn.limpu.hita.feature.livecourse.domain

import java.time.Duration

sealed interface LiveCourseState {
    data class Inactive(val course: LiveCourse) : LiveCourseState

    data class PreClass(
        val course: LiveCourse,
        val remaining: Duration,
    ) : LiveCourseState

    data class InClass(
        val course: LiveCourse,
        val elapsed: Duration,
        val remaining: Duration,
        val progress: Float,
    ) : LiveCourseState

    data class Ended(val course: LiveCourse) : LiveCourseState
}
