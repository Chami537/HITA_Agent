package cn.limpu.hita.feature.livecourse.domain

import java.time.Instant

/** Immutable projection of a timetable class for the system notification surface. */
data class LiveCourse(
    val courseId: String,
    val courseName: String,
    val classroom: String?,
    val startTime: Instant,
    val endTime: Instant,
)
