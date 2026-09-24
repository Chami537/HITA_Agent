package cn.limpu.hita.feature.livecourse.scheduler

import cn.limpu.hita.feature.livecourse.domain.LiveCourseState
import cn.limpu.hita.feature.livecourse.domain.LiveCourseStateResolver

/** The next lifecycle boundary, shared by the alarm and persistent work fallback. */
internal fun nextLiveCourseTransition(state: LiveCourseState): Long? = when (state) {
    is LiveCourseState.Inactive -> state.course.startTime
        .minus(LiveCourseStateResolver.DEFAULT_PRE_CLASS_WINDOW).toEpochMilli()
    is LiveCourseState.PreClass -> state.course.startTime.toEpochMilli()
    is LiveCourseState.InClass -> state.course.endTime.toEpochMilli()
    is LiveCourseState.Ended -> null
}
